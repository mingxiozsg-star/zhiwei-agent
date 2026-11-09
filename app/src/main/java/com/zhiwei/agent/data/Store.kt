package com.zhiwei.agent.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 会话本地存储：filesDir/conversations.json
 * 不依赖数据库，读写都在调用线程完成（调用方保证在 IO 线程）。
 */
class ConversationStore(context: Context) {

    private val file = File(context.filesDir, "conversations.json")

    fun load(): List<Conversation> = synchronized(this) {
        if (!file.exists()) return@synchronized emptyList()
        try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                list.add(Conversation.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Conversation>) {
        synchronized(this) {
            try {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, "conversations.json.tmp")
                tmp.writeText(arr.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (_: Exception) {
                // 存储失败不阻塞对话
            }
        }
    }

    fun exportMarkdown(conversation: Conversation): String {
        val sb = StringBuilder()
        sb.append("# ").append(conversation.title).append("\n\n")
        conversation.messages.forEach { m ->
            when (m.role) {
                ROLE_USER -> sb.append("**我：** ").append(m.content).append("\n\n")
                ROLE_ASSISTANT -> {
                    if (m.content.isNotBlank()) {
                        sb.append("**知微：** ").append(m.content).append("\n\n")
                    }
                    m.toolCalls.forEach { tc ->
                        sb.append("> 调用工具 `").append(tc.name).append("`：").append(tc.arguments).append("\n\n")
                    }
                }
                ROLE_TOOL -> sb.append("> 工具 `").append(m.toolName ?: "?").append("` 返回：")
                    .append(m.content).append("\n\n")
            }
        }
        return sb.toString()
    }
}
