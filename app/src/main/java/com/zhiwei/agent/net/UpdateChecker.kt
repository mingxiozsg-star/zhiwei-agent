package com.zhiwei.agent.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val REPO = "mingxiozsg-star/zhiwei-agent"
const val REPO_URL = "https://github.com/$REPO"
const val RELEASES_URL = "$REPO_URL/releases"
const val MIRROR_PREFIX = "https://ghfast.top/"

/** 一个新版本 */
data class UpdateInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String
) {
    val mirrorUrl: String
        get() = if (apkUrl.isBlank()) pageUrl else MIRROR_PREFIX + apkUrl
}

/** 更新检查的状态机 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Done(val info: UpdateInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * 内置更新检查：读 GitHub 的最新 Release，和本机版本比对。
 * 只做检查与跳转，不自己下载安装（交给系统浏览器/下载管理器，省掉安装权限）。
 */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val direct = "https://api.github.com/repos/$REPO/releases/latest"
        val urls = listOf(direct, MIRROR_PREFIX + direct)
        var lastError: String? = null
        for (u in urls) {
            val r = runCatching { fetch(u) }
            r.getOrNull()?.let { return@withContext Result.success(it) }
            lastError = r.exceptionOrNull()?.message
        }
        Result.failure(IOException(lastError ?: "检查更新失败，确认网络能访问 GitHub"))
    }

    private fun fetch(url: String): UpdateInfo {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZhiweiAgent")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) throw IOException("没读到版本号")
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url", "")
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
            }
            return UpdateInfo(
                tag = tag,
                name = json.optString("name", tag),
                notes = json.optString("body", "").trim(),
                publishedAt = json.optString("published_at", "").take(10),
                apkUrl = apkUrl,
                apkSize = apkSize,
                pageUrl = json.optString("html_url", RELEASES_URL)
            )
        }
    }

    /** 版本号比较：本地 1.3.0 对线上 v1.4.0 */
    fun isNewer(current: String, latest: String): Boolean {
        val a = parts(current)
        val b = parts(latest)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y > x) return true
            if (y < x) return false
        }
        return false
    }

    private fun parts(v: String): List<Int> = v.trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.filter { c -> c.isDigit() }.ifBlank { "0" }.toIntOrNull() ?: 0 }
}
