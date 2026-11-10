package com.zhiwei.agent.agent

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import com.zhiwei.agent.data.AppSettings
import com.zhiwei.agent.data.KnowledgeItem
import com.zhiwei.agent.data.KnowledgeStore
import com.zhiwei.agent.data.MemoryItem
import com.zhiwei.agent.data.MemoryStore
import com.zhiwei.agent.data.PluginStore
import com.zhiwei.agent.data.PluginTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 一个可被模型调用的工具 */
data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JSONObject
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "function")
        put(
            "function",
            JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", parameters)
            }
        )
    }
}

/** 一次工具执行的结果；顺带带回需要落库的记忆或知识 */
data class ToolOutcome(
    val text: String,
    val newMemory: MemoryItem? = null,
    val newKnowledge: KnowledgeItem? = null
)

/** 工具运行环境：设置 + 三个本地库的快照 */
data class AgentEnv(
    val context: Context,
    val settings: AppSettings,
    val memory: MemoryStore,
    val knowledge: KnowledgeStore,
    val pluginStore: PluginStore,
    val memories: List<MemoryItem> = emptyList(),
    val knowledgeList: List<KnowledgeItem> = emptyList(),
    val pluginList: List<PluginTool> = emptyList()
)

private fun obj(vararg pairs: Pair<String, Any>): JSONObject {
    val o = JSONObject()
    pairs.forEach { (k, v) -> o.put(k, v) }
    return o
}

private fun schema(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
    val p = JSONObject()
    props.forEach { (k, v) -> p.put(k, v) }
    return obj(
        "type" to "object",
        "properties" to p,
        "required" to org.json.JSONArray(required)
    )
}

private fun str(desc: String): JSONObject = obj("type" to "string", "description" to desc)

/**
 * 内置工具集。所有工具都在本机执行，不经过任何中转服务器。
 */
object ToolRegistry {

    const val TOOL_TIME = "get_current_time"
    const val TOOL_CALC = "calculate"
    const val TOOL_DEVICE = "get_device_info"
    const val TOOL_CLIPBOARD = "read_clipboard"
    const val TOOL_OPEN_URL = "open_url"
    const val TOOL_SHELL = "run_shell"
    const val TOOL_FETCH = "fetch_webpage"
    const val TOOL_SEARCH = "web_search"
    const val TOOL_REMEMBER = "remember"
    const val TOOL_RECALL = "recall"
    const val TOOL_KB_SEARCH = "search_knowledge"
    const val TOOL_KB_SAVE = "save_knowledge"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    fun specs(env: AgentEnv): List<ToolSpec> {
        val settings = env.settings
        val list = mutableListOf<ToolSpec>()

        list += ToolSpec(
            TOOL_TIME,
            "当前时间",
            "获取手机当前的日期、时间与星期。",
            schema()
        )

        list += ToolSpec(
            TOOL_CALC,
            "计算器",
            "计算一个数学表达式，支持 + - * / % 和括号、小数。",
            schema("expression" to str("要计算的表达式，例如 (12+8)*3/4"), required = listOf("expression"))
        )

        list += ToolSpec(
            TOOL_DEVICE,
            "设备信息",
            "获取手机型号、系统版本、电量、内存等信息。",
            schema()
        )

        list += ToolSpec(
            TOOL_CLIPBOARD,
            "读剪贴板",
            "读取手机剪贴板当前的文本内容。",
            schema()
        )

        list += ToolSpec(
            TOOL_OPEN_URL,
            "打开链接",
            "用系统浏览器打开一个网址。",
            schema("url" to str("完整网址，需以 http:// 或 https:// 开头"), required = listOf("url"))
        )

        list += ToolSpec(
            TOOL_FETCH,
            "抓取网页",
            "抓取网页并提取正文纯文本，适合读取指定页面的内容。",
            schema("url" to str("完整网址"), required = listOf("url"))
        )

        list += ToolSpec(
            TOOL_REMEMBER,
            "记住",
            "把用户透露的长期有用信息存进记忆库，之后任何对话都能用到。用户说「记住……」时用它。",
            schema(
                "text" to str("要记住的一句话，尽量简洁且自带上下文，例如「用户住在杭州」"),
                "tags" to str("可选，逗号分隔的标签，例如 偏好,地址"),
                required = listOf("text")
            )
        )

        list += ToolSpec(
            TOOL_RECALL,
            "回忆",
            "在记忆库里检索以前记下的信息。需要用户偏好、历史事实时先查它。",
            schema("query" to str("检索关键词"), required = listOf("query"))
        )

        list += ToolSpec(
            TOOL_KB_SEARCH,
            "查知识库",
            "在知识库里检索用户导入的资料、笔记、文档片段。",
            schema("query" to str("检索关键词"), required = listOf("query"))
        )

        list += ToolSpec(
            TOOL_KB_SAVE,
            "存进知识库",
            "把一段值得留存的资料写进知识库，方便以后跨对话检索。",
            schema(
                "title" to str("资料标题"),
                "content" to str("资料正文"),
                required = listOf("title", "content")
            )
        )

        if (settings.tavilyApiKey.isNotBlank()) {
            list += ToolSpec(
                TOOL_SEARCH,
                "联网搜索",
                "用搜索引擎检索实时信息，返回若干条结果摘要。",
                schema("query" to str("搜索关键词"), required = listOf("query"))
            )
        }

        if (settings.enableShellTool) {
            list += ToolSpec(
                TOOL_SHELL,
                "终端命令",
                "在手机本地执行一条 shell 命令并返回输出，可用于查看文件、系统信息等。",
                schema("command" to str("要执行的 shell 命令"), required = listOf("command"))
            )
        }

        if (settings.enablePlugins) {
            env.pluginList.filter { it.enabled }.forEach { p ->
                list += ToolSpec(
                    name = p.safeName(),
                    label = p.name,
                    description = p.description.ifBlank { "自定义工具：${p.name}" },
                    parameters = p.paramsJson()
                )
            }
        }

        return list
    }

