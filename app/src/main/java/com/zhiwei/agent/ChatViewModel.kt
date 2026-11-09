package com.zhiwei.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiwei.agent.agent.ShellRunner
import com.zhiwei.agent.agent.ToolRegistry
import com.zhiwei.agent.data.AppSettings
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.ConversationStore
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_TOOL
import com.zhiwei.agent.data.ROLE_USER
import com.zhiwei.agent.data.SettingsStore
import com.zhiwei.agent.data.ToolCall
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

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConversationStore(app)
    private val settingsStore = SettingsStore(app)
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
        }
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
            _messages.value.map { if (it.streaming) it.copy(streaming = false) else it },
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
            } finally {
                _busy.value = false
                runJob = null
                setMessages(
                    _messages.value.map { if (it.streaming) it.copy(streaming = false) else it },
                    persist = true
                )
            }
        }
    }

    private suspend fun assistantLoop() {
        val settings = _settings.value
        var round = 0
        while (true) {
            val placeholder = ChatMessage(role = ROLE_ASSISTANT, content = "", streaming = true)
            appendLocal(placeholder)

            val sb = StringBuilder()
            var calls: List<ToolCall> = emptyList()
            var failure: String? = null

            val tools = if (settings.enableTools) ToolRegistry.specs(settings) else emptyList()
            val history = _messages.value.filter { it.id != placeholder.id }

            api.streamChat(settings, history, tools) { event ->
                when (event) {
                    is ChatEvent.Delta -> {
                        sb.append(event.text)
                        replaceLocal(placeholder.id) { it.copy(content = sb.toString(), streaming = true) }
                    }

                    is ChatEvent.ToolCalls -> calls = event.calls
                    is ChatEvent.Failure -> failure = event.message
                    ChatEvent.Completed -> Unit
                }
            }

            if (failure != null) {
                val body = sb.toString().trim()
                val shown = failure ?: "未知错误"
                replaceLocal(placeholder.id) {
                    it.copy(
                        content = if (body.isEmpty()) "请求失败：$shown" else body,
                        isError = true,
                        streaming = false
                    )
                }
                appendLocal(
                    ChatMessage(
                        role = ROLE_TOOL,
                        toolName = "error",
                        content = shown
                    )
                )
                break
            }

            replaceLocal(placeholder.id) {
                it.copy(content = sb.toString(), toolCalls = calls, streaming = false)
            }

            if (calls.isEmpty()) break

            if (round >= settings.toolMaxRounds) {
                appendLocal(
                    ChatMessage(
                        role = ROLE_TOOL,
                        toolName = "limit",
                        content = "已达到工具调用轮次上限（${settings.toolMaxRounds} 轮），先停在这里。"
                    )
                )
                break
            }

            calls.forEach { call ->
                val toolMsg = ChatMessage(
                    role = ROLE_TOOL,
                    toolCallId = call.id,
                    toolName = call.name,
                    content = "执行中…"
                )
                appendLocal(toolMsg)
                val result = ToolRegistry.execute(
                    getApplication(),
                    settings,
                    call.name,
                    call.arguments
                )
                replaceLocal(toolMsg.id) { it.copy(content = result) }
            }
            round++
        }
    }

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
                    added.add(TerminalLine("命令没有输出（退出码 ${result.exitCode}）", isError = true))
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
}