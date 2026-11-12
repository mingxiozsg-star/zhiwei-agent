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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.data.ACT_DONE
import com.zhiwei.agent.data.ACT_ERROR
import com.zhiwei.agent.data.ACT_RUNNING
import com.zhiwei.agent.data.COLLAPSE_MULTI
import com.zhiwei.agent.data.COLLAPSE_NONE
import com.zhiwei.agent.data.COLLAPSE_SINGLE
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
    onRegenerate: (() -> Unit)? = null,
    showTimestamp: Boolean = false,
    collapseMode: String = COLLAPSE_MULTI,
    showReasoning: Boolean = true
) {
    var menu by remember { mutableStateOf(false) }

    when (message.role) {
        ROLE_USER -> Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
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
            if (showTimestamp) TimestampLabel(message.timestamp, Alignment.End)
        }

        ROLE_ASSISTANT -> Column(modifier = Modifier.fillMaxWidth()) {
            Row(
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
                        if (showReasoning && message.reasoning.isNotBlank()) {
                            ReasoningFold(message.reasoning)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (message.activities.isNotEmpty()) {
                            ToolActivityGroup(
                                messageId = message.id,
                                activities = message.activities,
                                collapseMode = collapseMode
                            )
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
            if (showTimestamp) TimestampLabel(message.timestamp, Alignment.Start)
        }

        else -> Unit
    }
}

/** 消息时间：只在设置里打开「显示消息时间」时出现 */
@Composable
private fun TimestampLabel(timestamp: Long, align: Alignment.Horizontal) {
    val text = remember(timestamp) {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = timestamp
        String.format(
            "%02d:%02d",
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE)
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = align
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 工具调用组：平时就是一行摘要，点开才看明细。
 * 折叠方式由设置决定：全部展开 / 只有一个时折叠 / 多个时折叠。
 */
@Composable
fun ToolActivityGroup(
    messageId: String,
    activities: List<ToolActivity>,
    collapseMode: String = COLLAPSE_MULTI
) {
    if (activities.isEmpty()) return

    val folded = when (collapseMode) {
        COLLAPSE_NONE -> false
        COLLAPSE_SINGLE -> activities.size == 1
        else -> activities.size > 1
    }

    if (!folded) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            activities.forEach { ToolActivityCard(it) }
        }
        return
    }

    var open by remember(messageId) { mutableStateOf(false) }
    val running = activities.count { it.status == ACT_RUNNING }
    val failed = activities.count { it.status == ACT_ERROR }
    val names = activities.map { it.label.ifBlank { it.name } }.distinct().take(3).joinToString("、")
    val tone = when {
        failed > 0 -> statusColor(ACT_ERROR)
        running > 0 -> statusColor(ACT_RUNNING)
        else -> statusColor(ACT_DONE)
    }
    val summary = when {
        running > 0 -> "正在执行 ${activities.size} 个工具"
        failed > 0 -> "调用了 ${activities.size} 个工具 · ${failed} 个失败"
        else -> "调用了 ${activities.size} 个工具"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .pointerInput(messageId) {
                detectTapGestures(onTap = { open = !open })
            }
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tone)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (open) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = names,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (open) {
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activities.forEach { ToolActivityCard(it) }
            }
        }
    }
}

/** 单次工具调用：状态点 + 名称一行，参数一行，结果三行；点一下看全部 */
@Composable
fun ToolActivityCard(activity: ToolActivity) {
    var open by remember(activity.id) { mutableStateOf(false) }
    val title = activity.label.ifBlank { activity.name }
    val argText = activity.arguments.trim().let { if (it == "{}" || it == "null") "" else it }
    val result = activity.result.trim()
    val expandable = result.length > 160 || result.count { it == '\n' } > 2 || argText.length > 90

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .pointerInput(activity.id) {
                detectTapGestures(onTap = { open = !open })
            }
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor(activity.status))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText(activity.status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(activity.status)
            )
        }

        if (argText.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = argText,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) 8 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            result.isNotEmpty() -> {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (open) result else previewOf(result),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (open) 400 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            activity.status == ACT_RUNNING -> {
                Spacer(Modifier.height(3.dp))
                TypingDots()
            }
        }

        if (expandable) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (open) "收起" else "展开全部",
                style = MaterialTheme.typography.labelSmall,
                color = BrandBlue
            )
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

/** 空会话提示：优先显示角色卡里的开场白 */
@Composable
fun EmptyChatHint(greeting: String? = null, onSuggestion: (String) -> Unit) {
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
            text = greeting?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "先在设置里填好接口地址与 API Key，就能开始聊了。",
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

/**
 * 思考过程：默认折起来，点一下展开。
 * 模型思维链（reasoning / thinking）只在这里出现，绝不混进正文。
 */
@Composable
fun ReasoningFold(text: String) {
    var open by remember(text) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text) { detectTapGestures(onTap = { open = !open }) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (open) "▾ 思考过程" else "▸ 思考过程",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${text.length} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (open) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
