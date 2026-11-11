package com.zhiwei.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.AppScreen
import com.zhiwei.agent.ChatViewModel

/**
 * 设置页的十三个分类。顺序固定，不跟着功能改动乱动；
 * 首页只放「标题 + 图标 + 一行小字简介」，具体开关都收进二级页。
 */
enum class SettingsGroup(
    val title: String,
    val icon: ImageVector,
    val desc: String
) {
    MODEL("模型与 API", Icons.Default.Settings, "主模型选择、API 网关与备用线路、额度消耗、流式输出"),
    PERSONA("智能体人设", Icons.Default.Edit, "系统提示词编辑、人设模板导入导出、角色记忆与上下文"),
    SKILLS("技能 Skills", Icons.Default.Star, "SKILL.md 技能包：浏览仓库、安装、启用禁用与删除"),
    TOOLS("工具与权限", Icons.Default.Build, "内置工具总开关、提权通道、高危命令拦截、权限管控"),
    STATS("使用统计", Icons.Default.DateRange, "消息热力图、今日消耗与累计额度、调用日志明细"),
    THEME("主题与字体", Icons.Default.Face, "预设主题、深色 / AMOLED、液态玻璃外观、字体字号"),
    CHAT("对话管理", Icons.AutoMirrored.Filled.List, "历史保存与导出、批量清空、消息编辑重发、气泡样式"),
    WORKSPACE("工作区管理", Icons.Default.Home, "工作区新建切换、文件联动、版本快照与变更对比"),
    LINK("联动扩展", Icons.Default.Share, "知微浏览器联动、读取本地目录、跨应用唤起"),
    NET("网络与安全", Icons.Default.Lock, "代理设置、SSL 校验、自动切换线路、请求超时"),
    NOTIFY("通知", Icons.Default.Notifications, "消息推送、后台通知、报错提醒、版本更新提醒"),
    GESTURE("快捷键与手势", Icons.Default.MoreVert, "侧边手势、长按菜单、快捷指令"),
    ABOUT("关于与赞助", Icons.Default.Info, "版本信息、更新检测、反馈入口、开源仓库、交流群、隐私政策")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigate: (AppScreen) -> Unit = {}
) {
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    val group = groupName?.let { n -> SettingsGroup.entries.firstOrNull { it.name == n } }

    // 二级页里按返回先回分类列表，而不是直接退出设置
    BackHandler(enabled = group != null) { groupName = null }

    val current = group
    if (current == null) {
        SettingsHome(onOpenDrawer = onOpenDrawer, onOpen = { groupName = it.name })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(current.title, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = { groupName = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (current) {
                SettingsGroup.MODEL -> ModelSettingsPage(vm)
                SettingsGroup.PERSONA -> PersonaSettingsPage(vm, onNavigate)
                SettingsGroup.SKILLS -> SkillsSettingsPage(vm, onNavigate)
                SettingsGroup.TOOLS -> ToolsSettingsPage(vm)
                SettingsGroup.STATS -> StatsSettingsPage(vm)
                SettingsGroup.THEME -> ThemeSettingsPage(vm)
                SettingsGroup.CHAT -> ChatSettingsPage(vm)
                SettingsGroup.WORKSPACE -> WorkspaceSettingsPage(vm, onNavigate)
                SettingsGroup.LINK -> LinkSettingsPage(vm)
                SettingsGroup.NET -> NetSettingsPage(vm)
                SettingsGroup.NOTIFY -> NotifySettingsPage(vm)
                SettingsGroup.GESTURE -> GestureSettingsPage(vm)
                SettingsGroup.ABOUT -> AboutSettingsPage(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHome(onOpenDrawer: () -> Unit, onOpen: (SettingsGroup) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            items(SettingsGroup.entries) { g ->
                SetGroupRow(group = g, onClick = { onOpen(g) })
            }
            item {
                Text(
                    text = "所有设置改完立刻生效，不用点保存；数据只留在这台手机上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
                )
            }
        }
    }
}

/** 分类行：标题 + 图标一行，下面一行小字概括，右侧箭头；点进去才是具体开关 */
@Composable
fun SetGroupRow(group: SettingsGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(7.dp))
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = group.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------- 二级页里共用的控件 ----------------

/** 二级页外壳：整页可滚动，统一左右留白 */
@Composable
fun SetPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun SetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun SetDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SetSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SetStepper(
    title: String,
    subtitle: String? = null,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    suffix: String = "",
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = { onChange((value - step).coerceIn(min, max)) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = if (suffix.isBlank()) value.toString() else "$value$suffix",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 46.dp)
        )
        TextButton(onClick = { onChange((value + step).coerceIn(min, max)) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SetSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SetChipPicker(
    title: String,
    subtitle: String? = null,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { pair ->
                val on = pair.first == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (on) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.clickable { onSelect(pair.first) }
                ) {
                    Text(
                        text = pair.second,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SetTextField(
    title: String,
    value: String,
    subtitle: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SetInfo(title: String, value: String, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

/** 只在二级页里用的行内操作按钮 */
@Composable
fun SetAction(
    title: String,
    subtitle: String? = null,
    label: String = "执行",
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

/** 还没做的功能如实标出来，不装作能用 */
@Composable
fun SetSoon(title: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = "开发中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun SetStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 掩码显示一个 Key，避免截图泄漏 */
fun maskKey(k: String): String =
    if (k.length <= 10) k.take(4) + "…" else k.take(6) + "…" + k.takeLast(4)
