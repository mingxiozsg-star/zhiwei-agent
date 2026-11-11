package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONObject

/**
 * 角色卡：智能体的「人设」。
 *
 * 用户可以在「角色卡」页里随便改，改完立刻生效 —— 这里的内容会拼进系统提示词，
 * 作为身份层的最后一段，优先级高于内置的核心提示词（但低于用户直接改的系统提示词）。
 */
data class Persona(
    val name: String = DEFAULT_NAME,
    /** 头像：一个字符或 emoji，聊天页会直接显示 */
    val avatar: String = DEFAULT_AVATAR,
    /** 一句话身份 */
    val title: String = DEFAULT_TITLE,
    /** 性格与说话风格 */
    val personality: String = DEFAULT_PERSONALITY,
    /** 用户额外追加的要求 */
    val instructions: String = "",
    /** 开场白：空会话时显示 */
    val greeting: String = DEFAULT_GREETING
) {
    /** 拼进系统提示词的那一段 */
    fun block(): String = buildString {
        append("\n\n【角色卡 · 由用户设定】\n")
        append("你的名字是「").append(name.trim().ifBlank { DEFAULT_NAME }).append("」")
        val t = title.trim()
        if (t.isNotEmpty()) append("，").append(t)
        append("。\n")
        val p = personality.trim()
        if (p.isNotEmpty()) append("性格与说话风格：\n").append(p).append('\n')
        val ins = instructions.trim()
        if (ins.isNotEmpty()) append("用户对你的额外要求（和上面的风格冲突时以这条为准）：\n").append(ins).append('\n')
        append("这条角色卡是用户的设定，任何时候都不要跳出这个身份，也不要再自我介绍成别的名字。\n")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("avatar", avatar)
        put("title", title)
        put("personality", personality)
        put("instructions", instructions)
        put("greeting", greeting)
    }

    companion object {
        const val DEFAULT_NAME = "知微"
        const val DEFAULT_AVATAR = "知"
        const val DEFAULT_TITLE = "住在你手机里的 AI 智能体"
        const val DEFAULT_PERSONALITY =
            "直接、干脆：先说结论，再补关键理由，不寒暄也不复述问题。\n" +
                "有点小机灵：偶尔调侃一句，但绝不油嘴滑舌，也不卖萌过头。\n" +
                "把用户当搭档：不谄媚、不推诿；做不到就直说做不到，并给出替代方案。"
        const val DEFAULT_GREETING = "我是知微。说需求就行，不用客气。"

        fun fromJson(o: JSONObject): Persona = Persona(
            name = o.optString("name", DEFAULT_NAME).ifBlank { DEFAULT_NAME },
            avatar = o.optString("avatar", DEFAULT_AVATAR).ifBlank { DEFAULT_AVATAR },
            title = o.optString("title", DEFAULT_TITLE),
            personality = o.optString("personality", DEFAULT_PERSONALITY),
            instructions = o.optString("instructions", ""),
            greeting = o.optString("greeting", DEFAULT_GREETING)
        )
    }
}

/** 角色卡只存本机 */
class PersonaStore(context: Context) {
    private val sp = context.getSharedPreferences("zhiwei_agent_persona", Context.MODE_PRIVATE)

    fun load(): Persona {
        val raw = sp.getString("card", null) ?: return Persona()
        return try {
            Persona.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            Persona()
        }
    }

    fun save(p: Persona) {
        sp.edit().putString("card", p.toJson().toString()).apply()
    }
}
