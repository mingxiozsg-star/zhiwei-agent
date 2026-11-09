package com.zhiwei.agent.ui

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.ROLE_ASSISTANT
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit
) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val settings by vm.settings.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val current = conversations.firstOrNull { it.id == currentId }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = current?.title ?: "知微智能体",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = settings.model.ifBlank { "未设置模型" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "会话列表")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
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
                                text = { Text("清空消息") },
                                onClick = {
                                    menuOpen = false
                                    vm.clearMessages()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除会话") },
                                onClick = {
                                    menuOpen = false
                                    currentId?.let { vm.deleteConversation(it) }
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
                    EmptyChatHint(onSuggestion = { vm.send(it) })
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
                            onRegenerate = if (m.role == ROLE_ASSISTANT) ({ vm.regenerate() }) else null
                        )
                    }
                }
            }

            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么…") },
                        maxLines = 5,
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (busy) {
                                vm.stopGenerating()
                            } else {
                                val t = input
                                input = ""
                                vm.send(t)
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (busy) Icons.Default.Clear else Icons.Default.Send,
                            contentDescription = if (busy) "停止" else "发送"
                        )
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