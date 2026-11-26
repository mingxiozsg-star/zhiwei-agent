package com.zhiwei.agent.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 更新包的下载状态 */
sealed class DownloadState {
    object Idle : DownloadState()

    /** percent 为 -1 表示服务端没给长度，进度算不出来 */
    data class Running(
        val percent: Int,
        val received: Long,
        val total: Long,
        val viaMirror: Boolean
    ) : DownloadState()

    data class Done(val file: File, val viaMirror: Boolean) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

/**
 * 内置更新：直接把安装包下到应用自己的目录里，下完调起系统安装器。
 *
 * 走的是应用私有目录（filesDir/updates），不需要任何存储权限；
 * 对外暴露只通过 FileProvider，装完即用，不经过浏览器和下载管理器。
 */
object UpdateInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun apkDir(context: Context): File = File(context.filesDir, "updates").also { it.mkdirs() }

    /** 已经在本地下好的包，直接复用，不重复下载 */
    fun cachedApk(context: Context, tag: String): File? {
        val dir = apkDir(context)
        val files = dir.listFiles() ?: return null
        return files.filter { it.isFile && it.name.startsWith("zhiwei-${tag}-") && it.name.endsWith(".apk") }
            .maxByOrNull { it.lastModified() }
    }

    /**
     * 依次尝试各条线路：加速镜像在前、官方直链兜底。
     * 已经下了一半的包会接着下，不从头再来。
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        preferMirror: Boolean,
        onProgress: (Int, Long, Long, Boolean) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<Pair<String, Boolean>>()
        if (info.apkUrl.isNotBlank()) {
            val mirrors = info.mirrorUrls.map { it to true }
            val direct = info.apkUrl to false
            if (preferMirror) candidates += mirrors else candidates += direct
            candidates += if (preferMirror) listOf(direct) else mirrors
        }
        if (candidates.isEmpty()) throw IOException("这个 Release 里没有 APK，去发行页看看")

        var lastError = "下载失败"
        for ((url, mirror) in candidates) {
            try {
                return@withContext fetch(context, url, info, mirror, onProgress)
            } catch (e: Exception) {
                lastError = e.message ?: "下载失败"
            }
        }
        throw IOException(lastError)
    }

    private fun fetch(
        context: Context,
        url: String,
        info: UpdateInfo,
        mirror: Boolean,
        onProgress: (Int, Long, Long, Boolean) -> Unit
    ): File {
        val dir = apkDir(context)
        val part = File(dir, "update.part")
        val marker = File(dir, "update.part.url")

        // 换线路就丢掉半截文件，避免把两家的字节拼在一起
        val recorded = runCatching { marker.readText() }.getOrDefault("")
        if (recorded != url && part.exists()) part.delete()
        runCatching { marker.writeText(url) }

        val exist = if (part.exists()) part.length() else 0L
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ZhiweiAgent")
            .apply { if (exist > 0) header("Range", "bytes=$exist-") }
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val append = resp.code == 206 && exist > 0
            val body = resp.body ?: throw IOException("服务商返回了空响应")
            val remain = body.contentLength()
            val total = if (remain > 0) (if (append) exist else 0L) + remain else -1L
            var got = if (append) exist else 0L
            FileOutputStream(part, append).use { fos ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        got += n
                        val pct = if (total > 0) ((got * 100) / total).toInt().coerceIn(0, 100) else -1
                        onProgress(pct, got, total, mirror)
                    }
                }
            }
        }

        val target = File(dir, "zhiwei-${info.tag}-${if (mirror) "mirror" else "direct"}.apk")
        if (target.exists()) target.delete()
        val moved = part.renameTo(target)
        if (!moved) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        runCatching { marker.delete() }
        if (target.length() <= 0L) throw IOException("下下来的文件是空的")
        return target
    }

    /** 有没有「安装未知来源应用」的权限 */
    fun canInstall(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

    fun openInstallPermission(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 调起系统安装器。没给权限就先跳去授权页，返回后再点一次即可。
     * @return true 表示安装器已经拉起来了
     */
    fun install(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        if (!canInstall(context)) {
            openInstallPermission(context)
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}