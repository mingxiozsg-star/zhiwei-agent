package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val PLUGIN_HTTP = "http"
const val PLUGIN_SHELL = "shell"
const val PLUGIN_MCP = "mcp"

/**
 * 自定义工具（插件）。三种执行方式：
 *  - http  ：把参数填进 URL / 请求头 / 请求体后发 HTTP 请求
 *  - shell ：把参数填进命令模板，在手机上执行
 *  - mcp   ：调用一个 MCP（Model Context Protocol）HTTP 服务端的 tools/call
 *
 * 导入时兼容三种写法：
 *  1. OpenAI function calling 的 {"type":"function","function":{...}} 列表（可带 x-exec 扩展）
 *  2. MCP 服务端返回的 {"tools":[{"name","description","inputSchema"}]}
 *  3. 本应用自己的简洁格式 {"name","description","parameters","kind","config"}
 */
data class PluginTool(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val parameters: String = "{}",
    val kind: String = PLUGIN_HTTP,
    val config: String = "{}",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun configJson(): JSONObject = try {
        JSONObject(config)
    } catch (_: Exception) {
        JSONObject()
    }

    fun paramsJson(): JSONObject = try {
        JSONObject(parameters)
    } catch (_: Exception) {
        JSONObject().put("type", "object").put("properties", JSONObject())
    }

    /** 给模型看的函数名：插件名可能带空格，统一做安全化 */
    fun safeName(): String = name.lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "plugin_" + id.take(6).replace("-", "") }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("parameters", parameters)
        put("kind", kind)
        put("config", config)
        put("enabled", enabled)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): PluginTool = PluginTool(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            description = o.optString("description", ""),
            parameters = o.optString("parameters", "{}"),
            kind = o.optString("kind", PLUGIN_HTTP),
            config = o.optString("config", "{}"),
            enabled = o.optBoolean("enabled", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

class PluginStore(context: Context) {

    private val file = File(context.filesDir, "plugins.json")

    fun load(): List<PluginTool> = synchronized(this) {
        if (!file.exists()) return@synchronized emptyList()
        try {
            val arr = JSONArray(file.readText())
            val out = mutableListOf<PluginTool>()
            for (i in 0 until arr.length()) out.add(PluginTool.fromJson(arr.getJSONObject(i)))
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<PluginTool>) {
        synchronized(this) {
            try {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, "plugins.json.tmp")
                tmp.writeText(arr.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * 插件导入解析器。返回解析出的插件列表（kind/config 已填好）。
 * defaultMcpUrl 用于 MCP 工具列表导入时补全服务端地址。
 */
object PluginParser {

    fun parse(text: String, defaultMcpUrl: String = ""): List<PluginTool> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val out = mutableListOf<PluginTool>()

        // 单个对象或数组统一处理
        val root: Any = try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        } catch (_: Exception) {
            // 一行一个 URL / 名称的朴素格式
            return trimmed.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { fromHttpUrl(it) }
        }

        when (root) {
            is JSONArray -> for (i in 0 until root.length()) {
                root.optJSONObject(i)?.let { out.addAll(fromObject(it, defaultMcpUrl)) }
            }

            is JSONObject -> {
                val tools = root.optJSONArray("tools")
                if (tools != null) {
                    // MCP tools/list 风格
                    val url = defaultMcpUrl.ifBlank { root.optString("url", "") }
                    for (i in 0 until tools.length()) {
                        val t = tools.optJSONObject(i) ?: continue
                        out.add(
                            PluginTool(
                                name = t.optString("name", ""),
                                description = t.optString("description", ""),
                                parameters = (t.optJSONObject("inputSchema")
                                    ?: t.optJSONObject("parameters")
                                    ?: JSONObject().put("type", "object").put("properties", JSONObject())).toString(),
                                kind = PLUGIN_MCP,
                                config = JSONObject().apply {
                                    put("url", url)
                                    put("tool", t.optString("name", ""))
                                }.toString()
                            )
                        )
                    }
                } else {
                    out.addAll(fromObject(root, defaultMcpUrl))
                }
            }
        }
        return out.filter { it.name.isNotBlank() }
    }

    private fun fromObject(o: JSONObject, defaultMcpUrl: String): List<PluginTool> {
        // OpenAI 风格
        val fn = o.optJSONObject("function")
        if (fn != null) {
            val exec = o.optJSONObject("x-exec") ?: fn.optJSONObject("x-exec") ?: JSONObject()
            return listOf(
                PluginTool(
                    name = fn.optString("name", ""),
                    description = fn.optString("description", ""),
                    parameters = (fn.optJSONObject("parameters") ?: JSONObject())
                        .toString(),
                    kind = exec.optString("kind", PLUGIN_HTTP),
                    config = (exec.optJSONObject("config") ?: JSONObject()).toString()
                )
            )
        }
        // MCP 单条
        val schema = o.optJSONObject("inputSchema")
        if (schema != null) {
            val url = defaultMcpUrl.ifBlank { o.optString("url", "") }
            return listOf(
                PluginTool(
                    name = o.optString("name", ""),
                    description = o.optString("description", ""),
                    parameters = schema.toString(),
                    kind = PLUGIN_MCP,
                    config = JSONObject().apply {
                        put("url", url)
                        put("tool", o.optString("name", ""))
                    }.toString()
                )
            )
        }
        // 本应用格式
        return listOf(
            PluginTool(
                name = o.optString("name", ""),
                description = o.optString("description", ""),
                parameters = (o.optJSONObject("parameters") ?: JSONObject()).toString(),
                kind = o.optString("kind", PLUGIN_HTTP),
                config = (o.optJSONObject("config") ?: JSONObject()).toString()
            )
        )
    }

    private fun fromHttpUrl(url: String): PluginTool = PluginTool(
        name = url.substringAfterLast("/").substringBefore("?").ifBlank { "http_tool" },
        description = "GET $url",
        kind = PLUGIN_HTTP,
        config = JSONObject().apply {
            put("method", "GET")
            put("url", url)
        }.toString()
    )
}