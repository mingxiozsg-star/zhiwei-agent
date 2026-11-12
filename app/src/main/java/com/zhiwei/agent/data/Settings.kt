package com.zhiwei.agent.data

import android.content.Context

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** 工具调用在消息里的折叠方式 */
const val COLLAPSE_NONE = "none"
const val COLLAPSE_SINGLE = "single"
const val COLLAPSE_MULTI = "multi"

/** 系统提示词版本：升级时只在用户没改过的情况下换成新版 */
const val SYSTEM_PROMPT_VERSION = 4

/**
 * v4 核心提示词。
 *
 * v3 的毛病：明文要求「先列 2~5 步计划、每步给一句进度」，等于授权模型表演，
 * 于是用户问一句截图，它先讲一段计划。v4 直接反过来：先动手，话越少越好。
 * 分层仍然是「你是谁 / 怎么说话 / 怎么干活 / 收尾」四层，只是把每条都压缩了。
 */
const val DEFAULT_SYSTEM_PROMPT =
    "你是知微，跑在用户手机里的 AI 智能体。你能调用工具去查、去算、去写，" +
        "也能操作这台设备（音量、亮度、手电筒、震动、截图、开关应用、读工作区文件、在沙箱里跑命令）。\n" +
        "\n" +
        "【你是谁】\n" +
        "- 名字「知微」，取「见微知著」。你住在这台手机里，用户的记忆库、知识库、工作区文件都是你的长期上下文。\n" +
        "\n" +
        "【怎么说话】\n" +
        "1. 先给结论，再补必要信息，能一句说完就不写两句。不寒暄、不复述用户的话、不说「好问题」「让我看看」「作为一个 AI」。\n" +
        "2. 不要预告、不要汇报过程、不要为自己的操作写说明文。工具调完只给结果；用户没问的细节不要主动铺开。\n" +
        "3. 默认中文。代码放代码块并标语言；能列点就别写大段。\n" +
        "4. 禁止把思考过程、内心独白、计划清单当正文输出。\n" +
        "\n" +
        "【怎么干活】\n" +
        "1. 能自己查的绝不猜：时间、计算、设备状态、剪贴板、记忆与知识库、网页、工作区文件，先调工具拿真实结果。\n" +
        "2. 该调工具就直接调，一次可以调多个，不要先问「要不要我帮你…」。多步任务直接往下做完，中间不要停下等用户催。\n" +
        "3. 工具报错不代表任务结束：读懂错误、换参数或换方案重试；确实做不到，一句话说清卡在哪、需要用户做什么。\n" +
        "4. 不编造事实、链接、数据、文件名、API；不确定就直说不确定。\n" +
        "5. 删除、发送、支付、改系统设置这类不可逆或有副作用的操作，先一句话说清再执行。\n" +
        "\n" +
        "【收尾】\n" +
        "做完给一句状态：成了什么、还差什么。没别的事就收住，不要追问、不要客套。"

/** v3 的老提示词，只用于升级时判断用户有没有自己改过 */
private const val LEGACY_SYSTEM_PROMPT_V3 =
    "你是知微，一个跑在用户手机里的 AI 智能体。你不是只会聊天的助手：你能调用工具去查、去算、去写，" +
        "也能操作这台设备（音量、亮度、手电筒、震动、开关应用、在沙箱里跑终端命令）。\n" +
        "\n" +
        "【你是谁】\n" +
        "- 名字「知微」，取「见微知著」：从细节里看出要害。\n" +
        "- 你住在这台手机里。用户的记忆库、知识库、工作区文件，都是你的长期上下文，不是外人的资料。\n" +
        "\n" +
        "【怎么说话】\n" +
        "1. 先给结论，再补关键理由。不寒暄、不复述问题、不说「好问题」「作为一个 AI」这类填充语。\n" +
        "2. 默认中文。代码放代码块并标注语言；能用清单/表格就别写大段文字。\n" +
        "3. 语气像一个靠谱的搭档：可以有点幽默，但不油腻、不谄媚、不卖萌过头。\n" +
        "4. 短问题短答，别写小作文。长内容先给三行以内的摘要，再展开细节。\n" +
        "\n" +
        "【怎么干活】\n" +
        "1. 能查就查，别猜：时间、日期、计算、设备状态、剪贴板、用户的记忆与知识、网页内容 —— 先调工具拿到真实结果再回答。\n" +
        "2. 需要两步以上才能完成的任务，先用一行列出 2~5 步计划，再一步步执行；每步给一句进度，做完给结果，\n" +
        "   不要中途停下来等用户催。\n" +
        "3. 工具报错不等于任务结束：读懂错误，换参数或换方案再试；确实做不到，就说清卡在哪一步、需要用户做什么。\n" +
        "4. 不确定就直说不确定，并给出验证办法。绝不编造事实、链接、数据、文件名或 API。\n" +
        "5. 删除、发送、支付、修改系统设置这类不可逆或有副作用的操作，先说清楚再执行。\n" +
        "6. 用户给的信息不够、且猜错代价很大时，先问一句再动手；其余情况先做，不要为了确认而确认。\n" +
        "\n" +
        "【收尾】\n" +
        "每次回答的最后，让用户一眼知道现在是什么状态：已完成什么、还差什么、下一步该做什么。"

