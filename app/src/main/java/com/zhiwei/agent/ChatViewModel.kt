package com.zhiwei.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiwei.agent.agent.AgentEnv
import com.zhiwei.agent.agent.ImageSupport
import com.zhiwei.agent.agent.ShellRunner
import com.zhiwei.agent.agent.ToolRegistry
import com.zhiwei.agent.agent.ToolSpec
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
import com.zhiwei.agent.data.Persona
import com.zhiwei.agent.data.PersonaStore
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
import com.zhiwei.agent.net.UpdateChecker
import com.zhiwei.agent.net.UpdateState
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
import java.io.File

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

/** 抽屉里的功能页 */
enum class AppScreen { CHAT, TERMINAL, WORKSPACE, MEMORY, KNOWLEDGE, PLUGINS, SETTINGS, ROLE }

/** 会话里挂着的附件：已经复制进工作区；图片会按多模态直接给模型看 */
data class Attachment(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    /** image / text / other */
    val kind: String = "other"
)

/** 工作区里的一个条目 */
data class WorkspaceEntry(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean
)

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
    private val personaStore = PersonaStore(app)
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
    /** 当前网络状态（重试进度），空表示正常 */
    private val _netStatus = MutableStateFlow<String?>(null)
    val netStatus: StateFlow<String?> = _netStatus.asStateFlow()
    /** 工作区（智能体的 home 目录） */
    private val _workspace = MutableStateFlow<List<WorkspaceEntry>>(emptyList())
    val workspace: StateFlow<List<WorkspaceEntry>> = _workspace.asStateFlow()
    /** 正在查看的文件：标题 -> 内容 */
    private val _workspacePreview = MutableStateFlow<Pair<String, String>?>(null)
    val workspacePreview: StateFlow<Pair<String, String>?> = _workspacePreview.asStateFlow()
    /** 更新检查结果 */
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    /** 角色卡：智能体的人设，用户可自己编辑 */
    private val _persona = MutableStateFlow(personaStore.load())
    val persona: StateFlow<Persona> = _persona.asStateFlow()
    /** 当前这条消息要带上的附件 */
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()
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
        // 卡住的忙状态要能自愈：只有真在跑的任务才拦截发送，
        // 否则会出现「发出去没反应、后端也看不到请求」。
        if (_busy.value) {
            if (runJob?.isActive == true) return
            _busy.value = false
        }
        val input = text.trim()
        val pending = _attachments.value
        if (input.isEmpty() && pending.isEmpty()) return
        ensureConversation()
        // 图片走多模态直传；文本类附件把内容内联进提问，其余只给路径
        val images = if (_settings.value.sendImages) {
            pending.filter { it.kind == "image" }.map { it.path }
        } else {
            emptyList()
        }
        val body = buildUserMessage(input, pending.filterNot { it.kind == "image" })
        val newTitle = if (_messages.value.none { it.role == ROLE_USER }) {
            input.ifBlank { pending.first().name }.take(18)
        } else {
            null
        }
        setMessages(
            _messages.value + ChatMessage(role = ROLE_USER, content = body, images = images),
            persist = true,
            newTitle = newTitle
        )
        clearAttachments()
        startRun()
    }
    /**
     * 用户消息正文。
     * 图片不在这里（走 images 字段直传）；文本类附件把内容读进来，省得模型再要一次，
     * 读不了或太大的就给路径 + 提示它用 read_file 工具自己取。
     */
    private fun buildUserMessage(input: String, list: List<Attachment>): String {
        if (list.isEmpty()) return input
        val sb = StringBuilder(input)
        val files = list.filter { it.kind != "image" }
        if (files.isEmpty()) return input
        sb.append(if (sb.isNotEmpty()) "\n\n" else "")
        files.forEach { a ->
            val text = if (a.kind == "text") ImageSupport.inlineText(a.path) else null
            if (text != null) {
                sb.append("【附件 ").append(a.name).append("】\n```\n").append(text).append("\n```\n")
                if (a.size > text.length) sb.append("（内容较长，这里只给了前面一部分，需要全文就用 read_file 读 ").append(a.path).append("）\n")
            } else {
                sb.append("【附件 ").append(a.name).append("】").append(readableSize(a.size))
                sb.append("，已放进工作区：").append(a.path).append("（用 read_file 可以读）\n")
            }
        }
        return sb.toString().trim()
    }

    // ---------- 附件 ----------

    /** 把用户选的文件复制进工作区的 uploads/，之后智能体就能直接读 */
    fun attachFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { copyIntoWorkspace(uris) }
            if (added.isEmpty()) {
                _notice.value = "没能读取所选文件"
                return@launch
            }
            _attachments.update { it + added }
            _notice.value = "已加入 ${added.size} 个附件"
        }
    }

    private fun copyIntoWorkspace(uris: List<Uri>): List<Attachment> {
        val app = getApplication<Application>()
        val dir = File(ShellRunner.homeDir(app.filesDir), "uploads")
        if (!dir.exists()) dir.mkdirs()
        val out = mutableListOf<Attachment>()
        uris.forEach { uri ->
            try {
                val name = queryDisplayName(uri) ?: ("file_" + System.currentTimeMillis())
                var target = File(dir, name)
                if (target.exists()) {
                    val base = name.substringBeforeLast('.', name)
                    val ext = name.substringAfterLast('.', "")
                    target = File(dir, base + "_" + System.currentTimeMillis() % 100000 + if (ext.isEmpty()) "" else ".$ext")
                }
                app.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@forEach
                out.add(
                    Attachment(
                        id = target.absolutePath,
                        name = target.name,
                        path = target.absolutePath,
                        size = target.length(),
                        kind = ImageSupport.kind(target.name)
                    )
                )
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        val app = getApplication<Application>()
        app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    fun removeAttachment(id: String) {
        _attachments.update { list -> list.filterNot { it.id == id } }
    }

    fun clearAttachments() {
        _attachments.value = emptyList()
    }
    /** 图片走多模态直传：本地路径 → data URL。读不到就按没图处理，不让整轮请求挂掉 */
    private fun imageDataUrl(path: String): String? =
        ImageSupport.dataUrl(path, _settings.value.imageMaxSide)

    // ---------- 角色卡 ----------

    fun updatePersona(transform: (Persona) -> Persona) {
        val next = transform(_persona.value)
        _persona.value = next
        viewModelScope.launch(Dispatchers.IO) { personaStore.save(next) }
    }

    fun resetPersona() {
        updatePersona { Persona() }
        _notice.value = "角色卡已恢复默认"
    }

    /** 导出角色卡为 JSON 文本，方便用户备份/分享人设 */
    fun exportPersonaJson(): String = _persona.value.toJson().toString(2)

    /** 从一段 JSON 文本导入角色卡；解析失败返回 false */
    fun importPersonaJson(raw: String): Boolean {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return false
        return try {
            val parsed = Persona.fromJson(JSONObject(raw.substring(start, end + 1)))
            _persona.value = parsed
            viewModelScope.launch(Dispatchers.IO) { personaStore.save(parsed) }
            _notice.value = "角色卡已导入"
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 角色卡 + 工具清单 + 记忆/知识，合成这一回合真正下发的系统提示词 */
    fun composeSystemPrompt(s: AppSettings = _settings.value, tools: List<ToolSpec>? = null): String {
        val sb = StringBuilder(s.systemPrompt.trim())
        sb.append(_persona.value.block())
        val list = tools ?: if (s.enableTools) {
            ToolRegistry.specs(env())
        } else {
            emptyList()
        }
        sb.append(toolManifest(list))
        if (s.injectContext) sb.append(contextBlocks(s, ""))
        return sb.toString()
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
                _netStatus.value = null
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
        // 思维链单独攒一条通道，只用于折叠展示，绝不当正文
        val reasoning = StringBuilder()
        // 只回了思考时，自动补要一次正文（只补一次，不惯着）
        var nudged = 0
        var nudge: String? = null
        // 工具刚截的图：下一轮作为一条用户消息带回对话，模型才能直接看图
        val shotImages = mutableListOf<String>()
        val query = _messages.value.lastOrNull { it.role == ROLE_USER }?.content ?: ""
        // 一回合的系统提示词 = 基础提示词 + 角色卡 + 工具清单 + 记忆/知识命中
        val effective = settings.copy(systemPrompt = composeSystemPrompt(settings, tools))
        var budgetUsed = 0
        var pendingUsage: ChatEvent.Usage? = null
        // 本地只读工具（时间、计算器、设备信息、记忆检索…）免费，不占预算
        val freeTools = tools.filter { it.free }.map { it.name }.toSet()
        // 同一次提问里，同参数的工具只真跑一遍；模型再要就复用结果，不重复执行
        val seenCalls = HashMap<String, String>()
        // 免费工具不占预算，光靠预算拦不住死循环，再加一道步数硬上限
        val maxRounds = (settings.toolMaxRounds + 8).coerceIn(8, 40)
        var rounds = 0
        // 服务商全报错时自动续跑的轮数（最多两次，避免无限循环）
        var autoResumes = 0
        // 模型反复要求同一批工具调用（全部命中缓存）时，不再陪它空转
        var noProgress = 0
        while (true) {
            rounds++
            if (rounds > maxRounds) {
                text.append("\n\n（这一回合已经跑了 $maxRounds 步，先停在这儿，免得空转烧额度。回一句「继续」我就接着做。）")
                replaceLocal(placeholder.id) { it.copy(content = text.toString()) }
                break
            }
            var calls: List<ToolCall> = emptyList()
            var failure: String? = null
            var reasoningOnly = false
            val base = text.toString()
            var roundText = StringBuilder()
            var roundReasoning = StringBuilder()
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
                // 只为这一轮追加的临时提醒，不落进会话历史
                nudge?.let { add(ChatMessage(role = ROLE_USER, content = it)) }
                // 工具刚截的图：包成一条用户消息，模型才能以多模态方式看图
                if (shotImages.isNotEmpty()) {
                    add(
                        ChatMessage(
                            role = ROLE_USER,
                            content = "（下面是刚截的图，直接看图）",
                            images = shotImages.toList()
                        )
                    )
                }
            }

            api.streamChat(effective, history, tools, imageLoader = { p -> imageDataUrl(p) }) { event ->
                when (event) {
                    is ChatEvent.Delta -> {
                        roundText.append(event.text)
                        replaceLocal(placeholder.id) {
                            it.copy(content = base + roundText.toString(), streaming = true)
                        }
                    }

                    is ChatEvent.Reasoning -> {
                        roundReasoning.append(event.text)
                        replaceLocal(placeholder.id) {
                            it.copy(reasoning = reasoning.toString() + roundReasoning.toString(), streaming = true)
                        }
                    }

                    is ChatEvent.ToolCalls -> calls = event.calls
                    is ChatEvent.Usage -> pendingUsage = event
                    is ChatEvent.Retry -> {
                        _netStatus.value = "第 ${event.attempt}/${event.total} 次重试：${brief(event.reason)}"
                    }
                    ChatEvent.Restart -> {
                        // 上一次尝试的半截输出丢掉，免得重试后内容重复
                        roundText = StringBuilder()
                        roundReasoning = StringBuilder()
                        replaceLocal(placeholder.id) { it.copy(content = base, streaming = true) }
                    }
                    ChatEvent.ReasoningOnly -> reasoningOnly = true
                    is ChatEvent.Failure -> failure = event.message
                    ChatEvent.Completed -> Unit
                }
            }
            _netStatus.value = null
            // 思考过程累加进本轮总账，只用于折叠展示，绝不混进正文
            if (roundReasoning.isNotEmpty()) reasoning.append(roundReasoning)
            pendingUsage?.let { u ->
                recordUsage(effective.model, u.prompt, u.completion)
                pendingUsage = null
            }

            if (failure != null) {
                val body = (base + roundText.toString()).trim()
                // 服务商报错不等于任务结束：先自己补一轮，别直接把任务掐了
                if (settings.autoResumeAfterFailure && autoResumes < 2) {
                    autoResumes++
                    text.setLength(0)
                    text.append(body)
                    if (text.isNotEmpty()) text.append("\n\n")
                    text.append("（连接失败：${brief(failure)}，已自动继续）\n")
                    replaceLocal(placeholder.id) { it.copy(content = text.toString(), streaming = true) }
                    _notice.value = "服务商报错，已自动继续：${brief(failure)}"
                    continue
                }
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

            // 只回了思考、正文一个字没给：自动补要一次正文（只补一次，不惯着）
            if (calls.isEmpty() && roundText.isBlank() && reasoningOnly && nudged == 0) {
                nudged++
                nudge = "刚才只输出了思考过程，没有给正文。现在直接给结论，一两句话说清楚，不要重复思考过程。"
                replaceLocal(placeholder.id) {
                    it.copy(reasoning = reasoning.toString(), streaming = true)
                }
                continue
            }
            nudge = null

            if (calls.isEmpty()) break

            // 全部命中缓存 = 模型在原地打转，再陪它跑就是白烧额度
            val fresh = calls.count { seenCalls[it.name + "\u0000" + it.arguments.trim()] == null }
            noProgress = if (fresh == 0) noProgress + 1 else 0
            if (noProgress >= 2) {
                text.append(
                    "\n\n（模型在重复请求同一批工具调用，已经停下，免得空转烧额度。" +
                        "回一句「继续」，或者换个说法再问一次。）"
                )
                replaceLocal(placeholder.id) { it.copy(content = text.toString()) }
                break
            }

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
                if (outcome.images.isNotEmpty()) shotImages.addAll(outcome.images)
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

        // 收尾：一个字都没有、只调了工具时别留空气泡
        // （以前这种情况看起来就是「额度扣了但没有任何输出」）
        val finalText = text.toString().trim()
        val acts = _messages.value.firstOrNull { it.id == placeholder.id }?.activities.orEmpty()
        val blank = finalText.isBlank()
        val finalContent = when {
            !blank -> finalText
            acts.isNotEmpty() -> "（模型只调用了工具，没有返回文字说明。回一句「继续」让它接着往下做。）"
            else -> "（模型没有返回任何内容。回一句「继续」再试一次；一直这样就去检查接口地址与模型名。）"
        }
        replaceLocal(placeholder.id) {
            it.copy(content = finalContent, streaming = false, isError = it.isError || blank)
        }
        _netStatus.value = null
        persistNow()
        maybeExtractMemory()
    }

    /** 把报错信息压成一小行，别把整段 JSON 塞进界面 */
    private fun brief(text: String?): String {
        val t = (text ?: "").replace('\n', ' ').trim()
        return if (t.length > 120) t.take(120) + "…" else t
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

    /**
     * 记忆库 + 知识库的命中片段，拼成一段可直接喂给模型的上下文。
     * query 为空时（例如刚开局）只取最近的记忆，不硬套相关性检索。
     */
    private fun contextBlocks(s: AppSettings, query: String): String {
        val blocks = StringBuilder()
        val mems = if (query.isBlank()) _memories.value.take(s.memoryTopK)
        else memoryStore.search(_memories.value, query, s.memoryTopK)
        if (mems.isNotEmpty()) {
            blocks.append("\n\n【记忆库】跨对话长期记忆，直接当已知事实用，不要问用户是否记得：\n")
            mems.forEach { blocks.append("- ").append(it.text).append('\n') }
        }
        if (query.isNotBlank()) {
            val hits = knowledgeStore.search(_knowledge.value, query, s.knowledgeTopK)
            if (hits.isNotEmpty()) {
                blocks.append("\n【知识库片段】用户导入的资料，回答时优先依据这些内容：\n")
                hits.forEach { blocks.append("〔").append(it.title).append("〕").append(it.text.take(900)).append('\n') }
            }
        }
        return blocks.toString()
    }

    /**
     * 把本轮真正可用的工具清单写进系统提示词。
     * 模型看得到「自己手上有什么工具」，才不会一上来就说「我做不到」。
     */
    private fun toolManifest(tools: List<ToolSpec>): String {
        if (tools.isEmpty()) {
            return "\n\n【当前可用工具】无。这一轮工具调用是关闭的，只能靠你已有的知识回答；" +
                "如果用户要的操作必须用工具，就直接说明需要先把「工具」打开。"
        }
        val sb = StringBuilder()
        sb.append("\n\n【当前可用工具】下面这些是你这一轮可以真正调用的能力，需要就用，不要只是嘴上说说：\n")
        tools.forEach { t -> sb.append("- ").append(t.name).append("（").append(t.label).append("）：").append(t.description).append('\n') }
        sb.append("调用规则：先想清楚要不要用工具，该用就直接调，一次可以调多个；")
        sb.append("不要先描述「我打算调用某工具」再等用户点确认，直接调；")
        sb.append("工具返回结果之后，用自然语言把结论讲给用户，不要只交一坨原始输出。")
        return sb.toString()
    }

    /** 兼容旧调用：把上下文塞进 settings 的 systemPrompt 里 */
    private fun injectContext(s: AppSettings, query: String): AppSettings {
        val blocks = contextBlocks(s, query)
        if (blocks.isEmpty()) return s
        return s.copy(systemPrompt = s.systemPrompt + blocks)
    }

    // ---------- 记忆库 ----------

    /** 对话结束后自动抽取长期记忆 */
    private suspend fun maybeExtractMemory() {
        val settings = _settings.value
        if (!settings.autoMemory) return
        if (settings.usableKeys().isEmpty()) return
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

    // ---------- 继续执行 ----------

    /** 上一轮失败或中途停下时，点「继续」接着做 */
    fun continueRun(text: String = "继续") {
        if (_busy.value) return
        send(text)
    }

    // ---------- 工作区：智能体的 home 目录 ----------

    fun workspaceDir(): String =
        ShellRunner.homeDir(getApplication<Application>().filesDir).absolutePath

    fun refreshWorkspace() {
        viewModelScope.launch {
            val root = ShellRunner.homeDir(getApplication<Application>().filesDir)
            _workspace.value = withContext(Dispatchers.IO) { listWorkspace(root) }
        }
    }

    private fun listWorkspace(root: File): List<WorkspaceEntry> {
        val files = root.listFiles() ?: return emptyList()
        return files.map { f ->
            WorkspaceEntry(
                name = f.name,
                path = f.absolutePath,
                size = if (f.isDirectory) 0L else f.length(),
                modified = f.lastModified(),
                isDir = f.isDirectory
            )
        }.sortedWith(
            compareByDescending<WorkspaceEntry> { it.isDir }.thenByDescending { it.modified }
        )
    }

    /** 看一眼文本文件；二进制和超大文件直接说明白，不硬塞 */
    fun openWorkspaceFile(entry: WorkspaceEntry) {
        if (entry.isDir) {
            _notice.value = "暂不支持子目录：${entry.name}"
            return
        }
        val f = File(entry.path)
        if (f.length() > 512 * 1024) {
            _notice.value = "文件太大（${readableSize(f.length())}），去终端里看"
            return
        }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { f.readText() }.getOrElse { "（读不了：${it.message}）" }
            }
            val binary = text.contains('\u0000')
            _workspacePreview.value = entry.name to
                if (binary) "（二进制文件，没法直接预览）" else text
        }
    }

    fun closeWorkspacePreview() {
        _workspacePreview.value = null
    }

    fun deleteWorkspaceFile(entry: WorkspaceEntry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(entry.path).delete() }.getOrDefault(false)
            }
            _notice.value = if (ok) "已删除 ${entry.name}" else "删除失败：${entry.name}"
            refreshWorkspace()
        }
    }

    // ---------- 更新检查 ----------

    fun appVersionName(): String = runCatching {
        val app = getApplication<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "1.4.0"

    fun checkUpdate() {
        if (_update.value is UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val r = UpdateChecker.check(appVersionName())
            _update.value = r.fold(
                onSuccess = { UpdateState.Done(it) },
                onFailure = { UpdateState.Error(it.message ?: "检查更新失败") }
            )
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateState.Idle
    }
}

/** 人类看得懂的大小 */
fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}