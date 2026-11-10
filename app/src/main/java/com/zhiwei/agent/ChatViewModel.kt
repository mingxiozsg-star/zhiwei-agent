package com.zhiwei.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiwei.agent.agent.AgentEnv
import com.zhiwei.agent.agent.ShellRunner
import com.zhiwei.agent.agent.ToolRegistry
import com.zhiwei.agent.data.ACT_DONE
import com.zhiwei.agent.data.ACT_ERROR
import com.zhiwei.agent.data.ACT_RUNNING
import com.zhiwei.agent.data.AppSettings
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.ConversationStore
import com.zhiwei.agent.data.KnowledgeItem
import com.zhiwei.agent.data.KnowledgeStore
import com.zhiwei.agent.data.MemoryItem
import com.zhiwei.agent.data.MemoryStore
import com.zhiwei.agent.data.PluginStore
import com.zhiwei.agent.data.PluginTool
import com.zhiwei.agent.data.PromptStore
import com.zhiwei.agent.data.PromptTemplate
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_TOOL
import com.zhiwei.agent.data.ROLE_USER
import com.zhiwei.agent.data.SettingsStore
import com.zhiwei.agent.data.StatsStore
import com.zhiwei.agent.data.ToolActivity
import com.zhiwei.agent.data.ToolCall
import com.zhiwei.agent.data.UsageSnapshot
import com.zhiwei.agent.net.ChatApi
import com.zhiwei.agent.net.ChatEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

/** 抽屉里的功能页 */
enum class AppScreen { CHAT, TERMINAL, MEMORY, KNOWLEDGE, PLUGINS, SETTINGS }