/** v1 的老提示词，只用于升级时判断用户有没有自己改过 */
private const val LEGACY_SYSTEM_PROMPT_V1 =
    "你是知微，一个高效、精准的手机端智能体。\n" +
        "规则：\n" +
        "1. 先给结论，再补关键理由，不说客套话和填充语。\n" +
        "2. 需要时间、计算、设备信息、网页内容时，直接调用工具，不要凭记忆猜。\n" +
        "3. 不确定就直说不确定，不编造事实。\n" +
        "4. 默认用中文回答，代码用代码块。"

/** v2 的老提示词，同样只用于升级判断 */
private const val LEGACY_SYSTEM_PROMPT_V2 =
    "你是知微，运行在手机上的 AI 智能体。你可以调用工具，也能操控这台设备（音量、亮度、手电筒、震动、应用、终端）。\n" +
        "工作方式：\n" +
        "1. 先给结论，再补关键理由；不寒暄、不复述问题、不说「作为一个 AI」。\n" +
        "2. 能自己查的就自己查：时间、计算、设备状态、记忆、知识库、网页内容，直接调工具，不要凭记忆猜。\n" +
        "3. 多步任务先用一行列出 2~5 步计划，再一步步做完；每步给一句进度，做完再收尾，不要中途停下等用户催。\n" +
        "4. 工具报错不等于任务结束：读懂错误，换个参数或换个办法再试；确实做不到，说清卡在哪一步、需要用户做什么。\n" +
        "5. 不编造事实、链接、数据和 API；不确定就直说不确定，并给出验证办法。\n" +
        "6. 默认中文回答；代码用代码块并标注语言；长内容先给摘要再给细节。\n" +
        "7. 删除、支付、发送给他人这类不可逆操作，先说明再执行。"

/**
 * 应用设置。任何一项改动都会立刻写入本地，不需要“保存”按钮。
 */
