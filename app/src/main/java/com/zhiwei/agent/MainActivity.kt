package com.zhiwei.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiwei.agent.data.Conversation
import com.zhiwei.agent.data.THEME_DARK
import com.zhiwei.agent.data.THEME_LIGHT
import com.zhiwei.agent.ui.ChatScreen
import com.zhiwei.agent.ui.ConversationDrawer
import com.zhiwei.agent.ui.KnowledgeScreen
import com.zhiwei.agent.ui.MemoryScreen
import com.zhiwei.agent.ui.PluginsScreen
import com.zhiwei.agent.ui.RoleScreen
import com.zhiwei.agent.ui.SettingsScreen
import com.zhiwei.agent.ui.TerminalScreen
import com.zhiwei.agent.ui.WorkspaceScreen
import com.zhiwei.agent.ui.theme.ZhiweiAgentTheme
import kotlin.math.roundToInt

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
    val settings by vm.settings.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }

    var screenName by rememberSaveable { mutableStateOf(AppScreen.CHAT.name) }
    val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.CHAT)
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // 覆盖层：终端 / 工作区盖在对话页上面，不跳页；再点一次同一个按钮就收起
    var overlayName by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = overlayName?.let { n -> AppScreen.entries.firstOrNull { it.name == n } }

    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.4.0"
    }

    val view = LocalView.current
    LaunchedEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
    }

    LaunchedEffect(notice) {
        val text = notice
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            vm.clearNotice()
        }
    }

    BackHandler(enabled = drawerOpen || overlay != null) {
        if (drawerOpen) drawerOpen = false else overlayName = null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val ratio = settings.drawerRatio.coerceIn(0.32f, 0.72f)
        val drawerWidth = maxWidth * ratio
        val drawerWidthPx = with(density) { drawerWidth.toPx() }

        val contentShift by animateFloatAsState(
            targetValue = if (drawerOpen) drawerWidthPx else 0f,
            animationSpec = tween(durationMillis = 260),
            label = "contentShift"
        )
        val drawerShift by animateFloatAsState(
            targetValue = if (drawerOpen) 0f else -drawerWidthPx,
            animationSpec = tween(durationMillis = 260),
            label = "drawerShift"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // 主内容：被折叠栏“挤开”，不覆盖
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(contentShift.roundToInt(), 0) }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (screen) {
                            AppScreen.CHAT -> ChatScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true },
                                onNavigate = {
                                    overlayName = null
                                    screenName = it.name
                                    drawerOpen = false
                                },
                                overlay = overlay,
                                onToggleOverlay = { target ->
                                    overlayName = if (overlayName == target.name) null else target.name
                                }
                            )
                            AppScreen.TERMINAL -> TerminalScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )

                            AppScreen.WORKSPACE -> WorkspaceScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )


                            AppScreen.MEMORY -> MemoryScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )

                            AppScreen.KNOWLEDGE -> KnowledgeScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )

                            AppScreen.PLUGINS -> PluginsScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )

                            AppScreen.SETTINGS -> SettingsScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true },
                                onNavigate = {
                                    overlayName = null
                                    screenName = it.name
                                    drawerOpen = false
                                }
                            )

                            AppScreen.ROLE -> RoleScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )
                        }

                        // 覆盖层：终端 / 工作区盖在对话页上，顶栏左侧按钮收起
                        val ov = overlay
                        if (ov != null && screen == AppScreen.CHAT) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when (ov) {
                                    AppScreen.TERMINAL -> TerminalScreen(
                                        vm = vm,
                                        onOpenDrawer = { drawerOpen = true },
                                        onClose = { overlayName = null }
                                    )

                                    AppScreen.WORKSPACE -> WorkspaceScreen(
                                        vm = vm,
                                        onOpenDrawer = { drawerOpen = true },
                                        onClose = { overlayName = null }
                                    )

                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }

            // 点击内容区关闭折叠栏
            if (drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(contentShift.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { drawerOpen = false })
                        }
                )
            }

            // 折叠栏本体（半屏，滑入左侧空位）
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(drawerShift.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -14f) drawerOpen = false
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    ConversationDrawer(
                        conversations = conversations,
                        currentId = currentId,
                        screen = screen,
                        versionName = versionName,
                        onSelect = { id ->
                            vm.selectConversation(id)
                            screenName = AppScreen.CHAT.name
                            drawerOpen = false
                        },
                        onNew = {
                            vm.newConversation()
                            screenName = AppScreen.CHAT.name
                            drawerOpen = false
                        },
                        onRename = { conv ->
                            renameTarget = conv
                            renameText = conv.title
                        },
                        onDelete = { id -> vm.deleteConversation(id) },
                        onNavigate = {
                            overlayName = null
                            screenName = it.name
                            drawerOpen = false
                        }
                    )
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