package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.ChatViewModel

private val TermBg = Color(0xFF0B0E14)
private val TermBar = Color(0xFF11151D)
private val TermText = Color(0xFFD7DEE8)
private val TermDim = Color(0xFF7B8798)
private val TermCmd = Color(0xFF8BE9A1)
private val TermErr = Color(0xFFFF8A80)

/** 普通终端的样子：黑底、等宽、一个提示符，没有卡片和气泡 */
@Composable
fun TerminalScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val lines by vm.terminalLines.collectAsState()
    val busy by vm.terminalBusy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun run() {
        val cmd = input.trim()
        if (cmd.isEmpty() || busy) return
        input = ""
        vm.runCommand(cmd)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermBar)
                .statusBarsPadding()
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", tint = TermText)
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单", tint = TermText)
                }
            }
            Text(
                text = "终端 · sh（应用沙箱）",
                color = TermDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.clearTerminal() }) {
                Icon(Icons.Default.Delete, contentDescription = "清空", tint = TermText)
            }
        }

        if (lines.isEmpty()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                Text(
                    text = "# 知微终端",
                    color = TermCmd,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "命令在当前应用沙箱内执行，工作目录是应用私有的 home。\n输入命令后回车，输出会直接跟在这一行下面。",
                    color = TermDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text,
                        color = when {
                            line.isCommand -> TermCmd
                            line.isError -> TermErr
                            else -> TermText
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermBar)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$",
                color = TermCmd,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = TermText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(TermCmd),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { run() })
            )
            IconButton(onClick = { run() }, enabled = !busy) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "执行",
                    tint = if (busy) TermDim else TermCmd
                )
            }
        }
    }
}