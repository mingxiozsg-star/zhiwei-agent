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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.KnowledgeItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(vm: ChatViewModel, onOpenDrawer: () -> Unit = {}) {
    val knowledge by vm.knowledge.collectAsState()

    var addOpen by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    var addContent by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }

    var viewing by remember { mutableStateOf<KnowledgeItem?>(null) }
    var viewTitle by remember { mutableStateOf("") }
    var viewContent by remember { mutableStateOf("") }

    var confirmClear by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("知识库", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "共 ${knowledge.size} 份资料 · 回答时按需引用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addTitle = ""
                addContent = ""
                addUrl = ""
                addOpen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加资料")
            }
        }
    ) { padding ->
        if (knowledge.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("知识库还是空的。", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点右下角添加：可以粘一段笔记，也可以只填网址让知微自己去抓正文。存进来的资料会在相关提问时被引用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(knowledge, key = { it.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(item.id) {
                                detectTapGestures(onTap = {
                                    viewing = item
                                    viewTitle = item.title
                                    viewContent = item.content
                                })
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = item.title.ifBlank { "未命名资料" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.content.replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.content.length} 字 · ${item.source.take(28)} · " +
                                        timeFmt.format(Date(item.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { vm.deleteKnowledge(item.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("添加资料") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addTitle,
                        onValueChange = { addTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("标题") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("网页地址（填了就抓这个网页）") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addContent,
                        onValueChange = { addContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        label = { Text("正文（也可以直接粘贴）") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (addUrl.isNotBlank()) {
                        vm.importFromUrl(addUrl, addTitle)
                    } else {
                        vm.addKnowledge(addTitle, addContent)
                    }
                    addOpen = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false }) { Text("取消") }
            }
        )
    }

    val target = viewing
    if (target != null) {
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("编辑资料") },
            text = {
                Column {
                    OutlinedTextField(
                        value = viewTitle,
                        onValueChange = { viewTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("标题") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewContent,
                        onValueChange = { viewContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14,
                        label = { Text("正文") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateKnowledge(target.id, viewTitle, viewContent)
                    viewing = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteKnowledge(target.id); viewing = null }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewing = null }) { Text("取消") }
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空知识库？") },
            text = { Text("所有资料都会被删掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearKnowledge()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}