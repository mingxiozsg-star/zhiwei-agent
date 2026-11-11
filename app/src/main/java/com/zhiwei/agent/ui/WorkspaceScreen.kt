package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.WorkspaceEntry
import com.zhiwei.agent.readableSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工作区：智能体自己的 home 目录。
 * 终端里跑命令、AI 写出来的文件都落在这儿，这里只是把它列出来看。
 */
@Composable
fun WorkspaceScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val files by vm.workspace.collectAsState()
    val preview by vm.workspacePreview.collectAsState()
    val dir = remember { vm.workspaceDir() }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }

    LaunchedEffect(Unit) { vm.refreshWorkspace() }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                    }
                } else {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("工作区", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = vm.workspaceDir(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.refreshWorkspace() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        if (files.isEmpty()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "工作区还是空的",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "这里对应终端的工作目录。让知微在终端里建个文件，或者让它把结果写进来，" +
                        "就会出现在这儿。\n路径：$dir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(files, key = { it.path }) { f ->
                    WorkspaceRow(
                        entry = f,
                        onOpen = { vm.openWorkspaceFile(f) },
                        onDelete = { deleteTarget = f }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    val p = preview
    if (p != null) {
        AlertDialog(
            onDismissRequest = { vm.closeWorkspacePreview() },
            title = {
                Text(p.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Text(
                    text = p.second,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.copyToClipboard(p.second)
                    vm.closeWorkspacePreview()
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeWorkspacePreview() }) { Text("关闭") }
            }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${target.name}？") },
            text = { Text("文件会直接从工作区里删掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWorkspaceFile(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WorkspaceRow(
    entry: WorkspaceEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val time = remember(entry.modified) {
        if (entry.modified <= 0) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(entry.modified))
    }
    val badge = remember(entry) {
        if (entry.isDir) "DIR" else entry.name.substringAfterLast('.', "").take(4).uppercase()
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry.path) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onDelete() }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp, 22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.ifBlank { "·" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (!entry.isDir) append(readableSize(entry.size)).append(" · ")
                        append(time)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}