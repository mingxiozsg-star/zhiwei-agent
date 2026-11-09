package com.zhiwei.agent.data

import android.content.Context

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

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
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 0,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val streamOutput: Boolean = true,
    val enableTools: Boolean = true,
    val enableShellTool: Boolean = false,
    val tavilyApiKey: String = "",
    val themeMode: String = THEME_SYSTEM,
    val toolMaxRounds: Int = 4
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
}

class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("zhiwei_agent_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        baseUrl = sp.getString("baseUrl", null) ?: AppSettings().baseUrl,
        apiKey = sp.getString("apiKey", "") ?: "",
        model = sp.getString("model", null) ?: AppSettings().model,
        temperature = sp.getFloat("temperature", 0.7f),
        maxTokens = sp.getInt("maxTokens", 0),
        systemPrompt = sp.getString("systemPrompt", null) ?: DEFAULT_SYSTEM_PROMPT,
        streamOutput = sp.getBoolean("streamOutput", true),
        enableTools = sp.getBoolean("enableTools", true),
        enableShellTool = sp.getBoolean("enableShellTool", false),
        tavilyApiKey = sp.getString("tavilyApiKey", "") ?: "",
        themeMode = sp.getString("themeMode", THEME_SYSTEM) ?: THEME_SYSTEM,
        toolMaxRounds = sp.getInt("toolMaxRounds", 4)
    )

    fun save(s: AppSettings) {
        sp.edit()
            .putString("baseUrl", s.baseUrl)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putFloat("temperature", s.temperature)
            .putInt("maxTokens", s.maxTokens)
            .putString("systemPrompt", s.systemPrompt)
            .putBoolean("streamOutput", s.streamOutput)
            .putBoolean("enableTools", s.enableTools)
            .putBoolean("enableShellTool", s.enableShellTool)
            .putString("tavilyApiKey", s.tavilyApiKey)
            .putString("themeMode", s.themeMode)
            .putInt("toolMaxRounds", s.toolMaxRounds)
            .apply()
    }
}
