package com.zhiwei.agent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.AppScreen
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.COLLAPSE_MULTI
import com.zhiwei.agent.data.COLLAPSE_NONE
import com.zhiwei.agent.data.COLLAPSE_SINGLE
import com.zhiwei.agent.data.THEME_DARK
import com.zhiwei.agent.data.THEME_LIGHT
import com.zhiwei.agent.data.THEME_SYSTEM
import com.zhiwei.agent.net.REPO_URL
import com.zhiwei.agent.net.UpdateState
import java.util.Locale

// ---------------- ⑥ 主题与字体 ----------------

@Composable
fun ThemeSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()

    SetPage {
        SetSection("主题") {
            SetChipPicker(
                title = "预设主题",
                subtitle = "跟随系统就按手机的深色模式走",
                selected = settings.themeMode,
                options = listOf(
                    THEME_SYSTEM to "跟随系统",
                    THEME_LIGHT to "浅色",
                    THEME_DARK to "深色"
                )
            ) { v -> vm.updateSettings { it.copy(themeMode = v) } }
        }

        SetSection("显示与字号") {
            SetSlider(
                title = "全局字号缩放",
                valueText = String.format(Locale.US, "%.2f×", settings.fontScale),
                value = settings.fontScale,
                range = 0.8f..1.6f
            ) { v -> vm.updateSettings { it.copy(fontScale = v) } }
            SetDivider()
            SetSlider(
                title = "抽屉宽度占屏比",
                valueText = String.format(Locale.US, "%.0f%%", settings.drawerRatio * 100),
                value = settings.drawerRatio,
                range = 0.32f..0.72f
            ) { v -> vm.updateSettings { it.copy(drawerRatio = v) } }
            SetDivider()
            SetSwitch(
                title = "聊天时屏幕常亮",
                subtitle = "等模型回话的时候不自动息屏",
                checked = settings.keepScreenOn
            ) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
        }

        SetSection("外观（路线图）") {
            SetSoon("深色 / AMOLED 纯黑", "纯黑底省电，OLED 屏尤其明显")
            SetSoon("液态玻璃 UI", "毛玻璃卡片与高光边缘")
            SetSoon("动效开关", "给旧机器留一个关动效的口子")
            SetSoon("回弹动画", "列表与气泡的弹性手感")
            SetSoon("自定义字体", "换一套字，字号单独再调")
        }
    }
}

// ---------------- ⑦ 对话管理 ----------------

@Composable
fun ChatSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()
    val conversations by vm.conversations.collectAsState()
    var confirm by remember { mutableStateOf<String?>(null) }

    SetPage {
        SetSection("历史记录") {
            SetInfo(
                title = "会话数",
                value = "${conversations.size} 个",
                subtitle = "全部存在本机，不上传"
            )
            SetDivider()
            SetAction(
                title = "导出当前对话",
                subtitle = "把当前会话导出成文本，复制到剪贴板",
                label = "导出"
            ) { vm.copyToClipboard(vm.exportCurrent()) }
            SetDivider()
            SetAction(
                title = "清空当前会话消息",
                subtitle = "只清这一条对话里的聊天记录",
                label = "清空"
            ) { confirm = "msgs" }
            SetDivider()
            SetAction(
                title = "清空全部会话",
                subtitle = "连同历史一起删掉，删了就找不回来",
                label = "全部清空"
            ) { confirm = "all" }
        }

        SetSection("消息显示") {
            SetSwitch(
                title = "显示消息时间",
                subtitle = "每条气泡下面带一行时间",
                checked = settings.showTimestamp
            ) { v -> vm.updateSettings { it.copy(showTimestamp = v) } }
            SetDivider()
            SetChipPicker(
                title = "工具调用折叠方式",
                subtitle = "一次回复里可能夹着好几步工具调用",
                selected = settings.collapseMode,
                options = listOf(
                    COLLAPSE_NONE to "全部展开",
                    COLLAPSE_SINGLE to "单步直接显示",
                    COLLAPSE_MULTI to "多步折叠"
                )
            ) { v -> vm.updateSettings { it.copy(collapseMode = v) } }
        }

        SetSection("消息操作") {
            SetInfo(
                title = "删除消息",
                value = "长按气泡",
                subtitle = "长按任意一条消息可以单独删掉"
            )
            SetDivider()
            SetInfo(
                title = "重新生成",
                value = "输入栏上方",
                subtitle = "对最后一条回复不满意，直接重新跑一遍"
            )
        }

        SetSection("路线图") {
            SetSoon("消息编辑", "改完再发，不用重新打一遍")
            SetSoon("气泡样式", "多套气泡配色与圆角可选")
        }
    }

    val target = confirm
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (target == "all") "清空全部会话？" else "清空当前会话消息？") },
            text = {
                Text(
                    if (target == "all") {
                        "所有会话和里面的消息都会删掉，这台手机上不留副本。"
                    } else {
                        "只清掉当前这条对话里的消息，其他会话不动。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (target == "all") vm.clearAllConversations() else vm.clearMessages()
                    confirm = null
                }) { Text("确定清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
            }
        )
    }
}

