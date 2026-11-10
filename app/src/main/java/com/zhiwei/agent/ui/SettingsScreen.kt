package com.zhiwei.agent.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.zhiwei.agent.data.THEME_DARK
import com.zhiwei.agent.data.THEME_LIGHT
import com.zhiwei.agent.data.THEME_SYSTEM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onOpenDrawer: () -> Unit = {}) {
    val settings by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()

    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    var tokensText by remember { mutableStateOf(if (settings.maxTokens > 0) settings.maxTokens.toString() else "") }
    var advanced by remember { mutableStateOf(false) }
    var confirmClearConvs by remember { mutableStateOf(false) }
    var confirmClearMessages by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1.0"
    }

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
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
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
                    OutlinedTextField(
                        value = settings.tavilyApiKey,
                        onValueChange = { v -> vm.updateSettings { it.copy(tavilyApiKey = v) } },
                        label = { Text("Tavily API Key（联网搜索，可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
                }
            }

            item {
                SectionCard("外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            THEME_SYSTEM to "跟随系统",
                            THEME_LIGHT to "浅色",
                            THEME_DARK to "深色"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = settings.themeMode == key,
                                onClick = { vm.updateSettings { it.copy(themeMode = key) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard("高级参数") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(advanced) { detectTapGestures(onTap = { advanced = !advanced }) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (advanced) "收起" else "展开（温度、长度、轮次、提示词）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (advanced) {
                        Divider()
                        Text(
                            text = "温度：" + String.format("%.2f", settings.temperature),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.temperature,
                            onValueChange = { v -> vm.updateSettings { it.copy(temperature = v) } },
                            valueRange = 0f..2f
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
                        StepperRow(
                            title = "工具调用轮次上限",
                            value = settings.toolMaxRounds,
                            range = 1..8,
                            onChange = { v -> vm.updateSettings { it.copy(toolMaxRounds = v) } }
                        )
                        OutlinedTextField(
                            value = settings.systemPrompt,
                            onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 12,
                            label = { Text("系统提示词") }
                        )
                        TextButton(onClick = {
                            vm.updateSettings { it.copy(systemPrompt = com.zhiwei.agent.data.DEFAULT_SYSTEM_PROMPT) }
                        }) { Text("恢复默认提示词") }
                    }
                }
            }

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

            item {
                SectionCard("关于") {
                    Text("知微智能体 v$versionName", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "手机端 AI 智能体：OpenAI 兼容接口 + 工具调用 + 终端 + 记忆/知识库。" +
                            "对话、记忆、知识全部只存在本机。",
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
}

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
            modifier = Modifier.width(28.dp)
        )
        TextButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("＋") }
    }
}