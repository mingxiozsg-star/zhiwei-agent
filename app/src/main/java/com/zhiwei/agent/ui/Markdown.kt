package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 极简 Markdown 解析：够用就好，不为一个聊天界面引入整条渲染链 */
sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    object Divider : MdBlock()
}

fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.split("\n")
    val para = StringBuilder()

    fun flush() {
        if (para.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(para.toString().trim()))
            para.setLength(0)
        }
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        if (t.startsWith("```")) {
            flush()
            val lang = t.removePrefix("```").trim()
            val sb = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                sb.append(lines[i]).append('\n')
                i++
            }
            blocks.add(MdBlock.Code(lang, sb.toString().trimEnd()))
            i++
            continue
        }
        when {
            t.isEmpty() -> flush()
            t.startsWith("#") -> {
                flush()
                val level = t.takeWhile { it == '#' }.length.coerceAtMost(6)
                blocks.add(MdBlock.Heading(level, t.drop(level).trim()))
            }

            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                flush()
                blocks.add(MdBlock.Bullet(t.drop(2).trim()))
            }

            Regex("^\\d+[.)]\\s+.*").matches(t) -> {
                flush()
                blocks.add(MdBlock.Bullet(t.replaceFirst(Regex("^\\d+[.)]\\s+"), "")))
            }

            t == "---" || t == "***" || t == "___" -> {
                flush()
                blocks.add(MdBlock.Divider)
            }

            t.startsWith("> ") -> {
                flush()
                blocks.add(MdBlock.Quote(t.drop(2).trim()))
            }

            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(t)
            }
        }
        i++
    }
    flush()
    return blocks
}

/** 行内样式：**粗体**、*斜体*、`代码`、~~删除线~~ */
fun inlineMarkdown(text: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    b.append(text[i]); i++
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > 0) {
                    b.withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    b.append(text[i]); i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    b.withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color(0x22808080)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    b.append(text[i]); i++
                }
            }

            text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    b.append(text[i]); i++
                }
            }

            else -> {
                b.append(text[i]); i++
            }
        }
    }
    return b.toAnnotatedString()
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onCopyCode: (String) -> Unit = {}
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block.lang, block.code, onCopyCode)
                is MdBlock.Heading -> Text(
                    text = inlineMarkdown(block.text),
                    fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 17.sp
                        else -> 16.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                is MdBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "•",
                        modifier = Modifier.width(14.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(
                        Modifier
                            .width(3.dp)
                            .padding(top = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                RoundedCornerShape(2.dp)
                            )
                            .size(width = 3.dp, height = 20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MdBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                is MdBlock.Paragraph -> Text(
                    inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onCopy(code) }) {
                Text("复制", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        )
    }
}