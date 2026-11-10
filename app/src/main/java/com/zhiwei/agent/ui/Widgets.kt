package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.data.ACT_DONE
import com.zhiwei.agent.data.ACT_ERROR
import com.zhiwei.agent.data.ACT_RUNNING
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_USER
import com.zhiwei.agent.data.ToolActivity

val BrandPink = Color(0xFFFF6FA8)
val BrandBlue = Color(0xFF5FB8FF)

/**
 * 一条消息 = 一个回合。
 * 工具调用内嵌在助手气泡里，不再另起消息、不显示头像。
 */
@Composable
fun MessageItem(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }

    when (message.role) {
        ROLE_USER -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { menu = true })
                    }
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            MessageMenu(
                expanded = menu,
                onDismiss = { menu = false },
                onCopy = { onCopy(message.content) },
                onDelete = onDelete,
                onRegenerate = null
            )
        }

        ROLE_ASSISTANT -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(message.id) {
                    detectTapGestures(onLongPress = { menu = true })
                }
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (message.activities.isNotEmpty()) {
                        ToolActivityGroup(messageId = message.id, activities = message.activities)
                        if (message.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    when {
                        message.content.isNotBlank() -> MarkdownText(
                            text = message.content,
                            onCopyCode = onCopy
                        )

                        message.streaming && message.activities.isEmpty() -> TypingDots()
                        message.streaming -> Unit
                        else -> Text(
                            text = "（没有内容）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (message.streaming && message.content.isNotBlank()) {
                        TypingDots()
                    }
                    if (message.isError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "出错了，检查设置里的接口地址与 API Key。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            MessageMenu(
                expanded = menu,
                onDismiss = { menu = false },
                onCopy = { onCopy(message.content) },
                onDelete = onDelete,
                onRegenerate = onRegenerate
            )
        }

        else -> Unit
    }
}

/** 工具调用组：单个直接展示，多个先折叠，点开看每一次的详情 */
@Composable
fun ToolActivityGroup(messageId: String, activities: List<ToolActivity>) {
    if (activities.isEmpty()) return

    if (activities.size == 1) {
        ToolActivityCard(activities.first())
        return
    }

    var open by remember(messageId) { mutableStateOf(false) }
    val running = activities.count { it.status == ACT_RUNNING }
    val failed = activities.count { it.status == ACT_ERROR }
    val summary = when {
        running > 0 -> "正在执行 ${activities.size} 个工具…"
        failed > 0 -> "调用了 ${activities.size} 个工具（${failed} 个失败）"
        else -> "调用了 ${activities.size} 个工具"
    }

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(messageId) {
                detectTapGestures(onTap = { open = !open })
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛠", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (open) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                activities.forEachIndexed { index, a ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    ToolActivityCard(a)
                }
            }
        }
    }
}

/** 单次工具调用：名称 + 参数 + 结果，点一下展开完整结果 */
@Composable
fun ToolActivityCard(activity: ToolActivity) {
    var showArgs by remember(activity.id) { mutableStateOf(false) }
    var showResult by remember(activity.id) { mutableStateOf(false) }
    val hasResult = activity.result.isNotBlank()
    val argText = activity.arguments.trim()
    val showArgBlock = argText.isNotEmpty() && argText != "{}" && argText != "null"

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(activity.id) {
                detectTapGestures(onTap = {
                    if (hasResult) showResult = !showResult else showArgs = !showArgs
                })
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor(activity.status))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "🛠 " + activity.label.ifBlank { activity.name },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = statusText(activity.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(activity.status)
                )
            }

            if (showArgBlock) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (showResult) argText else argText.take(120),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showResult) 12 else 2
                )
                if (!hasResult && argText.length > 120) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (showArgs) "收起参数" else "展开完整参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when {
                hasResult -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (showResult) activity.result else previewOf(activity.result),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (showResult) 400 else 4
                    )
                    if (activity.result.length > 140) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (showResult) "收起结果" else "展开完整结果",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                activity.status == ACT_RUNNING -> {
                    Spacer(Modifier.height(4.dp))
                    TypingDots()
                }
            }
        }
    }
}

private fun previewOf(text: String): String =
    text.lineSequence()
        .filter { it.isNotBlank() }
        .take(4)
        .joinToString("\n")
        .take(320)

private fun statusText(status: String): String = when (status) {
    ACT_RUNNING -> "进行中"
    ACT_DONE -> "完成"
    ACT_ERROR -> "失败"
    else -> status
}

private fun statusColor(status: String): Color = when (status) {
    ACT_RUNNING -> BrandBlue
    ACT_ERROR -> Color(0xFFE5484D)
    else -> Color(0xFF30A46C)
}

@Composable
private fun MessageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onDismiss()
                onCopy()
            }
        )
        if (onRegenerate != null) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                onClick = {
                    onDismiss()
                    onRegenerate()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("删除这条") },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

/** 打字指示：三个跳动的点 */
@Composable
fun TypingDots() {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "● ● ●",
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    }
}

/** 空会话提示 */
@Composable
fun EmptyChatHint(onSuggestion: (String) -> Unit) {
    val samples = listOf(
        "现在几点了？",
        "帮我算一下 (128+72)*3/8",
        "看看这台手机的型号和电量",
        "记住我常用的中转接口地址"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "知微待命中",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "先在设置里填好接口地址与 API Key，就能开始聊了。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        samples.forEach { s ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .pointerInput(s) { detectTapGestures(onTap = { onSuggestion(s) }) }
            ) {
                Text(
                    text = s,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** 抽屉里的功能入口图标块 */
@Composable
fun NavTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** 通用小标题 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
    )
}
