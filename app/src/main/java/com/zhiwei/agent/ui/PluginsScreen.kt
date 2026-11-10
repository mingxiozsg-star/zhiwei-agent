package com.zhiwei.agent.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.PLUGIN_HTTP
import com.zhiwei.agent.data.PLUGIN_MCP
import com.zhiwei.agent.data.PLUGIN_SHELL
import com.zhiwei.agent.data.PluginTool

private const val BUILTIN_HINT =
    "内置工具（始终可用）：时间、计算、设备信息、剪贴板、打开链接、终端命令、抓网页、联网搜索、记忆读写、知识库读写。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(vm: ChatViewModel, onOpenDrawer: () -> Unit = {}) {
    val plugins by vm.plugins.collectAsState()
    val testResult by vm.pluginTestResult.collectAsState()

    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importMcp by remember { mutableStateOf("") }

    var detail by remember { mutableStateOf<PluginTool?>(null) }
    var testArgs by remember { mutableStateOf("{}") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("工具与插件", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "自定义 ${plugins.size} 个 · 兼容 OpenAI / MCP 格式",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importText = ""
                importMcp = ""
                importOpen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "导入插件")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = BUILTIN_HINT,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (plugins.isEmpty()) {
                item {
                    Text(
                        text = "还没有自定义工具。点右下角导入：可以粘 OpenAI function calling 的 JSON、MCP 服务端返回的 tools 列表，或者一行一个网址。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        onToggle = { vm.togglePlugin(plugin.id) },
                        onDelete = { vm.deletePlugin(plugin.id) },
                        onOpen = {
                            detail = plugin
                            testArgs = "{}"
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("导入自定义工具") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 12,
                        label = { Text("粘贴工具定义（JSON）") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importMcp,
                        onValueChange = { importMcp = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("MCP 服务端地址（可留空）") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importPlugins(importText, importMcp)
                    importOpen = false
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) { Text("取消") }
            }
        )
    }

    val target = detail
    if (target != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(target.name.ifBlank { "自定义工具" }) },
            text = {
                Column {
                    Text(
                        text = kindLabel(target.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (target.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(target.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = target.config,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testArgs,
                        onValueChange = { testArgs = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        label = { Text("试跑参数（JSON）") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.testPlugin(target.id, testArgs)
                    detail = null
                }) { Text("试跑") }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            }
        )
    }

    val result = testResult
    if (result != null) {
        AlertDialog(
            onDismissRequest = { vm.clearPluginTestResult() },
            title = { Text("试跑结果") },
            text = {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 30
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearPluginTestResult() }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun PluginRow(
    plugin: PluginTool,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(plugin.id) { detectTapGestures(onTap = { onOpen() }) }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name.ifBlank { "未命名工具" },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = kindLabel(plugin.kind) +
                        if (plugin.description.isBlank()) "" else " · " + plugin.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "试跑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Switch(checked = plugin.enabled, onCheckedChange = { onToggle() })
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    PLUGIN_HTTP -> "HTTP 接口"
    PLUGIN_SHELL -> "本机命令"
    PLUGIN_MCP -> "MCP 工具"
    else -> kind
}