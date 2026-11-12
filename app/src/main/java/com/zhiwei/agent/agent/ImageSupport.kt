package com.zhiwei.agent.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 附件与图片处理。
 *
 * 手机拍的照片动辄 4000×3000、好几 MB，直接 base64 塞进请求体会把网关顶爆（413），
 * 也会白烧额度。这里统一：按长边缩放 → JPEG 85 → base64 data URL。
 * 主流客户端（Open WebUI / LibreChat / Cherry Studio）都是这么干的。
 */
object ImageSupport {

    /** 文本类附件直接读进上下文的上限：超过就只给路径和摘要 */
    const val INLINE_TEXT_LIMIT = 12000

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    fun isImage(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in imageExt
    }

    /** 附件归类：image / text / other */
    fun kind(path: String): String {
        if (isImage(path)) return "image"
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in setOf(
                "txt", "md", "markdown", "json", "xml", "yml", "yaml", "csv", "tsv", "log",
                "kt", "java", "py", "js", "ts", "sh", "c", "cpp", "h", "css", "html", "htm",
                "ini", "conf", "properties", "gradle", "kts", "sql", "toml"
            )
        ) {
            "text"
        } else {
            "other"
        }
    }

    /**
     * 把本地图片转成 OpenAI 多模态能吃的 data URL。
     * 失败（损坏、格式不支持、磁盘读不到）就返回 null，调用方按没图处理。
     */
    fun dataUrl(path: String, maxSide: Int = 1568): String? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = maxSide.coerceAtLeast(256)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target || bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        } ?: return null

        val scaled = scaleDown(decoded, target)
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(src.width, src.height)
        if (long <= maxSide) return src
        val ratio = maxSide.toFloat() / long
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (_: Exception) {
            src
        }
    }

    /** 文本附件：能读就内联一段，读不了就给路径 */
    fun inlineText(path: String, limit: Int = INLINE_TEXT_LIMIT): String? {
        val f = File(path)
        if (!f.exists() || !f.isFile || f.length() > 2_000_000) return null
        return try {
            val text = f.readText()
            if (text.contains('\u0000')) null else text.take(limit)
        } catch (_: Exception) {
            null
        }
    }
}
