package com.zhiwei.agent.net

import com.zhiwei.agent.agent.ToolSpec
import com.zhiwei.agent.data.AppSettings
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_TOOL
import com.zhiwei.agent.data.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlin.math.min
import kotlin.random.Random

/** 流式对话过程中产生的事件 */
sealed class ChatEvent {
    data class Delta(val text: String) : ChatEvent()
    /** 模型思维链增量：单独一条通道，只用来折叠展示，绝不混进正文 */
    data class Reasoning(val text: String) : ChatEvent()
    data class ToolCalls(val calls: List<ToolCall>) : ChatEvent()
    data class Usage(val prompt: Int, val completion: Int) : ChatEvent()
    /** 第 attempt 次重试（一共 total 次），reason 是上一次失败的原因 */
    data class Retry(val attempt: Int, val total: Int, val reason: String) : ChatEvent()
    /** 重试前要求上层丢掉上一次尝试已经流出的半截内容 */
    object Restart : ChatEvent()
    /** 这一轮只回了思考，没有正文也没有工具调用（多为 max_tokens 被思考吃光） */
    object ReasoningOnly : ChatEvent()
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
 * 支持 /v1/chat/completions 的 SSE 增量返回、function calling、多 Key 轮换，
 * 以及「服务商报错就换 Key 重试」的容错逻辑（重试次数在设置里调）。
 */
class ChatApi {

    /** 一次请求最多带几张图：再多请求体就太大了 */
    private val maxImagesPerRequest = 4

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

    /**
     * 第 attempt 次尝试（从 0 开始）该用哪个 Key。
     * 池子里有多个 Key 时，重试一定会换 Key —— 限流或单 Key 失效时这才有意义。
     */
    private fun keyForAttempt(s: AppSettings, attempt: Int): String {
        val keys = s.usableKeys()
        if (keys.isEmpty()) return ""
        if (keys.size == 1) return keys.first()
        if (attempt == 0 && !s.keyRotation) return keys.first()
        return keys[Math.floorMod(rotation.getAndIncrement(), keys.size)]
    }

    /** 重试间隔：线性退避 + 抖动，最长 6 秒 */
    private fun backoffMs(attempt: Int): Long {
        val base = min(900L * attempt, 6000L)
        return base + Random.nextLong(0, 400)
    }

    /** 哪些错误值得重试 */
    private fun retryableCode(code: Int, keyPoolSize: Int): Boolean = when {
        code == 408 || code == 409 || code == 425 || code == 429 -> true
        code in 500..599 -> true
        // Key 失效 / 无权限：池子里还有别的 Key 就换一个接着试
        code == 401 || code == 403 -> keyPoolSize > 1
        else -> false
    }

    private data class AttemptError(val message: String, val retryable: Boolean)

    /**
     * 非流式备用通道，用于记忆抽取、总结等一次性请求。
     * 同样带重试，但次数收敛到 4 次，免得后台任务拖太久。
     */
    suspend fun chatOnce(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        imageLoader: ((String) -> String?)? = null
    ): Result<ChatResult> = withContext(Dispatchers.IO) {
        val retries = settings.retryCount.coerceIn(0, 4)
        var last: String? = null
        var attempt = 0
        while (true) {
            val r = chatOnceAttempt(settings, history, tools, keyForAttempt(settings, attempt), imageLoader)
            r.onSuccess { return@withContext Result.success(it) }
            val e = r.exceptionOrNull()
            last = e?.message ?: e?.javaClass?.simpleName
            if (attempt >= retries) break
            attempt++
            delay(backoffMs(attempt))
        }
        Result.failure(IOException(last ?: "请求失败"))
    }

