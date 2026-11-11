package com.zhiwei.agent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhiwei.agent.AppScreen
import com.zhiwei.agent.ChatViewModel
import com.zhiwei.agent.data.DEFAULT_SYSTEM_PROMPT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- ① 模型与 API ----------------

@Composable
fun ModelSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val loading by vm.modelsLoading.collectAsState()
    val keyTests by vm.keyTests.collectAsState()
    val keyTesting by vm.keyTesting.collectAsState()

    SetPage {
        SetSection("接口地址") {
            SetTextField(
                title = "Base URL",
                value = settings.baseUrl,
                subtitle = "官方或中转站都填这里，例如 https://xxx/v1"
            ) { v -> vm.updateSettings { it.copy(baseUrl = v) } }
            SetDivider()
            SetTextField(
                title = "API Key",
                value = settings.apiKey,
                subtitle = "只存在本机；填了密钥池就以密钥池为准"
            ) { v -> vm.updateSettings { it.copy(apiKey = v) } }
            SetDivider()
            SetTextField(
                title = "密钥池（每行一个）",
                value = settings.apiKeysRaw,
                subtitle = "填几个就轮几个，某个 Key 报错自动换下一个",
                singleLine = false,
                minLines = 3
            ) { v -> vm.updateSettings { it.copy(apiKeysRaw = v) } }
            SetDivider()
            SetSwitch(
                title = "失败自动换 Key",
                subtitle = "默认开着；关掉就固定用第一个",
                checked = settings.keyRotation
            ) { v -> vm.updateSettings { it.copy(keyRotation = v) } }
            SetDivider()
            SetAction(
                title = "测试密钥池",
                subtitle = if (keyTesting) "正在逐个测…" else "每个 Key 发一条最小请求，看哪些能用",
                label = if (keyTesting) "测试中" else "开始测",
                enabled = !keyTesting
            ) { vm.testKeys() }
            if (keyTests.isNotEmpty()) {
                SetDivider()
                keyTests.forEach { (k, result) -> SetInfo(maskKey(k), result) }
                SetAction(title = "清掉测试结果", label = "清空") { vm.clearKeyTests() }
            }
        }

        SetSection("模型") {
            val options = remember(models) { models.sorted() }
            ModelPicker(
                title = "主文本模型",
                value = settings.model,
                options = options
            ) { v -> vm.updateSettings { it.copy(model = v) } }
            SetDivider()
            SetAction(
                title = "拉取模型列表",
                subtitle = when {
                    loading -> "正在拉取…"
                    models.isEmpty() -> "从上面的接口地址拿一次可用模型"
                    else -> "已经拿到 ${models.size} 个模型"
                },
                label = if (loading) "拉取中" else "拉取",
                enabled = !loading
            ) { vm.refreshModels() }
            SetDivider()
            SetTextField(
                title = "抽取 / 识图模型",
                value = settings.extractModel,
                subtitle = "记忆抽取用的小模型，留空就跟主模型一样"
            ) { v -> vm.updateSettings { it.copy(extractModel = v) } }
        }

        SetSection("流式输出与容错") {
            SetSwitch(
                title = "SSE 流式输出",
                subtitle = "逐字往外吐；关掉就等整段返回再显示",
                checked = settings.streamOutput
            ) { v -> vm.updateSettings { it.copy(streamOutput = v) } }
            SetDivider()
            SetStepper(
                title = "报错自动重试次数",
                subtitle = "超时 / 限流 / 5xx 会自己重试，0 表示不重试",
                value = settings.retryCount,
                min = 0,
                max = 10,
                suffix = " 次"
            ) { v -> vm.updateSettings { it.copy(retryCount = v) } }
            SetDivider()
            SetSwitch(
                title = "重试完仍失败就自动续跑",
                subtitle = "补一句「继续」接着往下做，不直接把任务掐断",
                checked = settings.autoResumeAfterFailure
            ) { v -> vm.updateSettings { it.copy(autoResumeAfterFailure = v) } }
            SetDivider()
            SetSwitch(
                title = "下发原生 tools 参数",
                subtitle = "个别网关不支持 function calling，那种情况关掉这里",
                checked = settings.nativeToolCall
            ) { v -> vm.updateSettings { it.copy(nativeToolCall = v) } }
            SetDivider()
            SetStepper(
                title = "单轮最多工具调用次数",
                subtitle = "防着模型自己绕圈跑不完",
                value = settings.toolMaxRounds,
                min = 1,
                max = 40,
                suffix = " 轮"
            ) { v -> vm.updateSettings { it.copy(toolMaxRounds = v) } }
        }

        SetSection("额度与生成参数") {
            SetStepper(
                title = "单次回复最大 Token",
                subtitle = "0 表示不限制，由服务端默认值决定",
                value = settings.maxTokens,
                min = 0,
                max = 32768,
                step = 256,
                suffix = ""
            ) { v -> vm.updateSettings { it.copy(maxTokens = v) } }
            SetDivider()
            SetSlider(
                title = "temperature（发散度）",
                valueText = String.format(Locale.US, "%.2f", settings.temperature),
                value = settings.temperature,
                range = 0f..2f
            ) { v -> vm.updateSettings { it.copy(temperature = v) } }
            SetDivider()
            SetSlider(
                title = "top_p（采样范围）",
                valueText = String.format(Locale.US, "%.2f", settings.topP),
                value = settings.topP,
                range = 0f..1f
            ) { v -> vm.updateSettings { it.copy(topP = v) } }
            SetDivider()
            SetSlider(
                title = "frequency_penalty（重复词惩罚）",
                valueText = String.format(Locale.US, "%.2f", settings.frequencyPenalty),
                value = settings.frequencyPenalty,
                range = -2f..2f
            ) { v -> vm.updateSettings { it.copy(frequencyPenalty = v) } }
            SetDivider()
            SetSlider(
                title = "presence_penalty（新话题倾向）",
                valueText = String.format(Locale.US, "%.2f", settings.presencePenalty),
                value = settings.presencePenalty,
                range = -2f..2f
            ) { v -> vm.updateSettings { it.copy(presencePenalty = v) } }
            SetDivider()
            SetInfo(
                title = "本机累计消耗",
                value = "${usageText(vm)} tokens"
            )
        }

        SetSection("联网检索") {
            SetTextField(
                title = "Tavily API Key",
                value = settings.tavilyApiKey,
                subtitle = "填了才能用联网搜索工具，不填就只能靠模型自己的知识"
            ) { v -> vm.updateSettings { it.copy(tavilyApiKey = v) } }
        }

        SetSection("线路与分流（路线图）") {
            SetSoon("多模型分流", "主文本 / 识图 / 代码按消息类型自动切模型")
            SetSoon("多网关备用线路", "一个网关挂了自动切下一个地址")
            SetSoon("单条对话 Token 限额", "超了就停下并提示，不闷头烧额度")
        }
    }
}

