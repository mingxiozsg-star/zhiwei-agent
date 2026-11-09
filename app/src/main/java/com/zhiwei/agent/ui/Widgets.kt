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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.agent.ToolRegistry
import com.zhiwei.agent.data.ChatMessage
import com.zhiwei.agent.data.ROLE_ASSISTANT
import com.zhiwei.agent.data.ROLE_TOOL
import com.zhiwei.agent.data.ROLE_USER

val BrandPink = Color(0xFFFF6FA8)
val BrandBlue = Color(0xFF5FB8FF)

@Composable
fun ZhiweiAvatar(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(BrandPink, BrandBlue))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "知",
            color = Color.White,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 消息行：用户靠右，助手靠左，工具结果单独成卡 */
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
                    .widthIn(max = 300.dp)
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

        ROLE_ASSISTANT -> Row(modifier = Modifier.fillMaxWidth()) {
            ZhiweiAvatar(size = 30.dp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { menu = true })
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.toolCalls.isNotEmpty()) {
                        message.toolCalls.forEach { call ->
                            ToolCallRow(label = ToolRegistry.labelOf(call.name), argument = call.arguments)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    when {
                        message.content.isNotBlank() -> MarkdownText(
                            text = message.content,
                            onCopyCode = onCopy
                        )

                        message.streaming -> TypingDots()
                        else -> Text(
                            text = "（无内容）",
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
                            text = "出错了，可以检查设置里的接口地址与 API Key。",
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

        ROLE_TOOL -> {
            var expanded by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(38.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(message.id) {
                            detectTapGestures(onTap = { expanded = !expanded })
                        }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🛠 " + ToolRegistry.labelOf(message.toolName ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (expanded) "收起" else "展开",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (expanded) message.content else message.content.lineSequence().firstOrNull()?.take(80) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
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

@Composable
fun ToolCallRow(label: String, argument: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = "调用工具：$label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (argument.isNotBlank() && argument != "{}") {
                Text(
                    text = argument.take(160),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

@Composable
fun EmptyChatHint(onSuggestion: (String) -> Unit) {
    val samples = listOf(
        "现在几点了？",
        "帮我算一下 (128+72)*3/8",
        "看看这台手机的型号和电量",
        "搜一下今天有什么科技新闻"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZhiweiAvatar(size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "知微待命中",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "先在设置里填好接口地址与 API Key，就能开始聊了。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
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