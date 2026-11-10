package com.zhiwei.agent.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.COLLAPSE_MULTI
import com.zhiwei.agent.data.COLLAPSE_NONE
import com.zhiwei.agent.data.COLLAPSE_SINGLE
import com.zhiwei.agent.data.DEFAULT_SYSTEM_PROMPT
import com.zhiwei.agent.data.THEME_DARK
import com.zhiwei.agent.data.THEME_LIGHT
import com.zhiwei.agent.data.THEME_SYSTEM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onOpenDrawer: () -> Unit = {}) {
    val settings by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()
    val usage by vm.usage.collectAsState()
    val keyTests by vm.keyTests.collectAsState()
    val keyTesting by vm.keyTesting.collectAsState()

    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var showKey by remember { mutableStateOf(false) }
    var showPool by remember { mutableStateOf(false) }
    var tokensText by remember { mutableStateOf(if (settings.maxTokens > 0) settings.maxTokens.toString() else "") }
    var promptOpen by remember { mutableStateOf(false) }
    var paramOpen by remember { mutableStateOf(false) }
    var confirmClearConvs by remember { mutableStateOf(false) }
    var confirmClearMessages by remember { mutableStateOf(false) }
    var confirmResetStats by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.2.0"
    }
    val poolKeys = settings.usableKeys()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------- 接口 ----------------
            item {
                SectionCard("接口") {
                    OutlinedTextField(
                        value = settings.baseUrl,
                        onValueChange = { v -> vm.updateSettings { it.copy(baseUrl = v) } },
                        label = { Text("API 地址") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.apiKey,
                        onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v) } },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = settings.model,
                            onValueChange = { v -> vm.updateSettings { it.copy(model = v) } },
                            label = { Text("模型") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { vm.refreshModels() }, enabled = !modelsLoading) {
                            Text(if (modelsLoading) "获取中" else "获取列表")
                        }
                    }
                    if (models.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(models.take(60)) { m ->
                                AssistChip(
                                    onClick = { vm.updateSettings { it.copy(model = m) } },
                                    label = { Text(m, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- 密钥池 ----------------
            item {
                SectionCard("API 密钥池") {
                    Text(
                        text = "一行一个 Key，支持 # 开头写注释。池子非空时优先用池子里的，单个 Key 自动忽略。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = settings.apiKeysRaw,
                        onValueChange = { v -> vm.updateSettings { it.copy(apiKeysRaw = v) } },
                        label = { Text("密钥列表") },
                        placeholder = { Text("sk-aaaa…\nsk-bbbb…\n# 备用\nsk-cccc…") },
                        visualTransformation = if (showPool) VisualTransformation.None else PasswordVisualTransformation(),
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showPool = !showPool }) {
                            Text(if (showPool) "隐藏内容" else "显示内容")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "共 ${poolKeys.size} 个",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SwitchRow(
                        title = "自动轮换",
                        subtitle = "每次请求按顺序挑下一个 Key，失败不重试",
                        checked = settings.keyRotation,
                        onChange = { v -> vm.updateSettings { it.copy(keyRotation = v) } }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.testKeys() }, enabled = !keyTesting) {
                            Text(if (keyTesting) "测试中…" else "测试全部")
                        }
                        TextButton(onClick = { vm.clearKeyTests() }, enabled = keyTests.isNotEmpty() && !keyTesting) {
                            Text("清除结果")
                        }
                    }
                    keyTests.forEach { (k, r) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = maskKey(k),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = r,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r == "可用") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ---------------- 智能体 ----------------
            item {
                SectionCard("智能体") {
                    SwitchRow(
                        title = "流式输出",
                        subtitle = "边生成边显示",
                        checked = settings.streamOutput,
                        onChange = { v -> vm.updateSettings { it.copy(streamOutput = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "启用内置工具",
                        subtitle = "时间、计算、设备、网页、记忆、知识库",
                        checked = settings.enableTools,
                        onChange = { v -> vm.updateSettings { it.copy(enableTools = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "原生工具调用",
                        subtitle = "下发 tools 参数；个别网关不支持时关掉",
                        checked = settings.nativeToolCall,
                        onChange = { v -> vm.updateSettings { it.copy(nativeToolCall = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "允许 AI 执行终端命令",
                        subtitle = "在应用沙箱内运行 shell，风险项",
                        checked = settings.enableShellTool,
                        onChange = { v -> vm.updateSettings { it.copy(enableShellTool = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "启用自定义插件",
                        subtitle = "让模型调用你导入的工具",
                        checked = settings.enablePlugins,
                        onChange = { v -> vm.updateSettings { it.copy(enablePlugins = v) } }
                    )
                    StepperRow(
                        title = "工具调用轮次上限",
                        value = settings.toolMaxRounds,
                        range = 1..8,
                        onChange = { v -> vm.updateSettings { it.copy(toolMaxRounds = v) } }
                    )
                    StepperRow(
                        title = "每次带上的历史消息",
                        value = settings.contextLimit,
                        range = 4..80,
                        onChange = { v -> vm.updateSettings { it.copy(contextLimit = v) } }
                    )
                    OutlinedTextField(
                        value = settings.tavilyApiKey,
                        onValueChange = { v -> vm.updateSettings { it.copy(tavilyApiKey = v) } },
                        label = { Text("Tavily API Key（联网搜索，可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider()
                    CollapseHeader(
                        text = if (promptOpen) "收起系统提示词" else "展开系统提示词",
                        onClick = { promptOpen = !promptOpen }
                    )
                    if (promptOpen) {
                        OutlinedTextField(
                            value = settings.systemPrompt,
                            onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 12,
                            label = { Text("系统提示词") }
                        )
                        TextButton(onClick = {
                            vm.updateSettings { it.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) }
                        }) { Text("恢复默认提示词") }
                    }
                }
            }

            // ---------------- 记忆与知识 ----------------
            item {
                SectionCard("记忆与知识") {
                    SwitchRow(
                        title = "自动记住对话",
                        subtitle = "聊完自动把长期信息存进记忆库",
                        checked = settings.autoMemory,
                        onChange = { v -> vm.updateSettings { it.copy(autoMemory = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "回答时带上记忆与知识",
                        subtitle = "把检索到的片段注入对话",
                        checked = settings.injectContext,
                        onChange = { v -> vm.updateSettings { it.copy(injectContext = v) } }
                    )
                    Divider()
                    StepperRow(
                        title = "每轮记忆条数",
                        value = settings.memoryTopK,
                        range = 0..10,
                        onChange = { v -> vm.updateSettings { it.copy(memoryTopK = v) } }
                    )
                    Divider()
                    StepperRow(
                        title = "每轮知识片段数",
                        value = settings.knowledgeTopK,
                        range = 0..10,
                        onChange = { v -> vm.updateSettings { it.copy(knowledgeTopK = v) } }
                    )
                    OutlinedTextField(
                        value = settings.extractModel,
                        onValueChange = { v -> vm.updateSettings { it.copy(extractModel = v) } },
                        label = { Text("记忆抽取用模型（留空＝跟随主模型）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.memoryExtractRules,
                        onValueChange = { v -> vm.updateSettings { it.copy(memoryExtractRules = v) } },
                        label = { Text("抽取附加规则（可选）") },
                        placeholder = { Text("例如：别记日程类临时信息；家人都记全名") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---------------- 生成参数 ----------------
            item {
                SectionCard("生成参数") {
                    CollapseHeader(
                        text = if (paramOpen) "收起参数" else "展开参数（温度、采样、惩罚、长度）",
                        onClick = { paramOpen = !paramOpen }
                    )
                    if (paramOpen) {
                        Divider()
                        SliderRow(
                            title = "温度",
                            text = String.format("%.2f", settings.temperature),
                            value = settings.temperature,
                            range = 0f..2f,
                            onChange = { v -> vm.updateSettings { it.copy(temperature = v) } }
                        )
                        SliderRow(
                            title = "Top-P",
                            text = String.format("%.2f", settings.topP),
                            value = settings.topP,
                            range = 0.1f..1f,
                            onChange = { v -> vm.updateSettings { it.copy(topP = v) } }
                        )
                        SliderRow(
                            title = "频率惩罚",
                            text = String.format("%.2f", settings.frequencyPenalty),
                            value = settings.frequencyPenalty,
                            range = -2f..2f,
                            onChange = { v -> vm.updateSettings { it.copy(frequencyPenalty = v) } }
                        )
                        SliderRow(
                            title = "存在惩罚",
                            text = String.format("%.2f", settings.presencePenalty),
                            value = settings.presencePenalty,
                            range = -2f..2f,
                            onChange = { v -> vm.updateSettings { it.copy(presencePenalty = v) } }
                        )
                        OutlinedTextField(
                            value = tokensText,
                            onValueChange = { v ->
                                tokensText = v.filter { it.isDigit() }.take(6)
                                vm.updateSettings { it.copy(maxTokens = tokensText.toIntOrNull() ?: 0) }
                            },
                            label = { Text("最大回复 tokens（0 表示不限）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---------------- 外观 ----------------
            item {
                SectionCard("外观") {
                    ChipPicker(
                        selected = settings.themeMode,
                        options = listOf(
                            THEME_SYSTEM to "跟随系统",
                            THEME_LIGHT to "浅色",
                            THEME_DARK to "深色"
                        ),
                        onSelect = { key -> vm.updateSettings { it.copy(themeMode = key) } }
                    )
                    SliderRow(
                        title = "字号缩放",
                        text = String.format("%.2f×", settings.fontScale),
                        value = settings.fontScale,
                        range = 0.8f..1.6f,
                        onChange = { v -> vm.updateSettings { it.copy(fontScale = v) } }
                    )
                    SliderRow(
                        title = "抽屉宽度",
                        text = String.format("%.0f%%", settings.drawerRatio * 100),
                        value = settings.drawerRatio,
                        range = 0.3f..0.7f,
                        onChange = { v -> vm.updateSettings { it.copy(drawerRatio = v) } }
                    )
                }
            }

            // ---------------- 显示与行为 ----------------
            item {
                SectionCard("显示与行为") {
                    Text("工具调用折叠方式", style = MaterialTheme.typography.bodyMedium)
                    ChipPicker(
                        selected = settings.collapseMode,
                        options = listOf(
                            COLLAPSE_NONE to "全部展开",
                            COLLAPSE_SINGLE to "只有一个时折叠",
                            COLLAPSE_MULTI to "多个时折叠"
                        ),
                        onSelect = { key -> vm.updateSettings { it.copy(collapseMode = key) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "显示消息时间",
                        subtitle = "每条消息下面标出时间",
                        checked = settings.showTimestamp,
                        onChange = { v -> vm.updateSettings { it.copy(showTimestamp = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "屏幕常亮",
                        subtitle = "对话页不自动息屏",
                        checked = settings.keepScreenOn,
                        onChange = { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
                    )
                    Divider()
                    SwitchRow(
                        title = "回复完成提醒",
                        subtitle = "回答结束弹一条通知",
                        checked = settings.notifyOnDone,
                        onChange = { v ->
                            vm.updateSettings { it.copy(notifyOnDone = v) }
                            if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    Divider()
                    SwitchRow(
                        title = "发送震动反馈",
                        subtitle = "按下发送时轻震一下",
                        checked = settings.hapticOnSend,
                        onChange = { v -> vm.updateSettings { it.copy(hapticOnSend = v) } }
                    )
                }
            }

            // ---------------- 用量统计 ----------------
            item {
                SectionCard("用量统计") {
                    if (usage.isEmpty) {
                        Text(
                            text = "还没有记录。服务端返回 usage 后会自动累计，数据只在本机。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val total = usage.total
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatCell("请求", total.requests.toString(), Modifier.weight(1f))
                            StatCell("输入 tokens", total.promptTokens.toString(), Modifier.weight(1f))
                            StatCell("输出 tokens", total.completionTokens.toString(), Modifier.weight(1f))
                        }
                        Divider()
                        usage.models.entries
                            .sortedByDescending { it.value.totalTokens }
                            .forEach { (name, u) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${u.requests} 次 · ${u.totalTokens} tokens",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "最近：" + usage.lastModel.ifBlank { "—" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { confirmResetStats = true }) { Text("清零") }
                        }
                    }
                }
            }

            // ---------------- 数据 ----------------
            item {
                SectionCard("数据") {
                    TextButton(onClick = { confirmClearMessages = true }) {
                        Text("清空当前会话消息")
                    }
                    TextButton(onClick = { confirmClearConvs = true }) {
                        Text("删除全部会话")
                    }
                }
            }

            // ---------------- 关于 ----------------
            item {
                SectionCard("关于") {
                    Text("知微智能体 v$versionName", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "手机端 AI 智能体：OpenAI 兼容接口 + 密钥池 + 工具调用 + 终端 + 记忆/知识库。" +
                            "对话、记忆、知识、用量全部只存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "github.com/mingxiozsg-star/zhiwei-agent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }

    if (confirmClearConvs) {
        AlertDialog(
            onDismissRequest = { confirmClearConvs = false },
            title = { Text("删除全部会话？") },
            text = { Text("所有本地对话记录都会被清掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllConversations()
                    confirmClearConvs = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearConvs = false }) { Text("取消") }
            }
        )
    }

    if (confirmClearMessages) {
        AlertDialog(
            onDismissRequest = { confirmClearMessages = false },
            title = { Text("清空当前会话？") },
            text = { Text("当前会话里的消息会被清空。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearMessages()
                    confirmClearMessages = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMessages = false }) { Text("取消") }
            }
        )
    }

    if (confirmResetStats) {
        AlertDialog(
            onDismissRequest = { confirmResetStats = false },
            title = { Text("清零用量统计？") },
            text = { Text("只是本机的计数，不影响任何服务端账单。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetStats()
                    confirmResetStats = false
                }) { Text("清零") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetStats = false }) { Text("取消") }
            }
        )
    }
}

/** 掩码显示一个 Key，避免截图泄漏 */
private fun maskKey(k: String): String =
    if (k.length <= 10) k.take(4) + "…" else k.take(6) + "…" + k.takeLast(4)

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CollapseHeader(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text) { detectTapGestures(onTap = { onClick() }) }
    )
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("－") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(38.dp)
        )
        TextButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("＋") }
    }
}

@Composable
private fun SliderRow(
    title: String,
    text: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
private fun ChipPicker(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}