/** 模型选择：点开列表挑，也可以自己在上面手填一个 */
@Composable
private fun ModelPicker(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    value.ifBlank { "还没设置" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "选择")
            }
        }
        if (expanded) {
            if (options.isEmpty()) {
                Text(
                    "列表是空的，先点上面的「拉取模型列表」，或者直接手填模型名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(m)
                                    expanded = false
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (m == value) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        SetTextField(
            title = "也可以直接填模型名",
            value = value,
            subtitle = "中转站的模型名花样多，手填更省事"
        ) { onSelect(it) }
    }
}

private fun usageText(vm: ChatViewModel): String {
    val u = vm.usage.value.total
    return "${u.totalTokens}"
}

// ---------------- ② 智能体人设 ----------------

@Composable
fun PersonaSettingsPage(vm: ChatViewModel, onNavigate: (AppScreen) -> Unit) {
    val settings by vm.settings.collectAsState()
    val persona by vm.persona.collectAsState()
    val prompts by vm.prompts.collectAsState()

    SetPage {
        SetSection("角色卡") {
            SetInfo(
                title = "当前人设",
                value = "${persona.avatar} ${persona.name}",
                subtitle = persona.title
            )
            SetDivider()
            SetInfo(
                title = "开场白",
                value = persona.greeting.take(20)
            )
            SetDivider()
            SetAction(
                title = "去编辑角色卡",
                subtitle = "改名字、身份、性格、开场白，还能导入导出人设模板",
                label = "打开"
            ) { onNavigate(AppScreen.ROLE) }
            SetDivider()
            SetAction(
                title = "导出人设 JSON",
                subtitle = "复制到剪贴板，方便备份或搬到别的机器",
                label = "导出"
            ) { vm.copyToClipboard(vm.exportPersonaJson()) }
        }

        SetSection("系统提示词") {
            SetTextField(
                title = "核心提示词",
                value = settings.systemPrompt,
                subtitle = "这是最底层的一段，角色卡和工具清单会在它后面追加",
                singleLine = false,
                minLines = 8
            ) { v -> vm.updateSettings { it.copy(systemPrompt = v) } }
            SetDivider()
            SetAction(
                title = "恢复内置提示词",
                subtitle = "只有你自己改过才会被覆盖，恢复后是 v3 版本",
                label = "恢复"
            ) { vm.updateSettings { it.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) } }
            SetDivider()
            SetAction(title = "复制当前提示词", label = "复制") {
                vm.copyToClipboard(settings.systemPrompt)
            }
        }

        SetSection("上下文与角色记忆") {
            SetStepper(
                title = "每次带上的历史条数",
                subtitle = "越大越贵也越不容易失忆",
                value = settings.contextLimit,
                min = 2,
                max = 100,
                step = 2,
                suffix = " 条"
            ) { v -> vm.updateSettings { it.copy(contextLimit = v) } }
            SetDivider()
            SetSwitch(
                title = "自动记记忆",
                subtitle = "聊完自动抽一条长期事实存进记忆库",
                checked = settings.autoMemory
            ) { v -> vm.updateSettings { it.copy(autoMemory = v) } }
            SetDivider()
            SetSwitch(
                title = "每轮注入记忆与知识",
                subtitle = "回答前先按当前问题检索一遍，命中就塞进提示词",
                checked = settings.injectContext
            ) { v -> vm.updateSettings { it.copy(injectContext = v) } }
            SetDivider()
            SetStepper(
                title = "每轮记忆条数",
                subtitle = "注入提示词里的记忆上限",
                value = settings.memoryTopK,
                min = 0,
                max = 10,
                suffix = " 条"
            ) { v -> vm.updateSettings { it.copy(memoryTopK = v) } }
            SetDivider()
            SetStepper(
                title = "每轮知识条数",
                subtitle = "知识库片段的上限",
                value = settings.knowledgeTopK,
                min = 0,
                max = 10,
                suffix = " 条"
            ) { v -> vm.updateSettings { it.copy(knowledgeTopK = v) } }
            SetDivider()
            SetTextField(
                title = "记忆抽取的额外规则",
                value = settings.memoryExtractRules,
                subtitle = "比如「不要记我的临时任务」，会拼进抽取提示词",
                singleLine = false,
                minLines = 3
            ) { v -> vm.updateSettings { it.copy(memoryExtractRules = v) } }
        }

        SetSection("提示词库（快捷短语）") {
            SetInfo(title = "已存短语", value = "${prompts.size} 条")
            SetDivider()
            prompts.take(8).forEach { p ->
                SetInfo(title = p.title, value = p.text.replace('\n', ' ').take(16))
                SetDivider()
            }
            SetAction(
                title = "恢复默认短语",
                subtitle = "回到总结要点 / 翻译 / 润色 / 写代码 / 列计划这几条",
                label = "恢复"
            ) { vm.resetPrompts() }
        }

        SetSection("路线图") {
            SetSoon("思考过程折叠开关", "现在只折叠工具调用；模型思维链要等接口侧暴露")
        }
    }
}

