package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一条提示词模板（快捷短语），聊天页点一下就填进输入框 */
data class PromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("text", text)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): PromptTemplate = PromptTemplate(
            id = o.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title", ""),
            text = o.optString("text", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

/**
 * 提示词库：只存本机，随改随存。
 * 第一次打开时装几条常用的，用户删空之后不再自动补回来。
 */
class PromptStore(context: Context) {
    private val sp = context.getSharedPreferences("zhiwei_agent_prompts", Context.MODE_PRIVATE)

    fun load(): List<PromptTemplate> {
        val raw = sp.getString("items", null)
        if (raw == null) {
            val seeded = defaults()
            save(seeded)
            return seeded
        }
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<PromptTemplate>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(PromptTemplate.fromJson(it)) }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<PromptTemplate>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        sp.edit().putString("items", arr.toString()).apply()
    }

    private fun defaults(): List<PromptTemplate> = listOf(
        PromptTemplate(title = "总结要点", text = "把下面的内容总结成 5 条要点，每条一句话：\n"),
        PromptTemplate(title = "翻译成中文", text = "把下面的内容翻译成中文，保持原有语气：\n"),
        PromptTemplate(title = "润色", text = "润色下面这段文字：去掉口语和重复，保持原意：\n"),
        PromptTemplate(title = "写代码", text = "用 Kotlin 实现下面的需求，给完整可运行的代码并加注释：\n"),
        PromptTemplate(title = "列计划", text = "把下面这件事拆成可执行的步骤清单，每步写明产出与验收标准：\n")
    )
}
