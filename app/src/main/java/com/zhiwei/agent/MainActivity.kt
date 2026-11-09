package com.zhiwei.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.THEME_DARK
import com.zhiwei.agent.data.THEME_LIGHT
import com.zhiwei.agent.ui.ChatScreen
import com.zhiwei.agent.ui.ConversationDrawer
import com.zhiwei.agent.ui.SettingsScreen
import com.zhiwei.agent.ui.TerminalScreen
import com.zhiwei.agent.ui.theme.ZhiweiAgentTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val dark = when (settings.themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            ZhiweiAgentTheme(darkTheme = dark) {
                ZhiweiApp(vm)
            }
        }
    }
}

@Composable
fun ZhiweiApp(vm: ChatViewModel) {
    val conversations by vm.conversations.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val notice by vm.notice.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(0) }

    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(notice) {
        val text = notice
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            vm.clearNotice()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                currentId = currentId,
                onSelect = { id ->
                    vm.selectConversation(id)
                    scope.launch { drawerState.close() }
                    tab = 0
                },
                onNew = {
                    vm.newConversation()
                    scope.launch { drawerState.close() }
                    tab = 0
                },
                onRename = { conv ->
                    renameTarget = conv
                    renameText = conv.title
                },
                onDelete = { id -> vm.deleteConversation(id) }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Send, contentDescription = null) },
                        label = { Text("对话") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        label = { Text("终端") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> ChatScreen(
                        vm = vm,
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )

                    1 -> TerminalScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }

    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("标题") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameConversation(target.id, renameText)
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}