// ---------------- ⑧ 工作区管理 ----------------

@Composable
fun WorkspaceSettingsPage(vm: ChatViewModel, onNavigate: (AppScreen) -> Unit) {
    val workspace by vm.workspace.collectAsState()
    val attachments by vm.attachments.collectAsState()

    LaunchedEffect(Unit) { vm.refreshWorkspace() }

    SetPage {
        SetSection("当前工作区") {
            SetInfo(
                title = "目录",
                value = vm.workspaceDir().substringAfterLast('/'),
                subtitle = vm.workspaceDir()
            )
            SetDivider()
            SetInfo(
                title = "文件数",
                value = "${workspace.size} 个",
                subtitle = if (attachments.isEmpty()) null else "本轮待发附件 ${attachments.size} 个"
            )
            SetDivider()
            SetAction(title = "刷新文件列表", label = "刷新") { vm.refreshWorkspace() }
            SetDivider()
            SetAction(
                title = "打开工作区页",
                subtitle = "看文件、预览内容、删文件",
                label = "打开"
            ) { onNavigate(AppScreen.WORKSPACE) }
        }

        SetSection("文件联动") {
            SetInfo(
                title = "聊天里上传的附件",
                value = "自动进工作区",
                subtitle = "点输入栏的加号选文件，会复制进 uploads/，模型可直接读"
            )
        }

        SetSection("路线图") {
            SetSoon("新建 / 切换 / 删除工作区", "一个项目一个工作区，互不串味")
            SetSoon("版本快照", "改之前先打个快照，随时回滚")
            SetSoon("代码变更对比", "改了什么、差在哪一行")
            SetSoon("一键打包导出", "把工作区打成 zip，或者直接出 APK")
        }
    }
}

// ---------------- ⑨ 联动扩展 ----------------

@Composable
fun LinkSettingsPage(vm: ChatViewModel) {
    SetPage {
        SetSection("已上线的联动") {
            SetInfo(
                title = "系统分享",
                value = "已上线",
                subtitle = "从别的应用把文字或链接分享进知微"
            )
        }

        SetSection("知微系列联动（路线图）") {
            SetSoon("知微浏览器联动", "浏览器里识别的下载链接直接丢进工作区")
            SetSoon("打开下载目录", "从对话里直接跳到文件管理器看产物")
            SetSoon("读取本地目录", "授权一个目录，让模型按需读里面的文件")
            SetSoon("跨应用唤起", "把任务结果送到别的应用继续处理")
        }
    }
}

// ---------------- ⑩ 网络与安全 ----------------

@Composable
fun NetSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()

    SetPage {
        SetSection("当前网络") {
            SetInfo(title = "实际请求地址", value = settings.endpoint())
            SetDivider()
            SetInfo(
                title = "密钥数量",
                value = "${settings.usableKeys().size} 个",
                subtitle = "轮换顺序按填写顺序来"
            )
        }

        SetSection("路线图") {
            SetSoon("代理设置", "给请求挂 HTTP / SOCKS 代理")
            SetSoon("SSL 校验开关", "自签证书的网关要能手动放行")
            SetSoon("自动切换线路", "主线路不通就换备用地址重试")
            SetSoon("请求超时", "连接、读、写三段分开调")
        }
    }
}

// ---------------- ⑪ 通知 ----------------

