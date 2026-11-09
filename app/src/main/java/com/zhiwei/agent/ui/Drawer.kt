package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_USER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationDrawer(
    conversations: List<Conversation>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZhiweiAvatar(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "知微智能体",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${conversations.size} 个会话 · 本地存储",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onNew() }) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text("新对话", style = MaterialTheme.typography.bodyLarge)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxHeight()) {
            items(conversations, key = { it.id }) { conv ->
                ConversationRow(
                    conversation = conv,
                    selected = conv.id == currentId,
                    onClick = { onSelect(conv.id) },
                    onRename = { onRename(conv) },
                    onDelete = { onDelete(conv.id) }
                )
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .pointerInput(conversation.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { menu = true }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = previewOf(conversation) + " · " + timeFmt.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "会话操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除会话") },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun previewOf(conversation: Conversation): String {
    val last = conversation.messages.lastOrNull() ?: return "还没有消息"
    val who = when (last.role) {
        ROLE_USER -> "我："
        ROLE_ASSISTANT -> "知微："
        else -> ""
    }
    val text = last.content.replace('\n', ' ').trim()
    return who + if (text.length > 24) text.take(24) + "…" else text
}

@Composable
fun DrawerSpacer(conversations: List<Conversation>) {
    // 预留：会话多时用于分组标题，当前保持空实现
    if (conversations.isEmpty()) {
        Spacer(Modifier.height(8.dp))
    }
}