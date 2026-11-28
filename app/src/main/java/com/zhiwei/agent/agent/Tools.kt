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
import java.io.File
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
    val newKnowledge: KnowledgeItem? = null,
    /** 工具产出的图片（例如截屏）：会被塞回对话，让模型直接看图 */
    val images: List<String> = emptyList()
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
    const val TOOL_LIST_DIR = "list_dir"
    const val TOOL_READ_FILE = "read_file"
    const val TOOL_WRITE_FILE = "write_file"
    const val TOOL_APPEND_FILE = "append_file"
    const val TOOL_MAKE_DIR = "make_dir"
    const val TOOL_DELETE_PATH = "delete_path"
    const val TOOL_MOVE_PATH = "move_path"
    const val TOOL_COPY_PATH = "copy_path"
    const val TOOL_FILE_INFO = "file_info"
    const val TOOL_SCREENSHOT = "screenshot"
    const val TOOL_UI_TAP = "ui_tap"
    const val TOOL_UI_SWIPE = "ui_swipe"
    const val TOOL_UI_TYPE = "ui_type"
    const val TOOL_UI_KEY = "ui_key"
    const val TOOL_UI_CLICK_TEXT = "ui_click_text"
    const val TOOL_UI_SCREEN = "ui_read_screen"
    const val TOOL_PRIV = "check_privilege"

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

        list += ToolSpec(
            TOOL_LIST_DIR,
            "列目录",
            "列出工作区里某个目录下的文件与子目录。path 留空就是工作区根目录。",
            schema("path" to str("可选，目录路径；留空为工作区根目录")),
            free = true
        )
        list += ToolSpec(
            TOOL_READ_FILE,
            "读文件",
            "读取工作区里某个文件的内容。文本文件直接返回正文；图片会告知路径（想让模型直接看图，请让用户把图片作为附件发到对话里）。大文件只返回前一部分。",
            schema("path" to str("文件路径"), required = listOf("path")),
            free = true
        )
        list += ToolSpec(
            TOOL_WRITE_FILE,
            "写文件",
            "把一段文本写进工作区的文件（覆盖写入），目录不存在会自动创建。",
            schema(
                "path" to str("文件路径"),
                "content" to str("要写入的文本"),
                required = listOf("path", "content")
            )
        )
        list += ToolSpec(
            TOOL_APPEND_FILE,
            "追加文件",
            "往工作区已有文件末尾追加文本（不覆盖原内容）。文件不存在时自动创建。写长文本分多次写、或给日志追加时用它。",
            schema(
                "path" to str("文件路径"),
                "content" to str("要追加的文本"),
                required = listOf("path", "content")
            )
        )
        list += ToolSpec(
            TOOL_MAKE_DIR,
            "建目录",
            "在工作区创建目录，父目录不存在会一并创建（相当于 mkdir -p）。已存在就直接返回成功。",
            schema("path" to str("目录路径"), required = listOf("path"))
        )
        list += ToolSpec(
            TOOL_DELETE_PATH,
            "删除",
            "删除工作区里的文件或目录。删目录时默认连内容一起删（recursive 默认 true）。删完会用 list_dir 复查一次，确认真的没了。",
            schema(
                "path" to str("文件或目录路径"),
                "recursive" to obj("type" to "boolean", "description" to "删目录时是否连内容一起删，默认 true"),
                required = listOf("path")
            )
        )
        list += ToolSpec(
            TOOL_MOVE_PATH,
            "移动/重命名",
            "把工作区里的文件或目录移到另一个路径；同目录内换名字就是重命名。目标存在会报错，不覆盖。",
            schema(
                "from" to str("原路径"),
                "to" to str("目标路径"),
                required = listOf("from", "to")
            )
        )
        list += ToolSpec(
            TOOL_COPY_PATH,
            "复制",
            "复制工作区里的文件或目录（目录递归复制）。目标存在会报错，不覆盖。",
            schema(
                "from" to str("原路径"),
                "to" to str("目标路径"),
                required = listOf("from", "to")
            )
        )
        list += ToolSpec(
            TOOL_FILE_INFO,
            "查路径详情",
            "查看某个路径是否存在、是文件还是目录、大小、可读可写与最后修改时间。动手前先查一眼，比猜可靠。",
            schema("path" to str("文件或目录路径"), required = listOf("path"))
        )
        list += ToolSpec(
            TOOL_SCREENSHOT,
            "截屏",
            "截取当前手机屏幕，截好的图会直接带回对话，模型能看图作答。第一次用需要用户在系统弹窗里授权一次（应用内会弹出）。",
            schema()
        )
        // 无障碍自动化：真正「控制整台设备」的能力，需要用户到系统无障碍设置页开启一次
        if (settings.enableDeviceTools) {
            list += ToolSpec(
                TOOL_UI_TAP,
                "点击屏幕",
                "模拟点击屏幕上的坐标。需要先开启无障碍服务（未开启时会返回开启引导）。",
                schema(
                    "x" to obj("type" to "integer", "description" to "横坐标像素"),
                    "y" to obj("type" to "integer", "description" to "纵坐标像素"),
                    required = listOf("x", "y")
                )
            )
            list += ToolSpec(
                TOOL_UI_SWIPE,
                "滑动屏幕",
                "模拟从一点滑到另一点，用于翻页、下拉、手势。需要无障碍服务。",
                schema(
                    "x1" to obj("type" to "integer", "description" to "起点横坐标"),
                    "y1" to obj("type" to "integer", "description" to "起点纵坐标"),
                    "x2" to obj("type" to "integer", "description" to "终点横坐标"),
                    "y2" to obj("type" to "integer", "description" to "终点纵坐标"),
                    "duration_ms" to obj("type" to "integer", "description" to "滑动时长，默认 400"),
                    required = listOf("x1", "y1", "x2", "y2")
                )
            )
            list += ToolSpec(
                TOOL_UI_TYPE,
                "输入文字",
                "在当前焦点输入框输入文字。需要无障碍服务，且目标输入框已获得焦点。",
                schema("text" to str("要输入的文字"), required = listOf("text"))
            )
            list += ToolSpec(
                TOOL_UI_KEY,
                "按系统键",
                "模拟按返回键 / 主页键 / 最近任务键。key 可选 back / home / recents。需要无障碍服务。",
                schema("key" to str("back / home / recents"), required = listOf("key"))
            )
            list += ToolSpec(
                TOOL_UI_CLICK_TEXT,
                "按文字点击",
                "在当前屏幕上查找指定文字的可点击控件并点击。需要无障碍服务。",
                schema("text" to str("目标控件上的文字"), required = listOf("text"))
            )
            list += ToolSpec(
                TOOL_UI_SCREEN,
                "读屏幕控件",
                "列出当前屏幕上所有可点击控件及其文字与中心坐标。做自动操作前先调它，能大幅提高点击命中率。",
                schema()
            )
        }

        // 联网搜索始终可用：配了 Tavily Key 就用 Tavily，没配就走内置的必应 RSS 通道
        list += ToolSpec(
            TOOL_SEARCH,
            "联网搜索",
            "用搜索引擎检索实时信息，返回若干条结果摘要（标题 / 链接 / 摘要）。",
            schema("query" to str("搜索关键词"), required = listOf("query"))
        )
        list += ToolSpec(
            TOOL_PRIV,
            "查权限通道",
            "查看当前能用的命令执行通道（沙箱 / Shizuku / Root）与授权状态，含屏幕自动化是否开启。",
            schema()
        )

        if (settings.enableShellTool) {
            val envLabel = env.settings.linuxEnv().label
            list += ToolSpec(
                TOOL_SHELL,
                "终端命令",
                "在本机执行一条 shell 命令并返回输出。当前运行环境：$envLabel。" +
                    "可用于查看文件、系统信息、开关应用、改系统设置等；" +
                    "权限取决于设置里的命令执行通道（沙箱 / Shizuku shell 权限 / Root 全权限），" +
                    "不确定能做什么就先调 $TOOL_PRIV 查一次。",
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
        TOOL_LIST_DIR -> "列目录"
        TOOL_READ_FILE -> "读文件"
        TOOL_WRITE_FILE -> "写文件"
        TOOL_APPEND_FILE -> "追加文件"
        TOOL_MAKE_DIR -> "建目录"
        TOOL_DELETE_PATH -> "删除"
        TOOL_MOVE_PATH -> "移动/重命名"
        TOOL_COPY_PATH -> "复制"
        TOOL_FILE_INFO -> "查路径详情"
        TOOL_SCREENSHOT -> "截屏"
        TOOL_UI_TAP -> "点击屏幕"
        TOOL_UI_SWIPE -> "滑动屏幕"
        TOOL_UI_TYPE -> "输入文字"
        TOOL_UI_KEY -> "按系统键"
        TOOL_UI_CLICK_TEXT -> "按文字点击"
        TOOL_UI_SCREEN -> "读屏幕控件"
        TOOL_PRIV -> "查权限通道"
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
                    TOOL_SHELL -> ToolOutcome(runShell(env.context, env.settings, args.optString("command", "")))
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
                    TOOL_LIST_DIR -> ToolOutcome(listDir(env.context, args.optString("path", "")))
                    TOOL_READ_FILE -> ToolOutcome(readFile(env.context, args.optString("path", "")))
                    TOOL_WRITE_FILE -> ToolOutcome(
                        writeFile(env.context, args.optString("path", ""), args.optString("content", ""))
                    )
                    TOOL_APPEND_FILE -> ToolOutcome(
                        appendFile(env.context, args.optString("path", ""), args.optString("content", ""))
                    )
                    TOOL_MAKE_DIR -> ToolOutcome(makeDir(env.context, args.optString("path", "")))
                    TOOL_DELETE_PATH -> ToolOutcome(
                        deletePath(env.context, args.optString("path", ""), args.optBoolean("recursive", true))
                    )
                    TOOL_MOVE_PATH -> ToolOutcome(
                        movePath(env.context, args.optString("from", ""), args.optString("to", ""))
                    )
                    TOOL_COPY_PATH -> ToolOutcome(
                        copyPath(env.context, args.optString("from", ""), args.optString("to", ""))
                    )
                    TOOL_FILE_INFO -> ToolOutcome(fileInfo(env.context, args.optString("path", "")))
                    TOOL_SCREENSHOT -> screenshot(env.context)
                    TOOL_UI_TAP -> ToolOutcome(uiTap(args.optInt("x", -1), args.optInt("y", -1)))
                    TOOL_UI_SWIPE -> ToolOutcome(
                        uiSwipe(
                            args.optInt("x1", 0), args.optInt("y1", 0),
                            args.optInt("x2", 0), args.optInt("y2", 0),
                            args.optLong("duration_ms", 400)
                        )
                    )
                    TOOL_UI_TYPE -> ToolOutcome(uiType(args.optString("text", "")))
                    TOOL_UI_KEY -> ToolOutcome(uiKey(args.optString("key", "")))
                    TOOL_UI_CLICK_TEXT -> ToolOutcome(uiClickText(args.optString("text", "")))
                    TOOL_UI_SCREEN -> ToolOutcome(uiReadScreen())
                    TOOL_PRIV -> ToolOutcome(privStatus(env))
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
        val dm = context.resources.displayMetrics
        return buildString {
            append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("系统：Android ").append(Build.VERSION.RELEASE)
            append("（API ").append(Build.VERSION.SDK_INT).append("）\n")
            append("屏幕：").append(dm.widthPixels).append(" × ").append(dm.heightPixels)
            append(" 像素（密度 ").append(dm.densityDpi).append(" dpi）\n")
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
        // 配了 Key 走 Tavily（质量最好），没配就走内置必应 RSS —— 两条都能用，不再要求用户必须先申请 Key
        if (settings.tavilyApiKey.isNotBlank()) {
            val rich = searchTavily(settings.tavilyApiKey, query)
            if (!rich.startsWith("搜索失败")) return rich
        }
        return searchBing(query)
    }

    /** Tavily 通道：需要 Key，摘要质量高 */
    private fun searchTavily(key: String, query: String): String {
        val payload = obj(
            "api_key" to key,
            "query" to query,
            "max_results" to 5,
            "search_depth" to "basic"
        )
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return "搜索失败：HTTP ${resp.code} ${text.take(200)}"
                val results = JSONObject(text).optJSONArray("results") ?: return "搜索失败：没有结果字段"
                val sb = StringBuilder()
                for (i in 0 until minOf(results.length(), 5)) {
                    val item = results.optJSONObject(i) ?: continue
                    sb.append(i + 1).append(". ").append(item.optString("title", "")).append('\n')
                    sb.append(item.optString("url", "")).append('\n')
                    sb.append(item.optString("content", "").take(500)).append("\n\n")
                }
                sb.toString().ifBlank { "搜索失败：结果为空" }
            }
        } catch (e: Exception) {
            "搜索失败：${e.message}"
        }
    }

    /**
     * 内置搜索通道：必应 RSS。
     *
     * 选它的理由很实在 —— 返回结构化 XML，不用对着 HTML 抠 class 名，
     * 也不会因为页面改版就整体失效；不需要任何 Key，装上就能用。
     */
    private fun searchBing(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://cn.bing.com/search?q=$encoded&format=rss&count=8")
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        return try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return "搜索失败：HTTP ${resp.code}"
                val xml = resp.body?.string() ?: return "搜索失败：响应为空"
                val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml)
                    .take(6)
                    .map { m -> m.groupValues[1] }
                    .toList()
                if (items.isEmpty()) return "没有搜到「$query」的结果"
                buildString {
                    items.forEachIndexed { i, raw ->
                        val title = tag(raw, "title")
                        val link = tag(raw, "link")
                        val desc = tag(raw, "description")
                        if (title.isBlank() && link.isBlank()) return@forEachIndexed
                        append(i + 1).append(". ").append(title).append('\n')
                        if (link.isNotBlank()) append(link).append('\n')
                        if (desc.isNotBlank()) append(desc.take(400)).append('\n')
                        append('\n')
                    }
                    append("（内置必应搜索；需要更细的长文摘要可在设置里填 Tavily Key）")
                }
            }
        } catch (e: Exception) {
            "搜索失败：${e.message}"
        }
    }

    private fun tag(xml: String, name: String): String {
        val m = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml) ?: return ""
        return m.groupValues[1]
            .replace("<![CDATA[", "").replace("]]>", "")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&#39;", "'")
            .replace("&quot;", "\"")
            .trim()
    }

    /** 把当前特权通道摊开给模型看，免得它瞎试权限 */
    private fun privStatus(env: AgentEnv): String = buildString {
        append("【命令执行通道】\n")
        append(Priv.statusText(env.context))
        append("\n\n当前设置：").append(env.settings.shellMode().label)
        append("（实际生效：").append(Priv.resolve(env.context, env.settings.shellMode()).label).append("）")
        append("\n\n【屏幕自动化】")
        append(
            if (AccessibilityAgentService.isEnabled(env.context)) {
                "无障碍已开启，可用 ui_tap / ui_swipe / ui_type / ui_key / ui_click_text / ui_read_screen"
            } else {
                "无障碍未开启，屏幕类工具不可用（设置 → 工具与权限 → 去开启）"
            }
        )
        append("\n\n【运行环境】")
        append(env.settings.linuxEnv().label)
        append("（")
        append(
            when {
                !LinuxEnv.supported() -> "本机 ABI 不支持内置 Linux"
                LinuxEnv.installed(env.context) -> "Alpine ${LinuxEnv.VERSION}，proot 常驻可用"
                else -> "未安装，需在设置页点安装"
            }
        )
        append("）")
        if (env.settings.linuxEnv() == Environment.LINUX && !LinuxEnv.installed(env.context)) {
            append("\n注意：已选 Linux 但环境没装好，命令会直接报错，先去装。")
        } else if (env.settings.linuxEnv() == Environment.LINUX) {
            append("\n提示：Linux 环境里可用 apk add 装软件，路径是 Linux 语义（/root、/usr、/etc）。")
        }

        append("\n【搜索】")
        append(if (env.settings.tavilyApiKey.isNotBlank()) "Tavily（已配 Key）" else "内置必应（无需 Key）")
    }

    private fun runShell(context: Context, settings: AppSettings, command: String): String {
        if (command.isBlank()) return "没有提供命令"
        val home = ShellRunner.homeDir(context.filesDir)
        // 用常驻会话跑：cd / export 留得住，并且会带上用户在「终端环境配置」里写的变量与启动脚本
        val result = Term.tool(home, settings, context).run(command, timeoutMs = 60000, maxChars = 6000)
        val body = result.output.trim()
        val envTag = settings.linuxEnv().label
        return buildString {
            if (body.isNotEmpty()) append(body) else append("（无输出）")
            append("\n[退出码 ").append(result.exitCode)
            append(" | 环境 ").append(envTag).append(']')
            if (result.cwd.isNotBlank() && result.cwd != home.absolutePath) {
                append("\n[当前目录 ").append(result.cwd).append(']')
            }
        }
    }

    // ---------- 无障碍自动化 ----------

    private const val UI_GUIDE =
        "无障碍服务还没开启，无法自动操作。请到 设置 → 无障碍 → 知微智能体 打开，" +
            "或在应用内「工具与权限」页点「去开启」。"

    private fun uiService(): AccessibilityAgentService? = AccessibilityAgentService.get()

    private fun uiTap(x: Int, y: Int): String {
        if (x < 0 || y < 0) return "坐标无效：x、y 不能为负数"
        val svc = uiService() ?: return UI_GUIDE
        return if (svc.tap(x, y)) "已点击屏幕 ($x, $y)" else "点击失败：手势派发未成功"
    }

    private fun uiSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String {
        val svc = uiService() ?: return UI_GUIDE
        val d = durationMs.coerceIn(50L, 5000L)
        return if (svc.swipe(x1, y1, x2, y2, d)) "已从 ($x1, $y1) 滑到 ($x2, $y2)" else "滑动失败"
    }

    private fun uiType(text: String): String {
        if (text.isEmpty()) return "没有提供要输入的文字"
        val svc = uiService() ?: return UI_GUIDE
        return if (svc.type(text)) "已输入文字（${text.length} 字）" else "输入失败：没找到聚焦的输入框"
    }

    private fun uiKey(key: String): String {
        val svc = uiService() ?: return UI_GUIDE
        val ok = when (key.trim().lowercase()) {
            "back", "返回" -> svc.back()
            "home", "主页" -> svc.home()
            "recents", "最近任务" -> svc.recents()
            else -> return "不认识的键：$key（可用 back / home / recents）"
        }
        return if (ok) "已按下 $key" else "按键失败"
    }

    private fun uiClickText(text: String): String {
        if (text.isEmpty()) return "没有提供要点击的文字"
        val svc = uiService() ?: return UI_GUIDE
        return if (svc.clickByText(text)) "已点击「$text」" else "没找到可点击的「$text」"
    }

    private fun uiReadScreen(): String {
        val svc = uiService() ?: return UI_GUIDE
        return try {
            svc.dumpScreen()
        } catch (e: Exception) {
            "读取屏幕失败：${e.message}"
        }
    }

    // ---------- 工作区文件 ----------

    /** 模型给的路径：相对路径按工作区解析，绝对路径原样 */
    private fun resolveInWorkspace(context: Context, path: String): File {
        val home = ShellRunner.homeDir(context.filesDir)
        val p = path.trim().ifBlank { "." }
        val f = File(p)
        return if (f.isAbsolute) f else File(home, p)
    }

    private fun listDir(context: Context, path: String): String {
        val dir = resolveInWorkspace(context, path)
        if (!dir.exists()) return "目录不存在：${dir.absolutePath}"
        if (!dir.isDirectory) return "这不是目录：${dir.absolutePath}（用 read_file 读文件）"
        val home = ShellRunner.homeDir(context.filesDir).absolutePath
        val items = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        if (items.isEmpty()) return "目录是空的：${dir.absolutePath}"
        return buildString {
            append("目录 ").append(dir.absolutePath.replace(home, "工作区"))
                .append("，共 ").append(items.size).append(" 项：\n")
            items.take(200).forEach { f ->
                append(if (f.isDirectory) "[目录] " else "[文件] ").append(f.name)
                if (!f.isDirectory) append("  ").append(humanSize(f.length()))
                append('\n')
            }
            if (items.size > 200) append("…（还有 ").append(items.size - 200).append(" 项未列出）")
        }.trim()
    }

    private fun readFile(context: Context, path: String): String {
        if (path.isBlank()) return "没有提供文件路径"
        val f = resolveInWorkspace(context, path)
        if (!f.exists()) return "文件不存在：${f.absolutePath}"
        if (f.isDirectory) return "这是目录：${f.absolutePath}（用 list_dir 列目录）"
        if (!f.canRead()) return "没有权限读取：${f.absolutePath}"
        if (ImageSupport.isImage(f.name)) {
            return "这是图片：${f.name}（${humanSize(f.length())}）。图片没法按文本读；" +
                "想让模型直接看图，请让用户把这张图作为附件发到对话里，模型就能以多模态方式直接看到。"
        }
        return try {
            val sb = StringBuilder()
            f.bufferedReader().use { r ->
                val buf = CharArray(4096)
                while (sb.length < 12000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
            }
            val text = sb.toString()
            when {
                text.contains('\u0000') -> "这是二进制文件（${humanSize(f.length())}），没法当文本读。"
                f.length() > 12000L -> text.take(12000) + "\n…（内容较长已截断，共 ${f.length()} 字节）"
                else -> text
            }
        } catch (e: Exception) {
            "读取失败：${e.message}"
        }
    }

    private fun writeFile(context: Context, path: String, content: String): String {
        if (path.isBlank()) return "没有提供文件路径"
        val f = resolveInWorkspace(context, path)
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content)
            "已写入 ${f.absolutePath}（${content.length} 字）"
        } catch (e: Exception) {
            "写入失败：${e.message}"
        }
    }

    /** 工作区绝对路径；越界（跑到工作区外）时直接拦住，避免模型乱删应用私有数据 */
    private fun guardInWorkspace(context: Context, f: File): String? {
        val home = ShellRunner.homeDir(context.filesDir).canonicalFile
        val target = f.canonicalFile
        val inHome = target.path == home.path || target.path.startsWith(home.path + File.separator)
        if (!inHome) return "只能操作工作区内的路径（当前工作区：${home.absolutePath}）"
        // 工作区根本身不允许删
        if (target.path == home.path) return "不能删除或移动工作区根目录"
        return null
    }

    private fun appendFile(context: Context, path: String, content: String): String {
        if (path.isBlank()) return "没有提供文件路径"
        val f = resolveInWorkspace(context, path)
        return try {
            f.parentFile?.mkdirs()
            f.appendText(content)
            "已追加到 ${f.absolutePath}（+${content.length} 字，现共 ${f.length()} 字节）"
        } catch (e: Exception) {
            "追加失败：${e.message}"
        }
    }

    private fun makeDir(context: Context, path: String): String {
        if (path.isBlank()) return "没有提供目录路径"
        val f = resolveInWorkspace(context, path)
        if (f.isDirectory) return "目录已存在：${f.absolutePath}"
        if (f.exists()) return "这个路径上已经有个文件了，不能当目录：${f.absolutePath}"
        return try {
            if (f.mkdirs()) {
                "已创建目录 ${f.absolutePath}"
            } else {
                // mkdirs 返回 false 但也许别处已建好，复查一次再下定论
                if (f.isDirectory) "目录已存在：${f.absolutePath}"
                else "建目录失败：${f.absolutePath}（父目录不可写？）"
            }
        } catch (e: Exception) {
            "建目录失败：${e.message}"
        }
    }

    private fun deletePath(context: Context, path: String, recursive: Boolean): String {
        if (path.isBlank()) return "没有提供路径"
        val f = resolveInWorkspace(context, path)
        guardInWorkspace(context, f)?.let { return it }
        if (!f.exists()) return "路径不存在，无需删除：${f.absolutePath}"
        val wasDir = f.isDirectory
        val count = if (wasDir) (f.listFiles()?.size ?: 0) else 0
        try {
            if (wasDir && recursive) f.deleteRecursively() else f.delete()
        } catch (e: Exception) {
            return "删除失败：${e.message}"
        }
        // 删完复查，不靠返回值猜
        if (f.exists()) {
            return if (wasDir && !recursive && count > 0)
                "目录非空，未删除：${f.absolutePath}（想连内容一起删就把 recursive 设为 true）"
            else "删除失败：${f.absolutePath}（可能被占用或权限不足）"
        }
        return if (wasDir) "已删除目录 ${f.absolutePath}（含 $count 项）" else "已删除文件 ${f.absolutePath}"
    }

    private fun movePath(context: Context, from: String, to: String): String {
        if (from.isBlank() || to.isBlank()) return "需要同时提供 from 和 to"
        val src = resolveInWorkspace(context, from)
        val dst = resolveInWorkspace(context, to)
        guardInWorkspace(context, src)?.let { return "源路径：$it" }
        guardInWorkspace(context, dst)?.let { return "目标路径：$it" }
        if (!src.exists()) return "源路径不存在：${src.absolutePath}"
        if (dst.exists()) return "目标已存在，不覆盖：${dst.absolutePath}"
        return try {
            dst.parentFile?.mkdirs()
            if (src.renameTo(dst)) "已移动 ${src.absolutePath} → ${dst.absolutePath}"
            else "移动失败：${src.absolutePath} → ${dst.absolutePath}"
        } catch (e: Exception) {
            "移动失败：${e.message}"
        }
    }

    private fun copyPath(context: Context, from: String, to: String): String {
        if (from.isBlank() || to.isBlank()) return "需要同时提供 from 和 to"
        val src = resolveInWorkspace(context, from)
        val dst = resolveInWorkspace(context, to)
        guardInWorkspace(context, dst)?.let { return "目标路径：$it" }
        if (!src.exists()) return "源路径不存在：${src.absolutePath}"
        if (dst.exists()) return "目标已存在，不覆盖：${dst.absolutePath}"
        return try {
            val counter = longArrayOf(0L)
            fun walk(a: File, b: File) {
                if (a.isDirectory) {
                    b.mkdirs()
                    a.listFiles()?.forEach { walk(it, File(b, it.name)) }
                } else {
                    b.parentFile?.mkdirs()
                    a.inputStream().use { i -> b.outputStream().use { o -> i.copyTo(o) } }
                    counter[0]++
                }
            }
            walk(src, dst)
            "已复制 ${src.absolutePath} → ${dst.absolutePath}（${counter[0]} 个文件）"
        } catch (e: Exception) {
            "复制失败：${e.message}"
        }
    }

    private fun fileInfo(context: Context, path: String): String {
        val f = resolveInWorkspace(context, path)
        if (!f.exists()) return "不存在：${f.absolutePath}"
        val kind = if (f.isDirectory) "目录" else "文件"
        return buildString {
            append(f.absolutePath).append('
')
            append("类型：").append(kind).append('
')
            if (!f.isDirectory) append("大小：").append(humanSize(f.length())).append("（").append(f.length()).append(" 字节）
")
            else append("内含：").append(f.listFiles()?.size ?: 0).append(" 项
")
            append("可读：").append(if (f.canRead()) "是" else "否")
            append("　可写：").append(if (f.canWrite()) "是" else "否").append('
')
            append("最后修改：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(f.lastModified())))
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // ---------- 截屏 ----------

    private fun screenshot(context: Context): ToolOutcome {
        if (!ScreenCapture.isGranted()) {
            ScreenCapture.requestPermission(context)
            return ToolOutcome(
                "已弹出系统截屏授权窗口，请让用户在弹窗里点一下「立即开始」（只需要一次）。" +
                    "授权好之后，再调一次 screenshot 就能拿到截图。"
            )
        }
        val since = System.currentTimeMillis()
        ScreenCapture.startCapture(context)
        val path = ScreenCapture.awaitShot(since, 8000)
            ?: return ToolOutcome("截屏超时或失败。可能是授权被系统回收，再试一次即可。")
        return ToolOutcome(text = "已截屏：$path（图已附在下一轮对话里，直接看图作答）", images = listOf(path))
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