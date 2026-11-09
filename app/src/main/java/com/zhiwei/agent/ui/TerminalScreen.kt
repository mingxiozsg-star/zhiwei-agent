package com.zhiwei.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.agent.ChatViewModel

private val QUICK_COMMANDS = listOf(
    "ls -al",
    "pwd",
    "df -h",
    "getprop ro.product.model",
    "cat /proc/meminfo | head -5",
    "ps -A | head -20",
    "date",
    "id"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: ChatViewModel) {
    val lines by vm.terminalLines.collectAsState()
    val busy by vm.terminalBusy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("终端", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "应用沙箱权限 · 非 root",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { vm.clearTerminal() }) {
                    Icon(Icons.Default.Delete, contentDescription = "清空")
                }
            }
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(QUICK_COMMANDS) { cmd ->
                AssistChip(
                    onClick = { input = cmd },
                    label = { Text(cmd, fontSize = 12.sp) }
                )
            }
        }

        Surface(
            color = Color(0xFF10131A),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (lines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "# 知微终端",
                        color = Color(0xFF7FD1FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "命令在当前应用沙箱内执行，工作目录是应用私有的 home 目录。\n输入命令后点「执行」，或先点上面的快捷命令。",
                        color = Color(0xFF8A93A6),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(lines) { line ->
                        Text(
                            text = line.text,
                            color = when {
                                line.isCommand -> Color(0xFF7FD1FF)
                                line.isError -> Color(0xFFFF8A80)
                                else -> Color(0xFFD6E1EE)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入 shell 命令") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val cmd = input
                        input = ""
                        vm.runCommand(cmd)
                    },
                    enabled = !busy
                ) {
                    Text(if (busy) "执行中" else "执行")
                }
            }
        }
    }
}

private fun Modifier.imePaddingFix(): Modifier = this