private const val EXTRACT_PROMPT =
    "你是记忆抽取器。从下面的对话里找出值得长期记住的事实：用户的身份、偏好、习惯、长期目标、重要约定、正在做的项目。\n" +
        "只输出 JSON 数组，元素是简短的中文句子，每条自带上下文（例如「用户常用 DeepSeek 的中转接口」）。\n" +
        "没有值得记的就输出 []。不要输出任何解释文字。"

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConversationStore(app)
    private val settingsStore = SettingsStore(app)
    private val memoryStore = MemoryStore(app)
    private val knowledgeStore = KnowledgeStore(app)
    private val pluginStore = PluginStore(app)
    private val statsStore = StatsStore(app)
    private val promptStore = PromptStore(app)
    private val api = ChatApi()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _terminalBusy = MutableStateFlow(false)
    val terminalBusy: StateFlow<Boolean> = _terminalBusy.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val _knowledge = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val knowledge: StateFlow<List<KnowledgeItem>> = _knowledge.asStateFlow()

    private val _plugins = MutableStateFlow<List<PluginTool>>(emptyList())
    val plugins: StateFlow<List<PluginTool>> = _plugins.asStateFlow()

    private val _memoryBusy = MutableStateFlow(false)
    val memoryBusy: StateFlow<Boolean> = _memoryBusy.asStateFlow()
    /** 本机累计用量，只记服务端返回的 usage */
    private val _usage = MutableStateFlow(UsageSnapshot())
    val usage: StateFlow<UsageSnapshot> = _usage.asStateFlow()
    /** 密钥池批量测活结果：Key -> 结论 */
    private val _keyTests = MutableStateFlow<Map<String, String>>(emptyMap())
    val keyTests: StateFlow<Map<String, String>> = _keyTests.asStateFlow()
    private val _keyTesting = MutableStateFlow(false)
    val keyTesting: StateFlow<Boolean> = _keyTesting.asStateFlow()
    /** 提示词库：聊天页点一下就填进输入框 */
    private val _prompts = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val prompts: StateFlow<List<PromptTemplate>> = _prompts.asStateFlow()
    private val statsLock = Any()

    private var runJob: Job? = null

    val currentConversation: Conversation?
        get() = _conversations.value.firstOrNull { it.id == _currentId.value }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            if (loaded.isEmpty()) {
                val c = Conversation()
                _conversations.value = listOf(c)
                _currentId.value = c.id
                _messages.value = emptyList()
            } else {
                val sorted = loaded.sortedByDescending { it.updatedAt }
                _conversations.value = sorted
                _currentId.value = sorted.first().id
                _messages.value = sorted.first().messages
            }
            _memories.value = withContext(Dispatchers.IO) { memoryStore.load() }
            _knowledge.value = withContext(Dispatchers.IO) { knowledgeStore.load() }
            _plugins.value = withContext(Dispatchers.IO) { pluginStore.load() }
            _usage.value = withContext(Dispatchers.IO) { statsStore.load() }
            _prompts.value = withContext(Dispatchers.IO) { promptStore.load() }
        }
    }

    // ---------- 提示词库 ----------

    fun addPrompt(title: String, text: String) {
        val t = title.trim()
        val body = text.trim()
        if (body.isEmpty()) {
            _notice.value = "提示词内容不能为空"
            return
        }
        val item = PromptTemplate(title = t.ifBlank { body.take(8) }, text = body)
        savePrompts(_prompts.value + item)
        _notice.value = "已添加快捷短语「${item.title}」"
    }

    fun updatePrompt(id: String, title: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) {
            _notice.value = "提示词内容不能为空"
            return
        }
        savePrompts(
            _prompts.value.map {
                if (it.id == id) it.copy(title = title.trim().ifBlank { body.take(8) }, text = body) else it
            }
        )
    }

    fun deletePrompt(id: String) {
        savePrompts(_prompts.value.filter { it.id != id })
    }

    fun resetPrompts() {
        _prompts.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { promptStore.save(emptyList()) }
        _notice.value = "提示词库已清空"
    }

    private fun savePrompts(list: List<PromptTemplate>) {
        _prompts.value = list
        viewModelScope.launch(Dispatchers.IO) { promptStore.save(list) }
    }

    // ---------- 会话管理 ----------

    fun newConversation() {
        val c = Conversation()
        _conversations.update { listOf(c) + it }
        _currentId.value = c.id
        _messages.value = emptyList()
        persistNow()
    }

    fun selectConversation(id: String) {
        val conv = _conversations.value.firstOrNull { it.id == id } ?: return
        _currentId.value = id
        _messages.value = conv.messages
    }

    fun deleteConversation(id: String) {
        val remaining = _conversations.value.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val c = Conversation()
            _conversations.value = listOf(c)
            _currentId.value = c.id
            _messages.value = emptyList()
        } else {
            _conversations.value = remaining
            if (_currentId.value == id) {
                _currentId.value = remaining.first().id
                _messages.value = remaining.first().messages
            }
        }
        persistNow()
    }

    fun renameConversation(id: String, title: String) {
        val t = title.trim().ifEmpty { "未命名" }
        _conversations.update { list -> list.map { if (it.id == id) it.copy(title = t) else it } }
        persistNow()
    }

    fun clearMessages() {
        setMessages(emptyList(), persist = true)
    }

    fun clearAllConversations() {
        val c = Conversation()
        _conversations.value = listOf(c)
        _currentId.value = c.id
        _messages.value = emptyList()
        persistNow()
    }

    fun deleteMessage(id: String) {
        setMessages(_messages.value.filterNot { it.id == id }, persist = true)
    }

    fun exportCurrent(): String {
        val conv = currentConversation ?: return ""
        return store.exportMarkdown(conv)
    }

    // ---------- 对话 ----------

    fun send(text: String) {
        if (_busy.value) return
        val input = text.trim()
        if (input.isEmpty()) return
        ensureConversation()
        val newTitle = if (_messages.value.none { it.role == ROLE_USER }) input.take(18) else null
        setMessages(
            _messages.value + ChatMessage(role = ROLE_USER, content = input),
            persist = true,
            newTitle = newTitle
        )
        startRun()
    }

    fun stopGenerating() {
        api.cancel()
        runJob?.cancel()
        runJob = null
        _busy.value = false
        setMessages(
            _messages.value.map { m ->
                if (m.streaming) {
                    m.copy(
                        streaming = false,
                        activities = m.activities.map {
                            if (it.status == ACT_RUNNING) it.copy(status = ACT_ERROR)
                            else it
                        }
                    )
                } else {
                    m
                }
            },
            persist = true
        )
    }

    fun regenerate() {
        if (_busy.value) return
        var list = _messages.value
        while (list.isNotEmpty() && list.last().role == ROLE_TOOL) list = list.dropLast(1)
        if (list.isNotEmpty() && list.last().role == ROLE_ASSISTANT) list = list.dropLast(1)
        if (list.isEmpty()) return
        setMessages(list, persist = true)
        startRun()
    }

    private fun startRun() {
        _busy.value = true
        runJob = viewModelScope.launch {
            try {
                assistantLoop()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = "出错了：${e.message}"
            } finally {
                _busy.value = false
                runJob = null
                setMessages(
                    _messages.value.map { if (it.streaming) it.copy(streaming = false) else it },
                    persist = true
                )
                notifyIfEnabled()
            }
        }
    }

    /**
     * 一个回合 = 界面上一条消息。
     * 工具调用挂在这条消息的 activities 上，不再拆成多条。
     */
    private suspend fun assistantLoop() {
        val settings = _settings.value
        val env = env()
        val tools = if (settings.enableTools) ToolRegistry.specs(env) else emptyList()

        val placeholder = ChatMessage(role = ROLE_ASSISTANT, content = "", streaming = true)
        appendLocal(placeholder)

        val text = StringBuilder()
        val query = _messages.value.lastOrNull { it.role == ROLE_USER }?.content ?: ""
        val effective = if (settings.injectContext) injectContext(settings, query) else settings
        var budgetUsed = 0
        var pendingUsage: ChatEvent.Usage? = null
        // 本地只读工具（时间、计算器、设备信息、记忆检索…）免费，不占预算
        val freeTools = tools.filter { it.free }.map { it.name }.toSet()
        // 同一次提问里，同参数的工具只真跑一遍；模型再要就复用结果，不重复执行
        val seenCalls = HashMap<String, String>()
        while (true) {
            var calls: List<ToolCall> = emptyList()
            var failure: String? = null
            val base = text.toString()
            val roundText = StringBuilder()
            val snapshot = _messages.value
            // 本回合前面几轮已经跑过的工具调用必须带进历史，否则模型看不到结果，
            // 会以为工具没生效，一遍遍重调同一个工具。
            val history = buildList {
                addAll(snapshot.filter { it.id != placeholder.id })
                snapshot.firstOrNull { it.id == placeholder.id }?.let { cur ->
                    if (cur.activities.isNotEmpty() || base.isNotBlank()) {
                        add(cur.copy(streaming = false))
                    }
                }
            }

            api.streamChat(effective, history, tools) { event ->
                when (event) {
                    is ChatEvent.Delta -> {
                        roundText.append(event.text)
                        replaceLocal(placeholder.id) {
                            it.copy(content = base + roundText.toString(), streaming = true)
                        }
                    }

                    is ChatEvent.ToolCalls -> calls = event.calls
                    is ChatEvent.Usage -> pendingUsage = event
                    is ChatEvent.Failure -> failure = event.message
                    ChatEvent.Completed -> Unit
                }
            }
            pendingUsage?.let { u ->
                recordUsage(effective.model, u.prompt, u.completion)
                pendingUsage = null
            }

            if (failure != null) {
                val body = (base + roundText.toString()).trim()
                replaceLocal(placeholder.id) {
                    it.copy(
                        content = if (body.isEmpty()) "请求失败：$failure" else body,
                        isError = true,
                        streaming = false
                    )
                }
                break
            }

            text.append(roundText)
            replaceLocal(placeholder.id) { it.copy(content = text.toString(), streaming = true) }

            if (calls.isEmpty()) break

            val cost = calls.count { it.name !in freeTools }
            if (budgetUsed > 0 && budgetUsed + cost > settings.toolMaxRounds) {
                text.append(
                    "\n\n（这一回合的工具预算用完了：${settings.toolMaxRounds} 次。回一句「继续」我就接着往下做，" +
                        "或者去设置里把「每回合工具预算」调大。）"
                )
                replaceLocal(placeholder.id) { it.copy(content = text.toString()) }
                break
            }
            budgetUsed += cost

            calls.forEach { call ->
                val label = ToolRegistry.labelFor(env, call.name)
                val memoKey = call.name + "\u0000" + call.arguments.trim()
                val cached = seenCalls[memoKey]
                replaceLocal(placeholder.id) { msg ->
                    msg.copy(
                        activities = msg.activities + ToolActivity(
                            callId = call.id,
                            name = call.name,
                            label = label,
                            arguments = call.arguments,
                            result = if (cached == null) "" else replayNote(cached),
                            status = if (cached == null) ACT_RUNNING else ACT_DONE,
                            finishedAt = if (cached == null) 0L else System.currentTimeMillis()
                        )
                    )
                }
                if (cached != null) return@forEach
                val outcome = ToolRegistry.execute(env, call.name, call.arguments)
                seenCalls[memoKey] = outcome.text
                outcome.newMemory?.let { addMemoryItem(it, persist = true) }
                outcome.newKnowledge?.let { addKnowledgeItem(it, persist = true) }
                val failed = outcome.text.startsWith("工具执行失败")
                replaceLocal(placeholder.id) { msg ->
                    msg.copy(
                        activities = msg.activities.map {
                            if (it.callId == call.id) {
                                it.copy(
                                    result = if (failed) "【执行失败】$outcome.text" else outcome.text,
                                    status = if (failed) ACT_ERROR else ACT_DONE,
                                    finishedAt = System.currentTimeMillis()
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }

        replaceLocal(placeholder.id) { it.copy(content = text.toString(), streaming = false) }
        persistNow()
        maybeExtractMemory()
    }

    /** 同一次提问里重复调用同一工具（参数也一样）时，直接复用上次结果，不真跑第二遍 */
    private fun replayNote(result: String): String =
        "（同一次提问里已经用同样的参数调用过，这次直接复用上次结果）\n$result"

    private fun ensureConversation() {
        val id = _currentId.value
        if (id != null && _conversations.value.any { it.id == id }) return
        val c = Conversation()
        _conversations.update { listOf(c) + it }
        _currentId.value = c.id
        _messages.value = emptyList()
    }

    private fun setMessages(
        list: List<ChatMessage>,
        persist: Boolean = false,
        newTitle: String? = null
    ) {
        _messages.value = list
        val id = _currentId.value
        if (id != null) {
            val now = System.currentTimeMillis()
            _conversations.update { convs ->
                convs.map { c ->
                    if (c.id == id) {
                        c.copy(
                            messages = list,
                            updatedAt = now,
                            title = newTitle ?: c.title
                        )
                    } else {
                        c
                    }
                }
            }
        }
        if (persist) persistNow()
    }

    private fun appendLocal(message: ChatMessage) {
        setMessages(_messages.value + message)
    }

    private fun replaceLocal(id: String, transform: (ChatMessage) -> ChatMessage) {
        setMessages(_messages.value.map { if (it.id == id) transform(it) else it })
    }

    private fun persistNow() {
        val snapshot = _conversations.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    // ---------- 上下文注入 ----------

    private fun env(): AgentEnv = AgentEnv(
        context = getApplication(),
        settings = _settings.value,
        memory = memoryStore,
        knowledge = knowledgeStore,
        pluginStore = pluginStore,
        memories = _memories.value,
        knowledgeList = _knowledge.value,
        pluginList = _plugins.value
    )

    private fun injectContext(s: AppSettings, query: String): AppSettings {
        val blocks = StringBuilder()
        val mems = memoryStore.search(_memories.value, query, s.memoryTopK)
        if (mems.isNotEmpty()) {
            blocks.append("\n\n【记忆库】跨对话长期记忆，直接当已知事实用，不要问用户是否记得：\n")
            mems.forEach { blocks.append("- ").append(it.text).append('\n') }
        }
        val hits = knowledgeStore.search(_knowledge.value, query, s.knowledgeTopK)
        if (hits.isNotEmpty()) {
            blocks.append("\n【知识库片段】用户导入的资料，回答时优先依据这些内容：\n")
            hits.forEach { blocks.append("〔").append(it.title).append("〕").append(it.text.take(900)).append('\n') }
        }
        if (blocks.isEmpty()) return s
        return s.copy(systemPrompt = s.systemPrompt + blocks)
    }

    // ---------- 记忆库 ----------

    /** 对话结束后自动抽取长期记忆 */
    private suspend fun maybeExtractMemory() {
        val settings = _settings.value
        if (!settings.autoMemory) return
        if (settings.apiKey.isBlank() && !settings.baseUrl.contains("localhost")) {
            // 本地接口不需要 key，其他情况没 key 就没法抽取
        }
        val msgs = _messages.value
        val userMsgs = msgs.filter { it.role == ROLE_USER }
        if (userMsgs.isEmpty()) return

        val convo = msgs.takeLast(8).joinToString("\n") { m ->
            when (m.role) {
                ROLE_USER -> "用户：" + m.content.take(600)
                ROLE_ASSISTANT -> "助手：" + m.content.take(600)
                else -> ""
            }
        }.trim()
        if (convo.isBlank()) return

        _memoryBusy.value = true
        try {
            val extraRules = settings.memoryExtractRules.trim()
            val extractSettings = settings.copy(
                systemPrompt = if (extraRules.isEmpty()) EXTRACT_PROMPT
                else EXTRACT_PROMPT + "\n附加规则：\n" + extraRules,
                streamOutput = false,
                model = if (settings.extractModel.isNotBlank()) settings.extractModel else settings.model,
                maxTokens = 600
            )
            val result = api.chatOnce(
                extractSettings,
                listOf(ChatMessage(role = ROLE_USER, content = convo)),
                emptyList()
            )
            result.onSuccess { r ->
                recordUsage(extractSettings.model, r.promptTokens, r.completionTokens)
                parseMemories(r.content).forEach { addMemoryItem(it, persist = false) }
                persistMemories()
            }
        } catch (_: Exception) {
            // 抽取失败不影响对话
        } finally {
            _memoryBusy.value = false
        }
    }

    private fun parseMemories(content: String): List<MemoryItem> {
        val t = content.trim()
        if (t.isEmpty()) return emptyList()
        val out = mutableListOf<MemoryItem>()
        val jsonText = t.substringAfter("[", "").let { if (it.isEmpty()) "" else "[" + it }
        if (jsonText.isNotEmpty()) {
            try {
                val arr = JSONArray(jsonText.substringBeforeLast("]") + "]")
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotEmpty()) out.add(MemoryItem(text = s, source = "自动记忆"))
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }
        t.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotEmpty() && it.length in 4..200 && !it.startsWith("[") }
            .forEach { out.add(MemoryItem(text = it, source = "自动记忆")) }
        return out
    }

    fun addMemoryItem(item: MemoryItem, persist: Boolean = true) {
        _memories.update { memoryStore.upsert(it, item) }
        if (persist) persistMemories()
    }

    fun addMemory(text: String, source: String = "手动") {
        val t = text.trim()
        if (t.isEmpty()) return
        addMemoryItem(MemoryItem(text = t, source = source))
        _notice.value = "已加入记忆"
    }

    fun updateMemory(id: String, text: String) {
        _memories.update { list -> list.map { if (it.id == id) it.copy(text = text.trim()) else it } }
        persistMemories()
    }

    fun togglePinMemory(id: String) {
        _memories.update { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
        persistMemories()
    }

    fun deleteMemory(id: String) {
        _memories.update { list -> list.filterNot { it.id == id } }
        persistMemories()
    }

    fun clearMemories() {
        _memories.value = emptyList()
        persistMemories()
    }

    /** 手动的“重新抽取记忆” */
    fun extractMemoryNow() {
        if (_memoryBusy.value) return
        viewModelScope.launch {
            maybeExtractMemory()
            _notice.value = "已根据当前对话更新记忆（共 ${_memories.value.size} 条）"
        }
    }

    private fun persistMemories() {
        val snapshot = _memories.value
        viewModelScope.launch(Dispatchers.IO) { memoryStore.save(snapshot) }
    }

    // ---------- 知识库 ----------

    fun addKnowledgeItem(item: KnowledgeItem, persist: Boolean = true) {
        _knowledge.update { listOf(item) + it }
        if (persist) persistKnowledge()
        _notice.value = "已存进知识库：${item.title}"
    }

    fun addKnowledge(title: String, content: String, source: String = "手动添加") {
        val t = title.trim().ifBlank { "未命名知识" }
        val c = content.trim()
        if (c.isEmpty()) return
        addKnowledgeItem(KnowledgeItem(title = t, content = c, source = source))
    }

    fun updateKnowledge(id: String, title: String, content: String) {
        _knowledge.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(title = title.trim(), content = content.trim(), updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        persistKnowledge()
    }

    fun deleteKnowledge(id: String) {
        _knowledge.update { list -> list.filterNot { it.id == id } }
        persistKnowledge()
    }

    fun clearKnowledge() {
        _knowledge.value = emptyList()
        persistKnowledge()
    }

    /** 抓取网页正文后直接存进知识库 */
    fun importFromUrl(url: String, title: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        viewModelScope.launch {
            _notice.value = "正在抓取…"
            val text = withContext(Dispatchers.IO) {
                try {
                    val fixed = if (u.startsWith("http")) u else "https://$u"
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder()
                        .url(fixed)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ZhiweiAgent")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext "抓取失败：HTTP ${resp.code}"
                        val body = resp.body?.string().orEmpty()
                        ToolRegistry.htmlToText(body).take(20000)
                    }
                } catch (e: Exception) {
                    "抓取失败：${e.message}"
                }
            }
            if (text.startsWith("抓取失败")) {
                _notice.value = text
            } else {
                val t = title.trim().ifBlank { u.substringAfterLast("/").take(40).ifBlank { "网页资料" } }
                addKnowledgeItem(
                    KnowledgeItem(title = t, content = text, source = "网页 $u")
                )
            }
        }
    }

    private fun persistKnowledge() {
        val snapshot = _knowledge.value
        viewModelScope.launch(Dispatchers.IO) { knowledgeStore.save(snapshot) }
    }

    // ---------- 插件 ----------

    fun importPlugins(text: String, mcpUrl: String = ""): Int {
        val parsed = com.zhiwei.agent.data.PluginParser.parse(text, mcpUrl)
        if (parsed.isEmpty()) {
            _notice.value = "没解析出任何工具，检查一下粘贴的内容"
            return 0
        }
        _plugins.update { existing ->
            val names = existing.map { it.safeName() }.toMutableSet()
            val added = parsed.filter { names.add(it.safeName()) }
            existing + added
        }
        persistPlugins()
        _notice.value = "已导入 ${parsed.size} 个工具"
        return parsed.size
    }

    fun addPlugin(plugin: PluginTool) {
        _plugins.update { it + plugin }
        persistPlugins()
        _notice.value = "已添加工具：${plugin.name}"
    }

    fun updatePlugin(plugin: PluginTool) {
        _plugins.update { list -> list.map { if (it.id == plugin.id) plugin else it } }
        persistPlugins()
    }

    fun togglePlugin(id: String) {
        _plugins.update { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
        persistPlugins()
    }

    fun deletePlugin(id: String) {
        _plugins.update { list -> list.filterNot { it.id == id } }
        persistPlugins()
    }

    /** 试跑一次，返回结果文本 */
    fun testPlugin(id: String, argsJson: String) {
        val plugin = _plugins.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _notice.value = "正在试跑 ${plugin.name}…"
            val out = com.zhiwei.agent.agent.PluginRunner.execute(env(), plugin, argsJson)
            _pluginTestResult.value = plugin.name + "\n" + out
        }
    }

    private val _pluginTestResult = MutableStateFlow<String?>(null)
    val pluginTestResult: StateFlow<String?> = _pluginTestResult.asStateFlow()

    fun clearPluginTestResult() {
        _pluginTestResult.value = null
    }

    private fun persistPlugins() {
        val snapshot = _plugins.value
        viewModelScope.launch(Dispatchers.IO) { pluginStore.save(snapshot) }
    }

    // ---------- 设置 ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        settingsStore.save(next)
    }

    fun refreshModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            _modelsLoading.value = true
            val result = api.listModels(_settings.value)
            _modelsLoading.value = false
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    _notice.value = "服务端没有返回模型列表"
                } else {
                    _models.value = list
                    _notice.value = "已获取 ${list.size} 个可用模型"
                }
            }.onFailure { e ->
                _notice.value = "获取模型失败：${e.message}"
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }
    // ---------- 用量统计 ----------
    /** 只记服务端真实返回的 token，不做本地估算 */
    private fun recordUsage(model: String, prompt: Int, completion: Int) {
        if (prompt <= 0 && completion <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val next = synchronized(statsLock) {
                statsStore.add(_usage.value, model.ifBlank { "未知模型" }, prompt, completion)
            }
            _usage.value = next
        }
    }

    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val empty = synchronized(statsLock) { statsStore.reset() }
            _usage.value = empty
        }
        _notice.value = "用量统计已清零"
    }
    // ---------- 密钥池 ----------
    /** 逐个跑一遍密钥池，把可用性写进 keyTests */
    fun testKeys() {
        if (_keyTesting.value) return
        val s = _settings.value
        val keys = s.usableKeys()
        if (keys.isEmpty()) {
            _notice.value = "密钥池是空的，先填几个 Key"
            return
        }
        viewModelScope.launch {
            _keyTesting.value = true
            val acc = LinkedHashMap<String, String>()
            keys.forEach { k ->
                acc[k] = "测试中…"
                _keyTests.value = LinkedHashMap(acc)
                val r = api.testKey(s, k)
                acc[k] = r.getOrElse { it.message ?: "不可用" }
                _keyTests.value = LinkedHashMap(acc)
            }
            _keyTesting.value = false
            val ok = acc.values.count { it == "可用" }
            _notice.value = "密钥池测完了：可用 $ok / ${keys.size}"
        }
    }
    fun clearKeyTests() {
        _keyTests.value = emptyMap()
    }
    /** 开了「回复完成提醒」就弹一条通知 */
    private fun notifyIfEnabled() {
        if (!_settings.value.notifyOnDone) return
        val text = _messages.value.lastOrNull { it.role == ROLE_ASSISTANT }?.content.orEmpty()
        val brief = text.replace('\n', ' ').trim().take(60).ifBlank { "已生成回复" }
        Notifier.notifyDone(getApplication(), "知微回答完毕", brief)
    }

    // ---------- 终端 ----------

    fun runCommand(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || _terminalBusy.value) return
        _terminalLines.update { it + TerminalLine("$ $cmd", isCommand = true) }
        _terminalBusy.value = true
        viewModelScope.launch {
            val home = ShellRunner.homeDir(getApplication<Application>().filesDir)
            val result = withContext(Dispatchers.IO) { ShellRunner.run(cmd, home, timeoutMs = 30000) }
            val out = result.output.trimEnd()
            _terminalLines.update { list ->
                val added = mutableListOf<TerminalLine>()
                if (out.isNotEmpty()) added.add(TerminalLine(out, isError = !result.ok))
                if (!result.ok && out.isEmpty()) {
                    added.add(TerminalLine("（退出码 ${result.exitCode}）", isError = true))
                }
                list + added
            }
            _terminalBusy.value = false
        }
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    // ---------- 杂项 ----------

    fun copyToClipboard(text: String) {
        try {
            val cm = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("zhiwei", text))
            _notice.value = "已复制到剪贴板"
        } catch (e: Exception) {
            _notice.value = "复制失败：${e.message}"
        }
    }

    fun toast(text: String) {
        _notice.value = text
    }

    /** 供设置页做 JSON 校验 */
    fun prettyJson(raw: String): String? = try {
        JSONObject(raw).toString(2)
    } catch (_: Exception) {
        null
    }
}