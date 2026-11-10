package com.zhiwei.agent.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
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
import kotlin.math.roundToInt

/** 一个可被模型调用的工具 */
data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JSONObject,
    /** 纯本地只读工具：不消耗每回合的工具预算，省着用不如随便用 */
    val free: Boolean = false
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
    const val TOOL_GET_VOLUME = "get_volume"
    const val TOOL_SET_VOLUME = "set_volume"
    const val TOOL_SET_BRIGHTNESS = "set_brightness"
    const val TOOL_TORCH = "toggle_torch"
    const val TOOL_VIBRATE = "vibrate"
    const val TOOL_CLIPBOARD_WRITE = "write_clipboard"
    const val TOOL_OPEN_SETTING = "open_system_setting"
    const val TOOL_LIST_APPS = "list_apps"
    const val TOOL_OPEN_APP = "open_app"
    const val TOOL_MEDIA = "media_control"
    const val TOOL_SPEAK = "speak_text"

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
            schema(),
            free = true
        )

        list += ToolSpec(
            TOOL_CALC,
            "计算器",
            "计算数学表达式。可以一次传多个表达式，批处理比一个一个算快得多。支持 + - * / % 和括号、小数。",
            schema(
                "expressions" to obj(
                    "type" to "array",
                    "description" to "要计算的表达式列表，例如 [\"90000/600\", \"(128+72)*3/8\"]",
                    "items" to obj("type" to "string")
                ),
                "expression" to str("只算一个时用这个字段")
            ),
            free = true
        )

        list += ToolSpec(
            TOOL_DEVICE,
            "设备信息",
            "获取手机型号、系统版本、电量、内存等信息。",
            schema(),
            free = true
        )

        list += ToolSpec(
            TOOL_CLIPBOARD,
            "读剪贴板",
            "读取手机剪贴板当前的文本内容。",
            schema(),
            free = true
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
            schema("query" to str("检索关键词"), required = listOf("query")),
            free = true
        )

        list += ToolSpec(
            TOOL_KB_SEARCH,
            "查知识库",
            "在知识库里检索用户导入的资料、笔记、文档片段。",
            schema("query" to str("检索关键词"), required = listOf("query")),
            free = true
        )

        if (settings.enableDeviceTools) {
            list += ToolSpec(
                TOOL_GET_VOLUME,
                "查音量",
                "读取当前的媒体音量与铃声音量（百分比）。",
                schema(),
                free = true
            )
            list += ToolSpec(
                TOOL_SET_VOLUME,
                "调音量",
                "设置媒体音量，传 0-100 的百分比。用户说「声音小一点/静音」时用它。",
                schema("percent" to obj("type" to "integer", "description" to "目标音量百分比 0-100"), required = listOf("percent"))
            )
            list += ToolSpec(
                TOOL_SET_BRIGHTNESS,
                "调亮度",
                "设置屏幕亮度，传 1-100 的百分比。需要用户在系统设置里给过「修改系统设置」权限。",
                schema("percent" to obj("type" to "integer", "description" to "目标亮度百分比 1-100"), required = listOf("percent"))
            )
            list += ToolSpec(
                TOOL_TORCH,
                "手电筒",
                "打开或关闭手电筒（闪光灯）。用户说「开手电」「关灯」时用它。",
                schema("on" to obj("type" to "boolean", "description" to "true 打开，false 关闭"), required = listOf("on"))
            )
            list += ToolSpec(
                TOOL_VIBRATE,
                "震动",
                "让手机震一下，可指定毫秒数（50-3000）。",
                schema("duration_ms" to obj("type" to "integer", "description" to "震动时长，默认 300 毫秒"))
            )
            list += ToolSpec(
                TOOL_CLIPBOARD_WRITE,
                "写剪贴板",
                "把一段文本放进手机剪贴板，方便用户去别处粘贴。",
                schema("text" to str("要复制的文本"), required = listOf("text"))
            )
            list += ToolSpec(
                TOOL_OPEN_SETTING,
                "打开系统设置",
                "打开系统设置页。page 可选 wifi / bluetooth / display / sound / battery / location / apps / write（修改系统设置权限页），留空则打开设置首页。",
                schema("page" to str("要打开的设置页，例如 wifi"))
            )
            list += ToolSpec(
                TOOL_LIST_APPS,
                "列出应用",
                "列出手机上已安装且可启动的应用，可按名称关键词过滤。用来查某个应用在不在、包名是什么。",
                schema("keyword" to str("可选，应用名关键词")),
                free = true
            )
            list += ToolSpec(
                TOOL_OPEN_APP,
                "打开应用",
                "按应用名或包名打开一个已安装的应用。",
                schema("target" to str("应用名（如 微信）或包名（如 com.tencent.mm）"), required = listOf("target"))
            )
            list += ToolSpec(
                TOOL_MEDIA,
                "媒体控制",
                "控制正在播放的音乐/视频：play（播放）、pause（暂停）、toggle（播放/暂停）、next（下一首）、previous（上一首）。",
                schema("action" to str("play / pause / toggle / next / previous"), required = listOf("action"))
            )
            list += ToolSpec(
                TOOL_SPEAK,
                "朗读",
                "用系统语音把一段文字读出来，适合用户想「听」答案的时候。",
                schema("text" to str("要朗读的文字"), required = listOf("text"))
            )
        }

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
        TOOL_GET_VOLUME -> "查音量"
        TOOL_SET_VOLUME -> "调音量"
        TOOL_SET_BRIGHTNESS -> "调亮度"
        TOOL_TORCH -> "手电筒"
        TOOL_VIBRATE -> "震动"
        TOOL_CLIPBOARD_WRITE -> "写剪贴板"
        TOOL_OPEN_SETTING -> "打开系统设置"
        TOOL_LIST_APPS -> "列出应用"
        TOOL_OPEN_APP -> "打开应用"
        TOOL_MEDIA -> "媒体控制"
        TOOL_SPEAK -> "朗读"
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
                    TOOL_CALC -> ToolOutcome(calculateMany(args))
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
                    TOOL_GET_VOLUME -> ToolOutcome(volumeInfo(env.context))
                    TOOL_SET_VOLUME -> ToolOutcome(setVolume(env.context, args.optInt("percent", 50)))
                    TOOL_SET_BRIGHTNESS -> ToolOutcome(setBrightness(env.context, args.optInt("percent", 60)))
                    TOOL_TORCH -> ToolOutcome(toggleTorch(env.context, args.optBoolean("on", true)))
                    TOOL_VIBRATE -> ToolOutcome(vibrate(env.context, args.optInt("duration_ms", 300).toLong()))
                    TOOL_CLIPBOARD_WRITE -> ToolOutcome(writeClipboard(env.context, args.optString("text", "")))
                    TOOL_OPEN_SETTING -> ToolOutcome(openSystemSetting(env.context, args.optString("page", "")))
                    TOOL_LIST_APPS -> ToolOutcome(listApps(env.context, args.optString("keyword", "")))
                    TOOL_OPEN_APP -> ToolOutcome(openApp(env.context, args.optString("target", "")))
                    TOOL_MEDIA -> ToolOutcome(mediaControl(env.context, args.optString("action", "")))
                    TOOL_SPEAK -> ToolOutcome(speak(env.context, args.optString("text", "")))
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

    /** 一次算一批：模型常把好几步运算拆成多次调用，这里一次算完 */
    private fun calculateMany(args: JSONObject): String {
        val items = mutableListOf<String>()
        args.optJSONArray("expressions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) items.add(s)
            }
        }
        val one = args.optString("expression", "").trim()
        if (items.isEmpty() && one.isNotEmpty()) items.add(one)
        if (items.isEmpty()) return "没有提供表达式"
        if (items.size == 1) return calculate(items[0])
        return items.joinToString("\n") { calculate(it) }
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

    // ---------- 设备操控 ----------

    private val ttsLock = Any()
    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var ttsPending: String? = null

    private fun audio(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun percentOf(value: Int, max: Int): Int = if (max > 0) value * 100 / max else 0

    private fun volumeInfo(context: Context): String {
        val am = audio(context) ?: return "音频服务不可用"
        val mediaMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val media = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        val ring = am.getStreamVolume(AudioManager.STREAM_RING)
        return "媒体音量：$media / $mediaMax（${percentOf(media, mediaMax)}%）\n" +
            "铃声音量：$ring / $ringMax（${percentOf(ring, ringMax)}%）"
    }

    private fun setVolume(context: Context, percent: Int): String {
        val am = audio(context) ?: return "音频服务不可用"
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val p = percent.coerceIn(0, 100)
        val target = (max * p / 100.0).roundToInt().coerceIn(0, max)
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            "媒体音量已设为 $p%"
        } catch (e: Exception) {
            "调音量失败：${e.message}"
        }
    }

    private fun setBrightness(context: Context, percent: Int): String {
        if (!Settings.System.canWrite(context)) {
            return "还没有「修改系统设置」权限。去 设置 → 应用 → 知微智能体 → 修改系统设置 打开后再试。"
        }
        val p = percent.coerceIn(1, 100)
        val value = (255 * p / 100.0).roundToInt().coerceIn(1, 255)
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            "屏幕亮度已设为 $p%"
        } catch (e: Exception) {
            "调亮度失败：${e.message}"
        }
    }

    private fun toggleTorch(context: Context, on: Boolean): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return "相机服务不可用"
        val id = try {
            cm.cameraIdList.firstOrNull { c ->
                cm.getCameraCharacteristics(c).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        } ?: return "这台设备没有可用的闪光灯"
        return try {
            cm.setTorchMode(id, on)
            if (on) "手电筒已打开" else "手电筒已关闭"
        } catch (e: Exception) {
            "手电筒操作失败：${e.message}"
        }
    }

    private fun vibrate(context: Context, ms: Long): String {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            ?: return "设备不支持震动"
        if (!v.hasVibrator()) return "设备不支持震动"
        val d = ms.coerceIn(50L, 3000L)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(d)
            }
            "已震动 $d 毫秒"
        } catch (e: Exception) {
            "震动失败：${e.message}"
        }
    }

    private fun writeClipboard(context: Context, text: String): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "剪贴板不可用"
        if (text.isBlank()) return "没有要复制的内容"
        return try {
            cm.setPrimaryClip(ClipData.newPlainText("知微", text))
            "已复制到剪贴板（${text.length} 字）"
        } catch (e: Exception) {
            "写剪贴板失败：${e.message}"
        }
    }

    private fun openSystemSetting(context: Context, page: String): String {
        val action = when (page.trim().lowercase()) {
            "wifi", "无线", "无线网络" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "蓝牙" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "显示" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "声音" -> Settings.ACTION_SOUND_SETTINGS
            "battery", "电池" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location", "位置" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps", "应用" -> Settings.ACTION_APPLICATION_SETTINGS
            "write", "修改系统设置" -> Settings.ACTION_MANAGE_WRITE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) {
                Intent(action, Uri.parse("package:" + context.packageName))
            } else {
                Intent(action)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "已打开系统设置页"
        } catch (e: Exception) {
            "打开设置失败：${e.message}"
        }
    }

    private fun launchableApps(context: Context): List<android.content.pm.ResolveInfo> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
    } catch (e: Exception) {
        emptyList()
    }

    private fun listApps(context: Context, keyword: String): String {
        val pm = context.packageManager
        val kw = keyword.trim().lowercase()
        val lines = mutableListOf<String>()
        val seen = HashSet<String>()
        for (info in launchableApps(context)) {
            val activity = info.activityInfo ?: continue
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                ""
            }
            if (kw.isNotEmpty() && !label.lowercase().contains(kw) && !activity.packageName.lowercase().contains(kw)) {
                continue
            }
            if (!seen.add(activity.packageName)) continue
            lines.add("$label（${activity.packageName}）")
            if (lines.size >= 25) break
        }
        return if (lines.isEmpty()) {
            if (kw.isEmpty()) "没有查到可启动的应用" else "没有名字里带「$keyword」的应用"
        } else {
            "找到 ${lines.size} 个：\n" + lines.joinToString("\n")
        }
    }

    private fun openApp(context: Context, target: String): String {
        val pm = context.packageManager
        val t = target.trim()
        if (t.isEmpty()) return "没有提供应用名或包名"
        try {
            pm.getLaunchIntentForPackage(t)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "已打开 $t"
            }
        } catch (_: Exception) {
        }
        val hit = launchableApps(context).firstOrNull { info ->
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                ""
            }
            label.contains(t, ignoreCase = true) ||
                (info.activityInfo?.packageName ?: "").contains(t, ignoreCase = true)
        } ?: return "没找到叫「$t」的应用，可以先用 list_apps 看一下有什么"
        val activity = hit.activityInfo ?: return "没找到叫「$t」的应用"
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(android.content.ComponentName(activity.packageName, activity.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "已打开「${hit.loadLabel(pm)}」"
        } catch (e: Exception) {
            "打开失败：${e.message}"
        }
    }

    private fun mediaControl(context: Context, action: String): String {
        val am = audio(context) ?: return "音频服务不可用"
        val code = when (action.trim().lowercase()) {
            "play", "播放", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "暂停" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle", "播放暂停" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "下一首" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "上一首" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "不认识的动作：$action（可用 play / pause / toggle / next / previous）"
        }
        return try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            "已发送媒体控制：$action"
        } catch (e: Exception) {
            "媒体控制失败：${e.message}"
        }
    }

    private fun speak(context: Context, text: String): String {
        val body = text.trim()
        if (body.isEmpty()) return "没有要朗读的内容"
        val app = context.applicationContext
        val clipped = body.take(2000)
        synchronized(ttsLock) {
            val engine = ttsEngine ?: TextToSpeech(app) { status ->
                synchronized(ttsLock) {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        ttsEngine?.language = Locale.CHINA
                        val pending = ttsPending
                        ttsPending = null
                        if (pending != null) {
                            ttsEngine?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "zhiwei")
                        }
                    }
                }
            }.also { ttsEngine = it }
            if (ttsReady) {
                ttsPending = null
                engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, "zhiwei")
            } else {
                ttsPending = clipped
            }
        }
        return "正在朗读（${body.length} 字）"
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