package com.zhiwei.agent.net

import com.zhiwei.agent.agent.ToolSpec
import com.zhiwei.agent.data.AppSettings
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_TOOL
import com.zhiwei.agent.data.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 流式对话过程中产生的事件 */
sealed class ChatEvent {
    data class Delta(val text: String) : ChatEvent()
    data class ToolCalls(val calls: List<ToolCall>) : ChatEvent()
    data class Usage(val prompt: Int, val completion: Int) : ChatEvent()
    data class Failure(val message: String) : ChatEvent()
    object Completed : ChatEvent()
}

/** 一次非流式请求的结果 */
data class ChatResult(
    val content: String,
    val toolCalls: List<ToolCall>,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

/**
 * OpenAI 兼容协议的客户端。
 * 支持 /v1/chat/completions 的 SSE 增量返回、function calling 与多 Key 轮换。
 */
class ChatApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var activeCall: Call? = null

    /** 密钥池轮换用的游标 */
    private val rotation = AtomicInteger(0)

    fun cancel() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {
        }
        activeCall = null
    }

    /** 取一个要用的 Key：开启轮换时按顺序取，否则固定用第一个 */
    fun pickKey(s: AppSettings): String {
        val keys = s.usableKeys()
        if (keys.isEmpty()) return ""
        if (!s.keyRotation || keys.size == 1) return keys.first()
        val i = rotation.getAndIncrement()
        return keys[Math.floorMod(i, keys.size)]
    }

    /** 非流式备用通道，用于记忆抽取、总结等一次性请求 */
    suspend fun chatOnce(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>
    ): Result<ChatResult> = withContext(Dispatchers.IO) {
        try {
            val body = buildBody(settings, history, tools, stream = false)
            val request = buildRequest(settings, body, pickKey(settings))
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException(describeHttpError(resp.code, text)))
                }
                val json = JSONObject(text)
                val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                val content = if (msg == null || msg.isNull("content")) "" else msg.optString("content", "")
                val calls = parseToolCalls(msg?.optJSONArray("tool_calls"))
                val usage = json.optJSONObject("usage")
                Result.success(
                    ChatResult(
                        content = content,
                        toolCalls = calls,
                        promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
                        completionTokens = usage?.optInt("completion_tokens", 0) ?: 0
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeCall = null
        }
    }

    /**
     * 流式对话。onEvent 会在 IO 线程被顺序调用，调用方直接更新 StateFlow 即可。
     */
    suspend fun streamChat(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: (ChatEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val body = buildBody(settings, history, tools, stream = true)
        val request = buildRequest(settings, body, pickKey(settings))
        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errText = resp.body?.string() ?: ""
                    onEvent(ChatEvent.Failure(describeHttpError(resp.code, errText)))
                    return@use
                }
                val source = resp.body?.source()
                if (source == null) {
                    onEvent(ChatEvent.Failure("响应为空"))
                    return@use
                }
                val toolAcc = LinkedHashMap<Int, ToolCallBuilder>()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload == "[DONE]") break
                    val json = try {
                        JSONObject(payload)
                    } catch (_: Exception) {
                        continue
                    }
                    // 有的网关会把用量塞在最后一个 chunk 里
                    json.optJSONObject("usage")?.let { u ->
                        val p = u.optInt("prompt_tokens", 0)
                        val c = u.optInt("completion_tokens", 0)
                        if (p > 0 || c > 0) onEvent(ChatEvent.Usage(p, c))
                    }
                    val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?: continue
                    if (!delta.isNull("content")) {
                        val piece = delta.optString("content", "")
                        if (piece.isNotEmpty()) onEvent(ChatEvent.Delta(piece))
                    }
                    val tcs = delta.optJSONArray("tool_calls")
                    if (tcs != null) {
                        for (i in 0 until tcs.length()) {
                            val t = tcs.optJSONObject(i) ?: continue
                            val idx = t.optInt("index", i)
                            val b = toolAcc.getOrPut(idx) { ToolCallBuilder() }
                            if (!t.isNull("id")) {
                                val id = t.optString("id", "")
                                if (id.isNotEmpty()) b.id = id
                            }
                            val fn = t.optJSONObject("function")
                            if (fn != null) {
                                if (!fn.isNull("name")) {
                                    val n = fn.optString("name", "")
                                    if (n.isNotEmpty()) b.name = n
                                }
                                if (!fn.isNull("arguments")) {
                                    b.args.append(fn.optString("arguments", ""))
                                }
                            }
                        }
                    }
                }
                if (toolAcc.isNotEmpty()) {
                    val calls = toolAcc.values.filter { it.name.isNotBlank() }.map {
                        ToolCall(
                            id = it.id.ifBlank { "call_" + System.currentTimeMillis() },
                            name = it.name,
                            arguments = it.args.toString().ifBlank { "{}" }
                        )
                    }
                    onEvent(ChatEvent.ToolCalls(calls))
                }
                onEvent(ChatEvent.Completed)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (call.isCanceled()) {
                onEvent(ChatEvent.Completed)
            } else {
                onEvent(ChatEvent.Failure(e.message ?: e.javaClass.simpleName))
            }
        } finally {
            activeCall = null
        }
    }

    /** 拉取服务端模型列表 */
    suspend fun listModels(settings: AppSettings): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val key = pickKey(settings)
                val request = Request.Builder()
                    .url(settings.modelsEndpoint())
                    .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IOException(describeHttpError(resp.code, text)))
                    }
                    val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
                    val out = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val id = arr.optJSONObject(i)?.optString("id", "") ?: ""
                        if (id.isNotBlank()) out.add(id)
                    }
                    out.sort()
                    Result.success(out)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 用指定 Key 发一个最小请求，验证它是否可用 */
    suspend fun testKey(settings: AppSettings, key: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(settings.modelsEndpoint())
                    .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Result.success("可用")
                    } else {
                        val text = resp.body?.string() ?: ""
                        val hint = when (resp.code) {
                            401 -> "Key 无效"
                            403 -> "无权限"
                            429 -> "额度用尽或限流"
                            else -> "HTTP ${resp.code}"
                        }
                        val extra = text.trim().take(120)
                        Result.failure(IOException(if (extra.isEmpty()) hint else "$hint：$extra"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    private fun buildRequest(settings: AppSettings, body: String, key: String): Request {
        val builder = Request.Builder()
            .url(settings.endpoint())
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", if (settings.streamOutput) "text/event-stream" else "application/json")
        if (key.isNotBlank()) {
            builder.header("Authorization", "Bearer $key")
        }
        if (settings.baseUrl.contains("openrouter.ai")) {
            builder.header("HTTP-Referer", "https://github.com/mingxiozsg-star/zhiwei-agent")
            builder.header("X-Title", "Zhiwei Agent")
        }
        return builder.build()
    }

    private fun buildBody(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean
    ): String {
        val root = JSONObject()
        root.put("model", settings.model)
        root.put("messages", buildMessages(settings, history))
        if (stream) root.put("stream", true)
        root.put("temperature", settings.temperature.toDouble())
        if (settings.topP < 1.0f) root.put("top_p", settings.topP.toDouble())
        if (settings.frequencyPenalty != 0f) root.put("frequency_penalty", settings.frequencyPenalty.toDouble())
        if (settings.presencePenalty != 0f) root.put("presence_penalty", settings.presencePenalty.toDouble())
        if (settings.maxTokens > 0) root.put("max_tokens", settings.maxTokens)
        if (tools.isNotEmpty() && settings.nativeToolCall) {
            root.put("tools", JSONArray().apply { tools.forEach { put(it.toJson()) } })
            root.put("tool_choice", "auto")
        }
        return root.toString()
    }

    private fun buildMessages(settings: AppSettings, history: List<ChatMessage>): JSONArray {
        var keep = history
        val limit = settings.contextLimit
        if (limit > 0 && keep.size > limit) {
            keep = keep.takeLast(limit)
            // 协议要求 tool 消息前面必须有对应的 assistant 调用，别让历史从孤立的 tool 开始
            while (keep.isNotEmpty() && keep.first().role == ROLE_TOOL) keep = keep.drop(1)
        }
        val arr = JSONArray()
        if (settings.systemPrompt.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", settings.systemPrompt))
        }
        keep.forEach { m ->
            when (m.role) {
                ROLE_ASSISTANT -> {
                    val acts = m.activities
                    val calls = if (m.toolCalls.isNotEmpty()) {
                        m.toolCalls
                    } else {
                        acts.map {
                            ToolCall(
                                id = it.callId.ifBlank { it.id },
                                name = it.name,
                                arguments = it.arguments.ifBlank { "{}" }
                            )
                        }
                    }
                    val o = JSONObject()
                    o.put("role", "assistant")
                    if (m.content.isBlank() && calls.isNotEmpty()) {
                        o.put("content", JSONObject.NULL)
                    } else {
                        o.put("content", m.content)
                    }
                    if (calls.isNotEmpty()) {
                        o.put(
                            "tool_calls",
                            JSONArray().apply { calls.forEach { put(it.toJson()) } }
                        )
                    }
                    arr.put(o)
                    // 助手消息里的工具结果要按协议补成独立的 tool 消息
                    acts.forEach { a ->
                        arr.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", a.callId.ifBlank { a.id })
                                .put("content", a.result.ifBlank { "（无结果）" })
                        )
                    }
                }

                ROLE_TOOL -> {
                    arr.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", m.toolCallId ?: "")
                            .put("content", m.content)
                    )
                }

                else -> arr.put(JSONObject().put("role", m.role).put("content", m.content))
            }
        }
        return arr
    }

    private fun parseToolCalls(arr: JSONArray?): List<ToolCall> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ToolCall>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(ToolCall.fromJson(o))
        }
        return out
    }

    private fun describeHttpError(code: Int, body: String): String {
        val snippet = body.trim().let { if (it.length > 400) it.substring(0, 400) + "…" else it }
        val hint = when (code) {
            401 -> "API Key 无效或未填写"
            403 -> "该 Key 无权限访问此模型"
            404 -> "接口地址不对，检查「API 地址」是否包含 /v1"
            429 -> "请求过于频繁或额度用尽"
            in 500..599 -> "服务端错误"
            else -> ""
        }
        return buildString {
            append("HTTP ").append(code)
            if (hint.isNotEmpty()) append("（").append(hint).append("）")
            if (snippet.isNotEmpty()) append("：").append(snippet)
        }
    }
}