// ---------------- ③ 技能 Skills ----------------

@Composable
fun SkillsSettingsPage(vm: ChatViewModel, onNavigate: (AppScreen) -> Unit) {
    val settings by vm.settings.collectAsState()
    val plugins by vm.plugins.collectAsState()

    SetPage {
        SetSection("自定义工具（插件）") {
            SetSwitch(
                title = "启用自定义工具",
                subtitle = "关掉之后只留内置工具",
                checked = settings.enablePlugins
            ) { v -> vm.updateSettings { it.copy(enablePlugins = v) } }
            SetDivider()
            SetInfo(
                title = "已安装",
                value = "${plugins.size} 个",
                subtitle = "支持 http / shell / MCP 三种执行方式"
            )
            SetDivider()
            SetAction(
                title = "打开插件与工具页",
                subtitle = "导入插件、试跑、看每个插件的参数",
                label = "打开"
            ) { onNavigate(AppScreen.PLUGINS) }
        }

        SetSection("SKILL.md 技能包（路线图）") {
            SetSoon("技能包格式", "一个文件夹一个技能：SKILL.md 写用法，附脚本")
            SetSoon("技能仓库浏览", "列出仓库里的技能，看了再用")
            SetSoon("安装技能", "从仓库或本地目录装进应用")
            SetSoon("启用 / 禁用技能", "按需开，不用的不占提示词")
            SetSoon("删除技能", "连文件一起清干净")
        }
    }
}

// ---------------- ④ 工具与权限 ----------------