@Composable
fun NotifySettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val granted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    SetPage {
        SetSection("通知") {
            SetInfo(
                title = "通知权限",
                value = if (granted) "已允许" else "未允许",
                subtitle = if (granted) null else "去系统设置里给知微打开通知权限"
            )
            SetDivider()
            SetSwitch(
                title = "回答完成后提醒我",
                subtitle = "切到别的应用时，回复好了发一条通知",
                checked = settings.notifyOnDone
            ) { v -> vm.updateSettings { it.copy(notifyOnDone = v) } }
            SetDivider()
            SetAction(
                title = "打开系统通知设置",
                subtitle = "权限被拒过时，只能去系统里手动开",
                label = "前往"
            ) { openSystemNotificationSettings(context) }
        }

        SetSection("路线图") {
            SetSoon("消息推送", "长任务跑完直接推一条")
            SetSoon("后台通知进度", "工具执行到第几步也能看见")
            SetSoon("报错提醒", "失败时除了红字再弹一条通知")
            SetSoon("版本更新提醒", "发现新版就提醒，一键跳下载")
        }
    }
}

// ---------------- ⑫ 快捷键与手势 ----------------

@Composable
fun GestureSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()

    SetPage {
        SetSection("手感") {
            SetSwitch(
                title = "发送时轻微震动",
                subtitle = "发消息的时候给一下触感反馈",
                checked = settings.hapticOnSend
            ) { v -> vm.updateSettings { it.copy(hapticOnSend = v) } }
        }

        SetSection("已支持的手势") {
            SetInfo(
                title = "从屏幕左侧往右拖",
                value = "收起抽屉",
                subtitle = "抽屉展开后，在抽屉上往左划一下即可关闭"
            )
            SetDivider()
            SetInfo(
                title = "长按消息",
                value = "删除",
                subtitle = "长按任意气泡弹出操作菜单"
            )
        }

        SetSection("路线图") {
            SetSoon("侧边手势开关", "可以关掉侧滑，避免和系统返回手势打架")
            SetSoon("长按菜单自定义", "自己决定长按气泡出哪些操作")
            SetSoon("快捷指令", "给常用提示词绑一个手势或桌面快捷方式")
        }
    }
}

// ---------------- ⑬ 关于与赞助 ----------------

@Composable
fun AboutSettingsPage(vm: ChatViewModel) {
    val update by vm.update.collectAsState()
    val context = LocalContext.current

    SetPage {
        SetSection("版本") {
            SetInfo(title = "当前版本", value = vm.appVersionName())
            SetDivider()
            SetAction(
                title = "检查更新",
                subtitle = "读 GitHub 最新 Release，和本机版本比一比",
                label = if (update is UpdateState.Checking) "检查中" else "检查",
                enabled = update !is UpdateState.Checking
            ) { vm.checkUpdate() }
            when (val u = update) {
                is UpdateState.Checking -> {
                    SetDivider()
                    SetInfo(title = "状态", value = "正在检查…")
                }

                is UpdateState.Done -> {
                    SetDivider()
                    SetInfo(title = "发现新版本", value = u.info.tag, subtitle = u.info.publishedAt)
                    SetDivider()
                    SetAction(
                        title = "打开下载页",
                        subtitle = "跳到 Release 页面自己挑线路下载",
                        label = "打开"
                    ) { openUrl(context, u.info.pageUrl) }
                    if (u.info.notes.isNotBlank()) {
                        SetDivider()
                        Text(
                            text = u.info.notes.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                is UpdateState.Error -> {
                    SetDivider()
                    SetInfo(title = "检查失败", value = u.message)
                }

                else -> Unit
            }
        }

        SetSection("仓库与交流") {
            SetAction(
                title = "开源仓库",
                subtitle = REPO_URL,
                label = "打开"
            ) { openUrl(context, REPO_URL) }
            SetDivider()
            SetAction(
                title = "资源分享群",
                subtitle = "QQ 1131325287",
                label = "复制群号"
            ) { vm.copyToClipboard("1131325287") }
        }

        SetSection("隐私") {
            Text(
                text = "对话、记忆、知识、工作区文件都只写在这台手机上，" +
                    "除了你自己的模型接口地址，应用不会往别的地方发数据。" +
                    "API Key 也只存在本机设置里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        SetSection("赞助") {
            SetSoon("赞助与打赏", "先不加，等核心功能做扎实再说")
        }
    }
}

// ---------------- 小工具 ----------------

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "打不开这个链接", Toast.LENGTH_SHORT).show()
    }
}

private fun openSystemNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    }
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "打不开系统设置", Toast.LENGTH_SHORT).show()
    }
}