    private suspend fun chatOnceAttempt(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        key: String,
        imageLoader: ((String) -> String?)? = null
    ): Result<ChatResult> = withContext(Dispatchers.IO) {
        try {
            val body = buildBody(settings, history, tools, stream = false, imageLoader = imageLoader)
            val request = buildRequest(settings, body, key)
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException(describeHttpError(resp.code, text)))
                }
                val json = JSONObject(text)
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                val msg = choice?.optJSONObject("message") ?: choice?.optJSONObject("delta")
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
     * 失败会按设置里的次数自动重试（换 Key + 退避），全部失败才发 Failure。
     */
    suspend fun streamChat(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        imageLoader: ((String) -> String?)? = null,
        onEvent: (ChatEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val retries = settings.retryCount.coerceIn(0, 10)
        var lastError: AttemptError? = null
        var attempt = 0
        while (true) {
            val err = streamOnce(settings, history, tools, keyForAttempt(settings, attempt), imageLoader, onEvent)
            if (err == null) return@withContext
            lastError = err
            if (attempt >= retries || !err.retryable) break
            attempt++
            onEvent(ChatEvent.Retry(attempt, retries, err.message))
            onEvent(ChatEvent.Restart)
            delay(backoffMs(attempt))
        }
        onEvent(ChatEvent.Failure(lastError.message))
    }

    /** 单次尝试；返回 null 表示成功，否则是失败详情 */
    private suspend fun streamOnce(
        settings: AppSettings,
        history: List<ChatMessage>,
        tools: List<ToolSpec>,
        key: String,
        imageLoader: ((String) -> String?)?,
        onEvent: (ChatEvent) -> Unit
    ): AttemptError? {
        val body = buildBody(settings, history, tools, stream = true, imageLoader = imageLoader)
        val request = buildRequest(settings, body, key)
        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string() ?: ""
                    return AttemptError(
                        describeHttpError(resp.code, raw),
                        retryableCode(resp.code, settings.usableKeys().size)
                    )
                }

                val source = resp.body?.source()
                    ?: return AttemptError("服务商返回了空响应", true)

                // 逐行读取：是 SSE 就边读边回调（真正做到流式），
                // 不认 stream 参数、直接回一整段 JSON 的网关也能兜住。
                val c = Chunk()
                val buffered = StringBuilder()
                var sse = false
                var sawAny = false
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    sawAny = true
                    if (!sse) {
                        val probe = line.trim()
                        if (probe.isEmpty()) {
                            buffered.append('\n')
                            continue
                        }
                        if (isSseLine(probe)) {
                            sse = true
                            buffered.setLength(0)
                            handleSseLine(line, c, onEvent)
                            continue
                        }
                        // 不是 SSE：先按「整段 JSON」攒着，读到底再解析
                        buffered.append(line).append('\n')
                        continue
                    }
                    handleSseLine(line, c, onEvent)
                }