@Composable
fun ToolsSettingsPage(vm: ChatViewModel) {
    val settings by vm.settings.collectAsState()

    SetPage {
        SetSection("内置工具总开关") {
            SetSwitch(
                title = "允许调用工具",
                subtitle = "总闸。关掉之后模型只能靠已有知识回答",
                checked = settings.enableTools
            ) { v -> vm.updateSettings { it.copy(enableTools = v) } }
            SetDivider()
            SetSwitch(
                title = "设备操控类工具",
                subtitle = "音量、亮度、手电筒、震动、开关应用、剪贴板",
                checked = settings.enableDeviceTools
            ) { v -> vm.updateSettings { it.copy(enableDeviceTools = v) } }
            SetDivider()
            SetSwitch(
                title = "沙箱终端工具",
                subtitle = "让模型在应用自己的沙箱里跑命令；默认关着",
                checked = settings.enableShellTool
            ) { v -> vm.updateSettings { it.copy(enableShellTool = v) } }
            SetDivider()
            SetStepper(
                title = "单轮工具调用上限",
                subtitle = "超了就停下来等你发话",
                value = settings.toolMaxRounds,
                min = 1,
                max = 40,
                suffix = " 轮"
            ) { v -> vm.updateSettings { it.copy(toolMaxRounds = v) } }
        }

        SetSection("工具清单") {
            SetInfo(title = "读文件 / 写文件 / 列目录", value = "已上线")
            SetDivider()
            SetInfo(title = "终端命令（沙箱）", value = if (settings.enableShellTool) "已开启" else "已关闭")
            SetDivider()
            SetInfo(title = "设备信息 / 计算器 / 时间", value = "已上线（不占工具预算）")
            SetDivider()
            SetInfo(title = "记忆与知识检索", value = "已上线")
            SetDivider()
            SetInfo(title = "自定义插件与 MCP", value = if (settings.enablePlugins) "已开启" else "已关闭")
        }

        SetSection("提权与权限（路线图）") {
            SetSoon("Shizuku 提权通道", "借用 Shizuku 的权限跑系统级操作")
            SetSoon("Root 提权通道", "有 root 时直接执行 su 命令")
            SetSoon("无障碍适配", "读屏用户走系统确认框，不吃掉焦点")
            SetSoon("高危命令拦截", "rm -rf、格式化这类命令在执行前拦下来")
            SetSoon("文件读写权限管控", "单独限制模型能碰哪些目录")
            SetSoon("插件权限隔离", "给每个插件单独划定可访问范围")
        }
    }
}

// ---------------- ⑤ 使用统计 ----------------

@Composable
fun StatsSettingsPage(vm: ChatViewModel) {
    val usage by vm.usage.collectAsState()
    val total = usage.total
    val timeText = if (usage.lastAt > 0) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(usage.lastAt))
    } else {
        "还没用过"
    }

    SetPage {
        SetSection("总览") {
            Row(modifier = Modifier.fillMaxWidth()) {
                SetStat(label = "请求次数", value = "${total.requests}")
                Spacer(Modifier.width(18.dp))
                SetStat(label = "输入 tokens", value = "${total.promptTokens}")
                Spacer(Modifier.width(18.dp))
                SetStat(label = "输出 tokens", value = "${total.completionTokens}")
            }
            Spacer(Modifier.height(12.dp))
            SetInfo(title = "合计 tokens", value = "${total.totalTokens}")
            SetDivider()
            SetInfo(title = "最近一次", value = timeText, subtitle = usage.lastModel.ifBlank { null })
        }

        SetSection("按模型拆分") {
            if (usage.models.isEmpty()) {
                Text(
                    "还没有记录。服务端返回 usage 时才会记一笔，本地不做估算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                usage.models.entries.toList().forEachIndexed { index, (name, u) ->
                    if (index > 0) SetDivider()
                    SetInfo(
                        title = name,
                        value = "${u.requests} 次 · ${u.totalTokens} tokens",
                        subtitle = "输入 ${u.promptTokens} / 输出 ${u.completionTokens}"
                    )
                }
            }
        }

        SetSection("清零") {
            SetAction(
                title = "清空用量统计",
                subtitle = "只清这台手机上的累计数，不影响服务商账单",
                label = "清空"
            ) { vm.resetStats() }
        }

        SetSection("路线图") {
            SetSoon("消息热力图", "要按天记消息数，统计结构得先升级")
            SetSoon("今日消耗", "按天汇总输入 / 输出 tokens")
            SetSoon("调用日志", "每次请求的耗时、状态码、重试次数")
        }
    }
}