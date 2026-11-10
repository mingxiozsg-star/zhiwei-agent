package com.zhiwei.agent.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.AppScreen
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_USER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 折叠栏：半屏宽度，展开时把对话页整体推开（不是盖在上面）。
 * 上半是功能入口，下半是会话来列表。
 */
@Composable
fun ConversationDrawer(
    conversations: List<Conversation>,
    currentId: String?,
    screen: AppScreen,
    versionName: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = "知微智能体",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 6.dp)
        )
        Text(
            text = "本地运行 · 数据只存本机",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
        )

        Spacer(Modifier.height(10.dp))

        // 功能入口改成竖排列表：图标对齐左侧，不再挤成一堆方块
        DrawerNavRow(
            icon = Icons.Default.Home,
            label = "对话",
            selected = screen == AppScreen.CHAT,
            onClick = { onNavigate(AppScreen.CHAT) }
        )
        DrawerNavRow(
            icon = Icons.Default.Build,
            label = "终端",
            selected = screen == AppScreen.TERMINAL,
            onClick = { onNavigate(AppScreen.TERMINAL) }
        )
        DrawerNavRow(
            icon = Icons.Default.Star,
            label = "记忆库",
            selected = screen == AppScreen.MEMORY,
            onClick = { onNavigate(AppScreen.MEMORY) }
        )
        DrawerNavRow(
            icon = Icons.AutoMirrored.Filled.List,
            label = "知识库",
            selected = screen == AppScreen.KNOWLEDGE,
            onClick = { onNavigate(AppScreen.KNOWLEDGE) }
        )
        DrawerNavRow(
            icon = Icons.Default.Search,
            label = "工具与插件",
            selected = screen == AppScreen.PLUGINS,
            onClick = { onNavigate(AppScreen.PLUGINS) }
        )
        DrawerNavRow(
            icon = Icons.Default.Settings,
            label = "设置",
            selected = screen == AppScreen.SETTINGS,
            onClick = { onNavigate(AppScreen.SETTINGS) }
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话 · ${conversations.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNew, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "新对话",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conv ->
                ConversationRow(
                    conversation = conv,
                    selected = conv.id == currentId,
                    onClick = { onSelect(conv.id) },
                    onRename = { onRename(conv) },
                    onDelete = { onDelete(conv.id) }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp)
        )
    }
}

/** 折叠栏里的单个功能入口：图标在左、文字在右，行高压到最低 */
@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pointerInput(conversation.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { menu = true }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
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
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "会话操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除会话") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
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
    return who + if (text.length > 20) text.take(20) + "…" else text
}