data class AppSettings(
    // ---- 接口 ----
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    /** 密钥池：一行一个 Key，开启轮换后按顺序使用；留空则用上面的单个 Key */
    val apiKeysRaw: String = "",
    val keyRotation: Boolean = true,

    // ---- 生成参数 ----
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 0,
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
    /** 是否在请求里下发 tools 参数；个别网关不支持原生 function calling，关掉后本回合不带工具 */
    val nativeToolCall: Boolean = true,

    // ---- 智能体 ----
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val streamOutput: Boolean = true,
    val enableTools: Boolean = true,
    val enableShellTool: Boolean = false,
    /** 设备操控类工具（音量、亮度、手电筒、震动、应用开关等）总开关 */
    val enableDeviceTools: Boolean = true,
    val tavilyApiKey: String = "",
    val toolMaxRounds: Int = 12,
    /** 服务商报错（超时、限流、5xx、断连）时的自动重试次数，0 = 不重试 */
    val retryCount: Int = 6,
    /** 重试用完仍失败时，自动补一轮「继续」接着做，不直接中断任务 */
    val autoResumeAfterFailure: Boolean = true,
    /** 每次请求最多带上的历史消息条数 */
    val contextLimit: Int = 24,
    /** 图片附件按多模态（image_url）直传给模型，需要模型支持视觉 */
    val sendImages: Boolean = true,
    /** 图片发出去前压缩到的长边像素；太小看不清，太大浪费额度 */
    val imageMaxSide: Int = 1568,
    /** 把模型的思考过程单独折叠展示，不混进正文 */
    val showReasoning: Boolean = true,

    // ---- 记忆与知识 ----
    val autoMemory: Boolean = true,
    val memoryTopK: Int = 4,
    val knowledgeTopK: Int = 3,
    val injectContext: Boolean = true,
    val extractModel: String = "",
    /** 自动记忆的额外规则，会拼进抽取提示词 */
    val memoryExtractRules: String = "",
    val enablePlugins: Boolean = true,

    // ---- 外观 ----
    val themeMode: String = THEME_SYSTEM,
    val fontScale: Float = 1.0f,
    /** 抽屉宽度占屏比 */
    val drawerRatio: Float = 0.42f,

    // ---- 显示与行为 ----
    val showTimestamp: Boolean = false,
    val collapseMode: String = COLLAPSE_MULTI,
    val keepScreenOn: Boolean = false,
    val notifyOnDone: Boolean = false,
    val hapticOnSend: Boolean = true
) {
    /** 补全成完整的 chat/completions 地址 */
    fun endpoint(): String {
        var base = baseUrl.trim()
        if (base.isEmpty()) base = "https://api.openai.com/v1"
        if (base.endsWith("/")) base = base.dropLast(1)
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") || base.endsWith("/v1beta") -> "$base/chat/completions"
            base.contains("/v1/") -> base
            else -> "$base/v1/chat/completions"
        }
    }

    /** 拉取模型列表用的地址 */
    fun modelsEndpoint(): String {
        var base = baseUrl.trim()
        if (base.isEmpty()) base = "https://api.openai.com/v1"
        if (base.endsWith("/")) base = base.dropLast(1)
        return when {
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
            base.endsWith("/v1") -> "$base/models"
            base.contains("/v1/") -> base.substringBefore("/v1/") + "/v1/models"
            else -> "$base/v1/models"
        }
    }

    /** 解析密钥池文本 */
    fun keyList(): List<String> = apiKeysRaw
        .split('\n', '\r', ',', ';', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()

    /** 实际可用的密钥序列：密钥池优先，否则退回单个 Key */
    fun usableKeys(): List<String> {
        val pool = keyList()
        if (pool.isNotEmpty()) return pool
        return if (apiKey.isNotBlank()) listOf(apiKey.trim()) else emptyList()
    }
}

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("zhiwei_agent_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        baseUrl = sp.getString("baseUrl", null) ?: AppSettings().baseUrl,
        apiKey = sp.getString("apiKey", "") ?: "",
        model = sp.getString("model", null) ?: AppSettings().model,
        apiKeysRaw = sp.getString("apiKeysRaw", "") ?: "",
        keyRotation = sp.getBoolean("keyRotation", true),
        temperature = sp.getFloat("temperature", 0.7f),
        topP = sp.getFloat("topP", 1.0f),
        maxTokens = sp.getInt("maxTokens", 0),
        frequencyPenalty = sp.getFloat("frequencyPenalty", 0f),
        presencePenalty = sp.getFloat("presencePenalty", 0f),
        nativeToolCall = sp.getBoolean("nativeToolCall", true),
        systemPrompt = loadSystemPrompt(),
        streamOutput = sp.getBoolean("streamOutput", true),
        enableTools = sp.getBoolean("enableTools", true),
        enableShellTool = sp.getBoolean("enableShellTool", false),
        enableDeviceTools = sp.getBoolean("enableDeviceTools", true),
        tavilyApiKey = sp.getString("tavilyApiKey", "") ?: "",
        toolMaxRounds = sp.getInt("toolMaxRounds", 12),
        retryCount = sp.getInt("retryCount", 6),
        autoResumeAfterFailure = sp.getBoolean("autoResumeAfterFailure", true),
        contextLimit = sp.getInt("contextLimit", 24),
        sendImages = sp.getBoolean("sendImages", true),
        imageMaxSide = sp.getInt("imageMaxSide", 1568),
        showReasoning = sp.getBoolean("showReasoning", true),
        autoMemory = sp.getBoolean("autoMemory", true),
        memoryTopK = sp.getInt("memoryTopK", 4),
        knowledgeTopK = sp.getInt("knowledgeTopK", 3),
        injectContext = sp.getBoolean("injectContext", true),
        extractModel = sp.getString("extractModel", "") ?: "",
        memoryExtractRules = sp.getString("memoryExtractRules", "") ?: "",
        enablePlugins = sp.getBoolean("enablePlugins", true),
        themeMode = sp.getString("themeMode", THEME_SYSTEM) ?: THEME_SYSTEM,
        fontScale = sp.getFloat("fontScale", 1.0f),
        drawerRatio = sp.getFloat("drawerRatio", 0.42f),
        showTimestamp = sp.getBoolean("showTimestamp", false),
        collapseMode = sp.getString("collapseMode", COLLAPSE_MULTI) ?: COLLAPSE_MULTI,
        keepScreenOn = sp.getBoolean("keepScreenOn", false),
        notifyOnDone = sp.getBoolean("notifyOnDone", false),
        hapticOnSend = sp.getBoolean("hapticOnSend", true)
    )

    /**
     * 系统提示词升级：只在用户没动过（还是老版本默认文案）时才换成新版，
     * 用户自己改过的一律原样保留，不覆盖。
     */
    private fun loadSystemPrompt(): String {
        val saved = sp.getString("systemPrompt", null)
        val version = sp.getInt("systemPromptVersion", 1)
        if (saved.isNullOrBlank()) return DEFAULT_SYSTEM_PROMPT
        if (version < SYSTEM_PROMPT_VERSION &&
            (saved == LEGACY_SYSTEM_PROMPT_V1 ||
                saved == LEGACY_SYSTEM_PROMPT_V2 ||
                saved == LEGACY_SYSTEM_PROMPT_V3 ||
                saved == DEFAULT_SYSTEM_PROMPT)
        ) {
            return DEFAULT_SYSTEM_PROMPT
        }
        return saved
    }

    fun save(s: AppSettings) {
        sp.edit()
            .putString("baseUrl", s.baseUrl)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("apiKeysRaw", s.apiKeysRaw)
            .putBoolean("keyRotation", s.keyRotation)
            .putFloat("temperature", s.temperature)
            .putFloat("topP", s.topP)
            .putInt("maxTokens", s.maxTokens)
            .putFloat("frequencyPenalty", s.frequencyPenalty)
            .putFloat("presencePenalty", s.presencePenalty)
            .putBoolean("nativeToolCall", s.nativeToolCall)
            .putString("systemPrompt", s.systemPrompt)
            .putInt("systemPromptVersion", SYSTEM_PROMPT_VERSION)
            .putBoolean("streamOutput", s.streamOutput)
            .putBoolean("enableTools", s.enableTools)
            .putBoolean("enableShellTool", s.enableShellTool)
            .putBoolean("enableDeviceTools", s.enableDeviceTools)
            .putString("tavilyApiKey", s.tavilyApiKey)
            .putInt("toolMaxRounds", s.toolMaxRounds)
            .putInt("retryCount", s.retryCount)
            .putBoolean("autoResumeAfterFailure", s.autoResumeAfterFailure)
            .putInt("contextLimit", s.contextLimit)
            .putBoolean("sendImages", s.sendImages)
            .putInt("imageMaxSide", s.imageMaxSide)
            .putBoolean("showReasoning", s.showReasoning)
            .putBoolean("autoMemory", s.autoMemory)
            .putInt("memoryTopK", s.memoryTopK)
            .putInt("knowledgeTopK", s.knowledgeTopK)
            .putBoolean("injectContext", s.injectContext)
            .putString("extractModel", s.extractModel)
            .putString("memoryExtractRules", s.memoryExtractRules)
            .putBoolean("enablePlugins", s.enablePlugins)
            .putString("themeMode", s.themeMode)
            .putFloat("fontScale", s.fontScale)
            .putFloat("drawerRatio", s.drawerRatio)
            .putBoolean("showTimestamp", s.showTimestamp)
            .putString("collapseMode", s.collapseMode)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("notifyOnDone", s.notifyOnDone)
            .putBoolean("hapticOnSend", s.hapticOnSend)
            .apply()
    }
}
