package com.zhiwei.agent.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun SettingsScreen(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()

    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    var tokensText by remember { mutableStateOf(if (settings.maxTokens > 0) settings.maxTokens.toString() else "") }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearMessages by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置", style = MaterialTheme.typography.titleMedium) })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        TextButton(
                            onClick = { vm.refreshModels() },
                            enabled = !modelsLoading
                        ) {
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
                SectionCard("生成参数") {
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
                        label = { Text("最大回复 tokens（留空或 0 表示不限制）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "工具调用轮次上限：${settings.toolMaxRounds}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.toolMaxRounds.toFloat(),
                        onValueChange = { v -> vm.updateSettings { it.copy(toolMaxRounds = v.toInt()) } },
                        valueRange = 1f..8f,
                        steps = 6
                    )
                }
            }

            item {
                SectionCard("行为") {
                    SwitchRow(
                        title = "流式输出",
                        subtitle = "边生成边显示，关闭后等待完整回复",
                        checked = settings.streamOutput,
                        onChange = { v -> vm.updateSettings { it.copy(streamOutput = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = "启用工具",
                        subtitle = "允许模型调用时间、计算、设备信息、网页等内置工具",
                        checked = settings.enableTools,
                        onChange = { v -> vm.updateSettings { it.copy(enableTools = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = "允许 AI 执行终端命令",
                        subtitle = "风险项：开启后模型可以在应用沙箱内运行 shell 命令",
                        checked = settings.enableShellTool,
                        onChange = { v -> vm.updateSettings { it.copy(enableShellTool = v) } }
                    )
                    OutlinedTextField(
                        value = settings.tavilyApiKey,
                        onValueChange = { v -> vm.updateSettings { it.copy(tavilyApiKey = v) } },
                        label = { Text("Tavily API Key（用于联网搜索，可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("系统提示词") {
                    OutlinedTextField(
                        value = settings.systemPrompt,
                        onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 12,
                        label = { Text("开局人设与规则") }
                    )
                    TextButton(onClick = { vm.updateSettings { it.copy(systemPrompt = com.zhiwei.agent.data.DEFAULT_SYSTEM_PROMPT) } }) {
                        Text("恢复默认")
                    }
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
                SectionCard("数据") {
                    TextButton(onClick = { confirmClearMessages = true }) {
                        Text("清空当前会话消息")
                    }
                    TextButton(onClick = { confirmClear = true }) {
                        Text("删除全部会话")
                    }
                }
            }

            item {
                SectionCard("关于") {
                    Text("知微智能体 v$versionName", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "本地运行的手机端 AI 智能体：自带 OpenAI 兼容接口客户端、工具调用与终端。" +
                            "对话数据只保存在本机。",
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

            item { Spacer(Modifier.padding(bottom = 20.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("删除全部会话？") },
            text = { Text("所有本地对话记录都会被清掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllConversations()
                    confirmClear = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
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