package com.zhiwei.agent.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val ROLE_SYSTEM = "system"
const val ROLE_USER = "user"
const val ROLE_ASSISTANT = "assistant"
const val ROLE_TOOL = "tool"

const val ACT_RUNNING = "running"
const val ACT_DONE = "done"
const val ACT_ERROR = "error"

/** 一次工具调用（OpenAI function calling 格式） */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("arguments", arguments.ifBlank { "{}" })
        })
    }

    companion object {
        fun fromJson(o: JSONObject): ToolCall {
            val fn = o.optJSONObject("function") ?: JSONObject()
            return ToolCall(
                id = o.optString("id", ""),
                name = fn.optString("name", ""),
                arguments = fn.optString("arguments", "")
            )
        }
    }
}

/**
 * 一次工具调用的完整记录：调了什么、参数是什么、返回了什么。
 * 它是助手消息的一部分，所以界面上一条回复只有一条消息。
 */
data class ToolActivity(
    val id: String = UUID.randomUUID().toString(),
    val callId: String = "",
    val name: String = "",
    val label: String = "",
    val arguments: String = "",
    val result: String = "",
    val status: String = ACT_RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("callId", callId)
        put("name", name)
        put("label", label)
        put("arguments", arguments)
        put("result", result)
        put("status", status)
        put("startedAt", startedAt)
        put("finishedAt", finishedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ToolActivity = ToolActivity(
            id = o.optString("id", UUID.randomUUID().toString()),
            callId = o.optString("callId", ""),
            name = o.optString("name", ""),
            label = o.optString("label", ""),
            arguments = o.optString("arguments", ""),
            result = o.optString("result", ""),
            status = o.optString("status", ACT_DONE),
            startedAt = o.optLong("startedAt", System.currentTimeMillis()),
            finishedAt = o.optLong("finishedAt", 0L)
        )
    }
}

/** 一条聊天消息 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String = ROLE_USER,
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val activities: List<ToolActivity> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val streaming: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        put("timestamp", timestamp)
        if (toolCalls.isNotEmpty()) {
            put("toolCalls", JSONArray().apply { toolCalls.forEach { put(it.toJson()) } })
        }
        if (activities.isNotEmpty()) {
            put("activities", JSONArray().apply { activities.forEach { put(it.toJson()) } })
        }
        if (toolCallId != null) put("toolCallId", toolCallId)
        if (toolName != null) put("toolName", toolName)
        if (isError) put("isError", true)
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMessage {
            val tcs = mutableListOf<ToolCall>()
            o.optJSONArray("toolCalls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    tcs.add(ToolCall.fromJson(arr.getJSONObject(i)))
                }
            }
            val acts = mutableListOf<ToolActivity>()
            o.optJSONArray("activities")?.let { arr ->
                for (i in 0 until arr.length()) {
                    acts.add(ToolActivity.fromJson(arr.getJSONObject(i)))
                }
            }
            return ChatMessage(
                id = o.optString("id", UUID.randomUUID().toString()),
                role = o.optString("role", ROLE_USER),
                content = o.optString("content", ""),
                toolCalls = tcs,
                activities = acts,
                toolCallId = if (o.isNull("toolCallId")) null else o.optString("toolCallId", "").ifEmpty { null },
                toolName = if (o.isNull("toolName")) null else o.optString("toolName", "").ifEmpty { null },
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                isError = o.optBoolean("isError", false)
            )
        }
    }
}

/** 一个会话 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Conversation {
            val msgs = mutableListOf<ChatMessage>()
            o.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    msgs.add(ChatMessage.fromJson(arr.getJSONObject(i)))
                }
            }
            return Conversation(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "新对话"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                messages = msgs
            )
        }
    }
}