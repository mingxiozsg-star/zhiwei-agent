package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.ChatViewModel

private val TermBg = Color(0xFF0B0E14)
private val TermBar = Color(0xFF11151D)
private val TermText = Color(0xFFD7DEE8)
private val TermDim = Color(0xFF7B8798)
private val TermCmd = Color(0xFF8BE9A1)
private val TermErr = Color(0xFFFF8A80)
private val TermCwd = Color(0xFF7FB8FF)
private val TermChip = Color(0xFF1A2130)

/** 常用命令：点一下就填进输入行，不用一个字一个字敲 */
private val QUICK_COMMANDS = listOf(
    "ls -la",
    "pwd",
    "cd ..",
    "df -h",
    "free -m",
    "ps -A",
    "du -sh .",
    "getprop ro.build.version.release",
    "ip addr",
    "cat /proc/cpuinfo",
    "date",
    "help"
)

/** 普通终端的样子：黑底、等宽、一个提示符，没有卡片和气泡 */
@Composable
fun TerminalScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val lines by vm.terminalLines.collectAsState()
    val busy by vm.terminalBusy.collectAsState()
    val cwd by vm.terminalCwd.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (cwd.isBlank()) vm.terminalHome()
    }

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
        // ── 顶栏：提示符信息 + 操作
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "终端 · sh（应用沙箱）",
                    color = TermDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    text = cwd.ifBlank { "~" },
                    color = TermCwd,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { vm.copyToClipboard(cwd) }
                )
            }
            if (busy) {
                IconButton(onClick = { vm.interruptCommand() }) {
                    Icon(Icons.Default.Close, contentDescription = "中断", tint = TermErr)
                }
            }
            IconButton(onClick = { vm.restartTerminalSession() }) {
                Icon(Icons.Default.Refresh, contentDescription = "重开会话", tint = TermText)
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.Delete, contentDescription = "更多", tint = TermText)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("清空屏幕") },
                    onClick = {
                        menuOpen = false
                        vm.clearTerminal()
                    }
                )
                DropdownMenuItem(
                    text = { Text("复制当前目录") },
                    onClick = {
                        menuOpen = false
                        vm.copyToClipboard(cwd)
                    }
                )
                DropdownMenuItem(
                    text = { Text("重开会话（回到 home）") },
                    onClick = {
                        menuOpen = false
                        vm.restartTerminalSession()
                    }
                )
            }
        }

        // ── 常用命令条
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermBar)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(QUICK_COMMANDS) { cmd ->
                Surface(
                    color = TermChip,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable(enabled = !busy) {
                        input = cmd
                    }
                ) {
                    Text(
                        text = cmd,
                        color = TermText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
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
                    text = "命令在应用自己的沙箱里跑，工作目录是应用私有的 home。\n" +
                        "这是一个常驻会话：cd 换目录、export 设变量都会留住，跟真终端一样。\n" +
                        "命令跑不完就点顶栏的 ✕ 中断，或者用 ↻ 重开会话。",
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
                        lineHeight = 17.sp,
                        modifier = Modifier.clickable { vm.copyToClipboard(line.text) }
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