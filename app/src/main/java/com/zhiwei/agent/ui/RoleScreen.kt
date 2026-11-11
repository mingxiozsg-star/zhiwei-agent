package com.zhiwei.agent.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.ChatViewModel

/**
 * 角色卡：智能体的人设，用户自己改。
 * 改完立刻生效 —— 下一回合的系统提示词就会带上新的人格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleScreen(vm: ChatViewModel, onOpenDrawer: () -> Unit = {}) {
    val persona by vm.persona.collectAsState()
    val context = LocalContext.current

    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var resetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色卡", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("身份") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = persona.avatar,
                            onValueChange = { v -> vm.updatePersona { it.copy(avatar = v.take(2)) } },
                            label = { Text("头像") },
                            singleLine = true,
                            modifier = Modifier.width(88.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = persona.name,
                            onValueChange = { v -> vm.updatePersona { it.copy(name = v) } },
                            label = { Text("名字") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = persona.title,
                        onValueChange = { v -> vm.updatePersona { it.copy(title = v) } },
                        label = { Text("一句话身份") },
                        placeholder = { Text("例如：住在你手机里的 AI 智能体") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("性格与说话风格") {
                    OutlinedTextField(
                        value = persona.personality,
                        onValueChange = { v -> vm.updatePersona { it.copy(personality = v) } },
                        label = { Text("人格设定") },
                        placeholder = { Text("例如：直接、干脆，先给结论再补理由；偶尔调侃但不油腻。") },
                        minLines = 4,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "这段会作为「角色卡」拼进系统提示词，优先级高于内置核心人设。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard("额外要求") {
                    OutlinedTextField(
                        value = persona.instructions,
                        onValueChange = { v -> vm.updatePersona { it.copy(instructions = v) } },
                        label = { Text("用户对智能体的补充要求") },
                        placeholder = { Text("例如：回答尽量短；报错先给原因再给修法。") },
                        minLines = 3,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("开场白") {
                    OutlinedTextField(
                        value = persona.greeting,
                        onValueChange = { v -> vm.updatePersona { it.copy(greeting = v) } },
                        label = { Text("空会话时显示的第一句话") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("模板") {
                    Text(
                        text = "导出会把当前角色卡转成一段 JSON，可以复制走备份；导入时粘贴回来即可。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val json = vm.exportPersonaJson()
                            vm.copyToClipboard(json)
                            Toast.makeText(context, "角色卡已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) { Text("导出到剪贴板") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            importText = ""
                            importOpen = true
                        }) { Text("粘贴导入") }
                    }
                    TextButton(onClick = { resetOpen = true }) { Text("恢复默认角色卡") }
                }
            }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("导入角色卡") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("把导出的 JSON 粘进来") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = importText.isNotBlank(),
                    onClick = {
                        if (vm.importPersonaJson(importText)) {
                            Toast.makeText(context, "角色卡已导入", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "这段内容不是有效的角色卡", Toast.LENGTH_SHORT).show()
                        }
                        importOpen = false
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) { Text("取消") }
            }
        )
    }

    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text("恢复默认角色卡？") },
            text = { Text("当前的名字、性格、开场白都会被默认值覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetPersona()
                    resetOpen = false
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { resetOpen = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}