package com.zhiwei.agent.agent

import com.zhiwei.agent.data.PLUGIN_MCP
import com.zhiwei.agent.data.PLUGIN_SHELL
import com.zhiwei.agent.data.PluginTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 插件（自定义工具）执行器。三种类型：
 *  - http ：URL / 请求头 / 请求体里用 {参数名} 占位，执行时替换
 *  - shell：命令模板里用 {参数名} 占位，在本机执行
 *  - mcp  ：调用 MCP HTTP 服务端的 tools/call（JSON-RPC 2.0）
 */
object PluginRunner {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun execute(env: AgentEnv, plugin: PluginTool, argsJson: String): String =
        withContext(Dispatchers.IO) {
            val args = try {
                if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            } catch (_: Exception) {
                JSONObject()
            }
            try {
                when (plugin.kind) {
                    PLUGIN_SHELL -> runShell(env, plugin, args)
                    PLUGIN_MCP -> runMcp(plugin, args)
                    else -> runHttp(plugin, args)
                }
            } catch (e: Exception) {
                "插件执行失败：${e.message}"
            }
        }

    // ---------- HTTP ----------

    private fun runHttp(plugin: PluginTool, args: JSONObject): String {
        val cfg = plugin.configJson()
        val method = cfg.optString("method", "GET").uppercase().ifBlank { "GET" }
        val urlTpl = cfg.optString("url", "")
        if (urlTpl.isBlank()) return "插件「${plugin.name}」没有配置 url"

        val used = HashSet<String>()
        var url = fill(urlTpl, args, { v -> urlEncode(v) }, used)
        if (!urlTpl.contains("{") && method == "GET" && args.length() > 0) {
            val extra = args.keys().asSequence()
                .filter { !used.contains(it) }
                .joinToString("&") { "${urlEncode(it)}=${urlEncode(args.optString(it, ""))}" }
            if (extra.isNotEmpty()) {
                url += if (url.contains("?")) "&$extra" else "?$extra"
            }
        }
        if (!url.startsWith("http")) url = "https://$url"

        val builder = Request.Builder().url(url).header("User-Agent", "ZhiweiAgent")
        cfg.optJSONObject("headers")?.let { hs ->
            hs.keys().forEach { k ->
                val v = fill(hs.optString(k, ""), args, { x -> x }, null)
                if (v.isNotBlank()) builder.header(k, v)
            }
        }

        val bodyTpl = cfg.optString("body", "")
        val bodyText = when {
            bodyTpl.isNotBlank() -> fill(bodyTpl, args, { v -> jsonEscape(v) }, null)
            method == "GET" || method == "HEAD" -> null
            else -> args.toString()
        }
        val rb = (bodyText ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = when (method) {
            "POST" -> builder.post(rb).build()
            "PUT" -> builder.put(rb).build()
            "PATCH" -> builder.patch(rb).build()
            "DELETE" -> builder.delete(rb).build()
            else -> builder.get().build()
        }

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            return buildString {
                append("HTTP ").append(resp.code).append('\n')
                append(text.take(4000).ifBlank { "（无响应体）" })
            }
        }
    }

    // ---------- Shell ----------

    private fun runShell(env: AgentEnv, plugin: PluginTool, args: JSONObject): String {
        val tpl = plugin.configJson().optString("command", "")
        if (tpl.isBlank()) return "插件「${plugin.name}」没有配置 command"
        val cmd = fill(tpl, args, { v -> shellQuote(v) }, null)
        val home = ShellRunner.homeDir(env.context.filesDir)
        val result = ShellRunner.run(cmd, home, timeoutMs = 30000, maxChars = 6000)
        return buildString {
            append("\$ ").append(cmd).append('\n')
            append(result.output.trim().ifEmpty { "（无输出）" })
            append("\n[退出码 ").append(result.exitCode).append(']')
        }
    }

    // ---------- MCP ----------

    private fun runMcp(plugin: PluginTool, args: JSONObject): String {
        val cfg = plugin.configJson()
        var url = cfg.optString("url", "")
        if (url.isBlank()) return "插件「${plugin.name}」没有配置 MCP 服务端地址"
        if (!url.startsWith("http")) url = "https://$url"
        val tool = cfg.optString("tool", "").ifBlank { plugin.name }

        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/call")
            put(
                "params",
                JSONObject().apply {
                    put("name", tool)
                    put("arguments", args)
                }
            )
        }
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json, text/event-stream")
            .header("User-Agent", "ZhiweiAgent")
        cfg.optJSONObject("headers")?.let { hs ->
            hs.keys().forEach { k -> builder.header(k, hs.optString(k, "")) }
        }

        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return "MCP 调用失败：HTTP ${resp.code} ${text.take(300)}"
            val json = extractJson(text)
            val err = json?.optJSONObject("error")
            if (err != null) return "MCP 返回错误：" + err.optString("message", err.toString())
            val result = json?.optJSONObject("result")
            val content = result?.optJSONArray("content")
            if (content != null) {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    val t = item.optString("text", "")
                    if (t.isNotBlank()) sb.append(t).append('\n')
                }
                if (sb.isNotBlank()) return sb.toString().trim().take(4000)
            }
            return (result?.toString() ?: text).take(4000)
        }
    }

    /** SSE 或纯 JSON 都能吃 */
    private fun extractJson(text: String): JSONObject? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("{")) {
            return try {
                JSONObject(t)
            } catch (_: Exception) {
                null
            }
        }
        t.lines().forEach { line ->
            if (line.startsWith("data:")) {
                val payload = line.substring(5).trim()
                if (payload.startsWith("{")) {
                    try {
                        return JSONObject(payload)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return null
    }

    // ---------- 模板 ----------

    private fun fill(
        template: String,
        args: JSONObject,
        transform: (String) -> String,
        used: MutableSet<String>?
    ): String {
        val re = Regex("\\{([A-Za-z0-9_\\u4e00-\\u9fa5]+)}")
        return re.replace(template) { m ->
            val key = m.groupValues[1]
            used?.add(key)
            val raw = if (args.has(key)) args.optString(key, "") else ""
            transform(raw)
        }
    }

    private fun urlEncode(v: String): String =
        try {
            URLEncoder.encode(v, "UTF-8")
        } catch (_: Exception) {
            v
        }

    private fun jsonEscape(v: String): String {
        val quoted = JSONObject.quote(v)
        return quoted.substring(1, quoted.length - 1)
    }

    private fun shellQuote(v: String): String = "'" + v.replace("'", "'\\''") + "'"
}