    fun labelFor(env: AgentEnv, name: String): String = when (name) {
        TOOL_TIME -> "当前时间"
        TOOL_CALC -> "计算器"
        TOOL_DEVICE -> "设备信息"
        TOOL_CLIPBOARD -> "读剪贴板"
        TOOL_OPEN_URL -> "打开链接"
        TOOL_SHELL -> "终端命令"
        TOOL_FETCH -> "抓取网页"
        TOOL_SEARCH -> "联网搜索"
        TOOL_REMEMBER -> "记住"
        TOOL_RECALL -> "回忆"
        TOOL_KB_SEARCH -> "查知识库"
        TOOL_KB_SAVE -> "存进知识库"
        else -> env.pluginList.firstOrNull { it.safeName() == name }?.name ?: name
    }

    suspend fun execute(env: AgentEnv, name: String, argsJson: String): ToolOutcome =
        withContext(Dispatchers.IO) {
            val args = try {
                if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            } catch (_: Exception) {
                JSONObject()
            }
            try {
                val plugin = env.pluginList.firstOrNull { it.safeName() == name }
                if (plugin != null) {
                    return@withContext ToolOutcome(PluginRunner.execute(env, plugin, argsJson))
                }
                when (name) {
                    TOOL_TIME -> ToolOutcome(currentTime())
                    TOOL_CALC -> ToolOutcome(calculate(args.optString("expression", "")))
                    TOOL_DEVICE -> ToolOutcome(deviceInfo(env.context))
                    TOOL_CLIPBOARD -> ToolOutcome(readClipboard(env.context))
                    TOOL_OPEN_URL -> ToolOutcome(openUrl(env.context, args.optString("url", "")))
                    TOOL_FETCH -> ToolOutcome(fetchPage(args.optString("url", "")))
                    TOOL_SEARCH -> ToolOutcome(search(env.settings, args.optString("query", "")))
                    TOOL_SHELL -> ToolOutcome(runShell(env.context, args.optString("command", "")))
                    TOOL_REMEMBER -> remember(args)
                    TOOL_RECALL -> ToolOutcome(recall(env, args.optString("query", "")))
                    TOOL_KB_SEARCH -> ToolOutcome(searchKnowledge(env, args.optString("query", "")))
                    TOOL_KB_SAVE -> saveKnowledge(args)
                    else -> ToolOutcome("未知工具：$name")
                }
            } catch (e: Exception) {
                ToolOutcome("工具执行失败：${e.message}")
            }
        }

    // ---------- 记忆 / 知识库 ----------

    private fun remember(args: JSONObject): ToolOutcome {
        val text = args.optString("text", "").trim()
        if (text.isEmpty()) return ToolOutcome("没有提供要记住的内容")
        val tags = args.optString("tags", "")
            .split(',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return ToolOutcome(
            text = "已记住：$text",
            newMemory = MemoryItem(text = text, tags = tags, source = "对话")
        )
    }

    private fun recall(env: AgentEnv, query: String): String {
        if (env.memories.isEmpty()) return "记忆库还是空的。"
        val hits = env.memory.search(env.memories, query, 6)
        if (hits.isEmpty()) return "记忆库里没有和「$query」相关的记录。"
        return hits.joinToString("\n") { "- " + it.text }
    }

    private fun searchKnowledge(env: AgentEnv, query: String): String {
        if (env.knowledgeList.isEmpty()) return "知识库还是空的。"
        val hits = env.knowledge.search(env.knowledgeList, query, 4)
        if (hits.isEmpty()) return "知识库里没有和「$query」相关的片段。"
        return hits.joinToString("\n\n") { "【${it.title}】\n" + it.text.take(1200) }
    }

    private fun saveKnowledge(args: JSONObject): ToolOutcome {
        val title = args.optString("title", "").trim().ifBlank { "未命名知识" }
        val content = args.optString("content", "").trim()
        if (content.isEmpty()) return ToolOutcome("没有提供正文")
        return ToolOutcome(
            text = "已存进知识库：$title（${content.length} 字）",
            newKnowledge = KnowledgeItem(title = title, content = content, source = "对话")
        )
    }

    // ---------- 基础工具实现 ----------