                if (!sawAny) return AttemptError("服务商返回了空响应", true)
                val err = if (sse) {
                    finishChunk(c, onEvent)
                } else {
                    val raw = buffered.toString().trim()
                    if (raw.isEmpty()) AttemptError("服务商返回了空响应", true)
                    else handleWholeBody(raw, onEvent)
                }
                if (err != null) return err
                onEvent(ChatEvent.Completed)
                return null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (call.isCanceled()) {
                onEvent(ChatEvent.Completed)
                return null
            }
            return AttemptError(e.message ?: e.javaClass.simpleName, true)
        } finally {
            activeCall = null
        }
    }

    /** 累积器：正文、思考、工具调用 */
    private class Chunk {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var sawUsage: Pair<Int, Int>? = null
        val toolAcc = LinkedHashMap<Int, ToolCallBuilder>()
    }

    /** 判断一行是不是 SSE 协议行 */
    private fun isSseLine(trimmed: String): Boolean =
        trimmed.startsWith("data:") ||
            trimmed.startsWith("event:") ||
            trimmed.startsWith("id:") ||
            trimmed.startsWith(":") ||
            trimmed == "[DONE]"

    /** 解析一行 SSE，增量回吐正文与工具调用 */
    private fun handleSseLine(line: String, c: Chunk, onEvent: (ChatEvent) -> Unit) {
        val l = line.trim()
        if (l.isEmpty() || l.startsWith(":") || !l.startsWith("data:")) return
        val payload = l.substring(5).trim()
        if (payload.isEmpty() || payload == "[DONE]") return
        val json = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        readUsage(json, c)
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return
        readText(delta, c, onEvent)
        readToolCalls(delta.optJSONArray("tool_calls"), c)
    }

    /** 解析一次性返回的整段 JSON */
    private fun handleWholeBody(raw: String, onEvent: (ChatEvent) -> Unit): AttemptError? {
        val c = Chunk()
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return AttemptError("看不懂服务商返回的内容：${raw.take(200)}", false)
        }
        readUsage(json, c)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val msg = choice?.optJSONObject("message") ?: choice?.optJSONObject("delta")
        if (msg != null) {
            readText(msg, c, onEvent)
            readToolCalls(msg.optJSONArray("tool_calls"), c)
        }
        return finishChunk(c, onEvent)
    }

    private fun readUsage(json: JSONObject, c: Chunk) {
        json.optJSONObject("usage")?.let { u ->
            val p = u.optInt("prompt_tokens", 0)
            val comp = u.optInt("completion_tokens", 0)
            if (p > 0 || comp > 0) c.sawUsage = p to comp
        }
    }

    private fun readText(node: JSONObject, c: Chunk, onEvent: (ChatEvent) -> Unit) {
        if (!node.isNull("content")) {
            val piece = when (val raw = node.opt("content")) {
                null -> ""
                is JSONArray -> buildString {
                    for (i in 0 until raw.length()) {
                        val part = raw.optJSONObject(i) ?: continue
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) append(t)
                    }
                }
                else -> node.optString("content", "")
            }
            if (piece.isNotEmpty()) {
                c.text.append(piece)
                onEvent(ChatEvent.Delta(piece))
            }
        }
        // thinking 类模型：正文和思考是两条通道，思考只走 Reasoning 事件
        for (k in listOf("reasoning_content", "reasoning", "thinking")) {
            if (!node.isNull(k)) {
                val r = node.optString(k, "")
                if (r.isNotEmpty()) {
                    c.reasoning.append(r)
                    onEvent(ChatEvent.Reasoning(r))
                }
            }
        }
    }

    private fun readToolCalls(tcs: JSONArray?, c: Chunk) {
        if (tcs == null) return
        for (i in 0 until tcs.length()) {
            val t = tcs.optJSONObject(i) ?: continue
            val idx = t.optInt("index", i)
            val b = c.toolAcc.getOrPut(idx) { ToolCallBuilder() }
            if (!t.isNull("id")) {
                val id = t.optString("id", "")
                if (id.isNotEmpty()) b.id = id
            }
            val fn = t.optJSONObject("function") ?: continue
            if (!fn.isNull("name")) {
                val n = fn.optString("name", "")
                if (n.isNotEmpty()) b.name = n
            }
            if (!fn.isNull("arguments")) {
                b.args.append(fn.optString("arguments", ""))
            }
        }
    }

    /**
     * 收尾。
     * v1.6.0 起：思考不再冒充正文。
     * - 只有思考、没有正文也没有工具调用 → ReasoningOnly 事件，交给上层决定是重试还是如实告知；
     * - 彻底空白（连思考都没有）→ 当成可重试的错误，以前这种会静默结束、白扣额度。
     */
    private fun finishChunk(c: Chunk, onEvent: (ChatEvent) -> Unit): AttemptError? {
        c.sawUsage?.let { (p, comp) -> onEvent(ChatEvent.Usage(p, comp)) }

        val calls = c.toolAcc.values.filter { it.name.isNotBlank() }.map {
            ToolCall(
                id = it.id.ifBlank { "call_" + System.currentTimeMillis() + "_" + it.name },
                name = it.name,
                arguments = it.args.toString().ifBlank { "{}" }
            )
        }
        if (calls.isNotEmpty()) onEvent(ChatEvent.ToolCalls(calls))

        if (c.text.isBlank() && calls.isEmpty()) {
            if (c.reasoning.isNotBlank()) {
                onEvent(ChatEvent.ReasoningOnly)
                return null
            }
            return AttemptError("服务商返回了空内容：既没有正文，也没有工具调用", true)
        }
        return null
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
        stream: Boolean,
        imageLoader: ((String) -> String?)? = null
    ): String {
        val root = JSONObject()
        root.put("model", settings.model)
        root.put("messages", buildMessages(settings, history, imageLoader))
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

    private fun buildMessages(
        settings: AppSettings,
        history: List<ChatMessage>,
        imageLoader: ((String) -> String?)? = null
    ): JSONArray {
        var keep = history
        val limit = settings.contextLimit
        if (limit > 0 && keep.size > limit) {
            keep = keep.takeLast(limit)
            // 协议要求 tool 消息前面必须有对应的 assistant 调用，别让历史从孤立的 tool 开始
            while (keep.isNotEmpty() && keep.first().role == ROLE_TOOL) keep = keep.drop(1)
        }
        // 图片只带最近几条：请求体一大，网关先给你 413
        val imageMsgIds = HashSet<String>()
        if (settings.sendImages && imageLoader != null) {
            var budget = maxImagesPerRequest
            for (m in keep.asReversed()) {
                if (m.images.isEmpty()) continue
                if (budget <= 0) break
                imageMsgIds.add(m.id)
                budget -= m.images.size
            }
        }
        val arr = JSONArray()
        if (settings.systemPrompt.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", settings.systemPrompt))
        }
        keep.forEach { m ->
            when (m.role) {
                ROLE_ASSISTANT -> {
                    val acts = m.activities.filter { it.name.isNotBlank() }
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
                    // 既没正文也没有工具调用：空 assistant 消息会被部分服务商判 400，直接跳过
                    if (calls.isEmpty() && m.content.isBlank()) return@forEach

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

                else -> arr.put(userMessage(settings, m, m.id in imageMsgIds, imageLoader))
            }
        }
        return arr
    }

    /**
     * 用户消息：带图时按 OpenAI 多模态格式下发 content 数组，
     * 图片由上层给的 imageLoader 转成 data URL（本地压缩后 base64）。
     */
    private fun userMessage(
        settings: AppSettings,
        m: ChatMessage,
        allowImages: Boolean,
        imageLoader: ((String) -> String?)? = null
    ): JSONObject {
        val o = JSONObject().put("role", m.role)
        val urls = if (allowImages && imageLoader != null) {
            m.images.mapNotNull { path ->
                try {
                    imageLoader(path)
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            emptyList()
        }
        if (urls.isEmpty()) {
            o.put("content", m.content)
            return o
        }
        val content = JSONArray()
        if (m.content.isNotBlank()) {
            content.put(JSONObject().put("type", "text").put("text", m.content))
        }
        urls.forEach { u ->
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", u))
            )
        }
        o.put("content", content)
        return o
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
            400 -> "请求被拒绝，检查模型名与参数"
            401 -> "API Key 无效或未填写"
            403 -> "该 Key 无权限访问此模型"
            404 -> "接口地址不对，检查「API 地址」是否包含 /v1"
            408 -> "服务端超时"
            413 -> "请求体过大，把「每次带上的历史消息」调小"
            422 -> "参数不合法，可能该模型不支持某个参数"
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