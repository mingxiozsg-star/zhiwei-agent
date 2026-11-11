package com.zhiwei.agent.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.AppScreen
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.ROLE_ASSISTANT
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppScreen) -> Unit = {},
    overlay: AppScreen? = null,
    onToggleOverlay: (AppScreen) -> Unit = {}
) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val settings by vm.settings.collectAsState()
    val memories by vm.memories.collectAsState()
    val knowledge by vm.knowledge.collectAsState()
    val netStatus by vm.netStatus.collectAsState()
    val attachments by vm.attachments.collectAsState()
    val persona by vm.persona.collectAsState()
    val prompts by vm.prompts.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var promptMenuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val current = conversations.firstOrNull { it.id == currentId }

    // 加号 = 上传附件：选完直接复制进工作区，智能体自己就能读
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.attachFiles(uris)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, messages.lastOrNull()?.activities?.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submit() {
        val t = input.trim()
        if (busy) return
        if (t.isEmpty() && attachments.isEmpty()) return
        if (settings.hapticOnSend) {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {
            }
        }
        input = ""
        vm.send(t)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = current?.title ?: persona.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = settings.model.ifBlank { "未设置模型" } +
                                " · 记忆 ${memories.size} · 知识 ${knowledge.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    // 终端与工作区：点了直接盖在这一层上，再点一下收起，不跳页面
                    IconButton(onClick = { onToggleOverlay(AppScreen.TERMINAL) }) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "终端",
                            tint = if (overlay == AppScreen.TERMINAL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { onToggleOverlay(AppScreen.WORKSPACE) }) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "工作区",
                            tint = if (overlay == AppScreen.WORKSPACE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名会话") },
                                onClick = {
                                    menuOpen = false
                                    renameText = current?.title ?: ""
                                    renameOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("角色卡") },
                                onClick = {
                                    menuOpen = false
                                    onNavigate(AppScreen.ROLE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出 Markdown") },
                                onClick = {
                                    menuOpen = false
                                    val md = vm.exportCurrent()
                                    if (md.isBlank()) {
                                        Toast.makeText(context, "当前会话是空的", Toast.LENGTH_SHORT).show()
                                    } else {
                                        try {
                                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                                            val f = File(dir, "zhiwei-chat-${System.currentTimeMillis()}.md")
                                            f.writeText(md)
                                            vm.copyToClipboard(md)
                                            Toast.makeText(
                                                context,
                                                "已导出并复制：${f.absolutePath}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("快捷短语") },
                                onClick = {
                                    menuOpen = false
                                    promptMenuOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空消息") },
                                onClick = {
                                    menuOpen = false
                                    vm.clearMessages()
                                }
                            )
                        }
                        // 快捷短语：原来挂在输入栏的 ＋ 上；加号改成上传附件后挪到这里，不再多加一个入口
                        DropdownMenu(
                            expanded = promptMenuOpen,
                            onDismissRequest = { promptMenuOpen = false }
                        ) {
                            if (prompts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("提示词库还是空的") },
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                            prompts.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = p.title.ifBlank { p.text.take(12) },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        promptMenuOpen = false
                                        val now = SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm EEEE",
                                            Locale.getDefault()
                                        ).format(Date())
                                        val body = p.text
                                            .replace("{time}", now)
                                            .replace("{model}", settings.model)
                                        input = if (input.isBlank()) {
                                            body
                                        } else {
                                            input.trimEnd() + "\n" + body
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("管理提示词") },
                                onClick = {
                                    promptMenuOpen = false
                                    onNavigate(AppScreen.SETTINGS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空输入") },
                                onClick = {
                                    promptMenuOpen = false
                                    input = ""
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EmptyChatHint(greeting = persona.greeting, onSuggestion = { vm.send(it) })
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { m ->
                        MessageItem(
                            message = m,
                            onCopy = { vm.copyToClipboard(it) },
                            onDelete = { vm.deleteMessage(m.id) },
                            onRegenerate = if (m.role == ROLE_ASSISTANT) ({ vm.regenerate() }) else null,
                            showTimestamp = settings.showTimestamp,
                            collapseMode = settings.collapseMode
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // 待发送的附件
                    if (attachments.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(attachments, key = { it.id }) { a ->
                                AttachmentChip(
                                    name = a.name,
                                    onRemove = { vm.removeAttachment(a.id) }
                                )
                            }
                        }
                    }

                    // 重试进度：服务商报错时会显示「第 2/6 次重试」
                    val status = netStatus
                    if (!status.isNullOrBlank()) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 12.dp, top = 6.dp)
                        )
                    }

                    // 上一次失败/中途停下：给一个直接的「继续」，不用自己打两个字
                    val lastMsg = messages.lastOrNull()
                    if (!busy && lastMsg != null && lastMsg.role == ROLE_ASSISTANT && lastMsg.isError) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "这一轮没做完",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { vm.continueRun() }) { Text("继续") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.regenerate() }) { Text("重答") }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { pickFiles.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "上传附件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                if (input.isEmpty()) {
                                    Text(
                                        text = "给${persona.name}发消息…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                BasicTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 6,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { submit() })
                                )
                            }
                        }

                        FilledIconButton(
                            onClick = {
                                if (busy) vm.stopGenerating() else submit()
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (busy) Icons.Default.Clear else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (busy) "停止" else "发送"
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("标题") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentId?.let { vm.renameConversation(it, renameText) }
                    renameOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("取消") }
            }
        )
    }
}

/** 待发送附件：文件名 + 去掉 */
@Composable
private fun AttachmentChip(name: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "移除附件",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onRemove() },
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}