    /** 表达式求值：递归下降，支持 + - * / % 与括号、小数 */
    private fun calculate(expr: String): String {
        val e = expr.trim()
        if (e.isEmpty()) return "没有提供表达式"
        return try {
            val v = ExprParser(e).parse()
            val text = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            "$e = $text"
        } catch (ex: Exception) {
            "无法计算：${ex.message}"
        }
    }

    private class ExprParser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skip()
            if (pos < s.length) throw IllegalArgumentException("第 ${pos + 1} 个字符无法识别")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                    val op = s[pos++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else {
                    return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skip()
                if (pos < s.length && (s[pos] == '*' || s[pos] == '/' || s[pos] == '%')) {
                    val op = s[pos++]
                    val r = factor()
                    v = when (op) {
                        '*' -> v * r
                        '/' -> v / r
                        else -> v % r
                    }
                } else {
                    return v
                }
            }
        }

        private fun factor(): Double {
            skip()
            if (pos >= s.length) throw IllegalArgumentException("表达式不完整")
            val c = s[pos]
            if (c == '(') {
                pos++
                val v = expr()
                skip()
                if (pos < s.length && s[pos] == ')') pos++
                return v
            }
            if (c == '-') {
                pos++
                return -factor()
            }
            if (c == '+') {
                pos++
                return factor()
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("遇到非法字符 '$c'")
            return s.substring(start, pos).toDouble()
        }

        private fun skip() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }

    private fun currentTime(): String {
        val now = Date()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA)
        val tz = java.util.TimeZone.getDefault().id
        return "${fmt.format(now)}（时区 $tz）"
    }

    private fun deviceInfo(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val rt = Runtime.getRuntime()
        val totalMb = rt.maxMemory() / 1024 / 1024
        val freeMb = rt.freeMemory() / 1024 / 1024
        return buildString {
            append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("系统：Android ").append(Build.VERSION.RELEASE)
            append("（API ").append(Build.VERSION.SDK_INT).append("）\n")
            append("电量：").append(if (level >= 0) "$level%" else "未知").append('\n')
            append("可用堆内存：约 ").append(freeMb).append(" MB / ").append(totalMb).append(" MB")
        }
    }

    private fun readClipboard(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "剪贴板不可用"
        val clip = cm.primaryClip ?: return "剪贴板为空"
        if (clip.itemCount == 0) return "剪贴板为空"
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
        return if (text.isBlank()) "剪贴板为空" else text.take(4000)
    }

    private fun openUrl(context: Context, url: String): String {
        if (url.isBlank()) return "没有提供网址"
        val fixed = if (url.startsWith("http")) url else "https://$url"
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "已用系统浏览器打开：$fixed"
        } catch (e: Exception) {
            "打开失败：${e.message}"
        }
    }

    private fun fetchPage(url: String): String {
        if (url.isBlank()) return "没有提供网址"
        val fixed = if (url.startsWith("http")) url else "https://$url"
        val request = Request.Builder()
            .url(fixed)
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return "抓取失败：HTTP ${resp.code}"
            val body = resp.body?.string() ?: return "页面为空"
            val text = htmlToText(body)
            return if (text.isBlank()) "页面没有可读文本" else text.take(6000)
        }
    }

    private fun search(settings: AppSettings, query: String): String {
        if (query.isBlank()) return "没有提供关键词"
        if (settings.tavilyApiKey.isBlank()) return "未配置搜索 API Key"
        val payload = obj(
            "api_key" to settings.tavilyApiKey,
            "query" to query,
            "max_results" to 5,
            "search_depth" to "basic"
        )
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return "搜索失败：HTTP ${resp.code} $text"
            val json = JSONObject(text)
            val results = json.optJSONArray("results") ?: return "没有搜索结果"
            val sb = StringBuilder()
            for (i in 0 until minOf(results.length(), 5)) {
                val item = results.optJSONObject(i) ?: continue
                sb.append(i + 1).append(". ").append(item.optString("title", "")).append('\n')
                sb.append(item.optString("url", "")).append('\n')
                sb.append(item.optString("content", "").take(500)).append("\n\n")
            }
            return sb.toString().ifBlank { "没有搜索结果" }
        }
    }

    private fun runShell(context: Context, command: String): String {
        if (command.isBlank()) return "没有提供命令"
        val home = ShellRunner.homeDir(context.filesDir)
        val result = ShellRunner.run(command, home, timeoutMs = 20000, maxChars = 6000)
        val body = result.output.trim()
        return buildString {
            if (body.isNotEmpty()) append(body) else append("（无输出）")
            append("\n[退出码 ").append(result.exitCode).append(']')
        }
    }

    fun htmlToText(html: String): String {
        var s = html.replace(Regex("(?is)<script.*?</script>"), " ")
        s = s.replace(Regex("(?is)<style.*?</style>"), " ")
        s = s.replace(Regex("(?is)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?is)</(p|div|li|h[1-6]|tr|section|article)>"), "\n")
        s = s.replace(Regex("(?s)<[^>]+>"), "")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&" + "quot;", "\"")
            .replace("&#39;", "'")
        s = s.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        s = s.replace(Regex(" *\\n *"), "\n")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }
}