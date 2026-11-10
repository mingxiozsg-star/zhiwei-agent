package com.zhiwei.agent.data

import android.content.Context

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** 工具调用在消息里的折叠方式 */
const val COLLAPSE_NONE = "none"
const val COLLAPSE_SINGLE = "single"
const val COLLAPSE_MULTI = "multi"

const val DEFAULT_SYSTEM_PROMPT =
    "你是知微，一个高效、精准的手机端智能体。\n" +
        "规则：\n" +
        "1. 先给结论，再补关键理由，不说客套话和填充语。\n" +
        "2. 需要时间、计算、设备信息、网页内容时，直接调用工具，不要凭记忆猜。\n" +
        "3. 不确定就直说不确定，不编造事实。\n" +
        "4. 默认用中文回答，代码用代码块。"

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
    val tavilyApiKey: String = "",
    val toolMaxRounds: Int = 4,
    /** 每次请求最多带上的历史消息条数 */
    val contextLimit: Int = 24,

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
        systemPrompt = sp.getString("systemPrompt", null) ?: DEFAULT_SYSTEM_PROMPT,
        streamOutput = sp.getBoolean("streamOutput", true),
        enableTools = sp.getBoolean("enableTools", true),
        enableShellTool = sp.getBoolean("enableShellTool", false),
        tavilyApiKey = sp.getString("tavilyApiKey", "") ?: "",
        toolMaxRounds = sp.getInt("toolMaxRounds", 4),
        contextLimit = sp.getInt("contextLimit", 24),
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
            .putBoolean("streamOutput", s.streamOutput)
            .putBoolean("enableTools", s.enableTools)
            .putBoolean("enableShellTool", s.enableShellTool)
            .putString("tavilyApiKey", s.tavilyApiKey)
            .putInt("toolMaxRounds", s.toolMaxRounds)
            .putInt("contextLimit", s.contextLimit)
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
