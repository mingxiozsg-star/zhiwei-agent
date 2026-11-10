## 知微智能体 v1.2.0

这一版把设置做细了，重点是「多个 API Key 自动轮换」和「用量统计」。

**API 密钥池**

以前只能填一个 Key，用多了容易被限流。现在设置里多了一块密钥池：一行一个 Key，支持 `#` 开头写注释。

打开自动轮换，每次请求按顺序挑下一个用；池子非空时，上面那个单个 Key 会自动忽略。池子里有几个、能用的有几个，一眼就能看到。

点「测试全部」会逐个打一次最小请求，把结论标在每个 Key 后面——Key 无效、无权限、额度用尽或限流，分开说清楚。截图分享时 Key 只显示前后几位。

**用量统计**

服务端返回多少 token 就记多少，不做本地估算，也不上传。设置页里能看到总请求数、输入和输出 token，下面按模型分开列，最近用的是哪个也标出来。只存在本机，随时可以清零。

**设置改细了**

- 生成参数：温度、Top-P、频率惩罚、存在惩罚、最大回复长度，收在可折叠的「生成参数」里
- 智能体：流式输出、内置工具、原生工具调用（个别网关不吃 `tools` 参数时关掉）、终端命令开关、插件开关、工具轮次上限，新增「每次带上的历史消息条数」
- 记忆与知识：多了「抽取附加规则」，可以写「别记日程类临时信息」这类要求，会拼进抽取提示词
- 显示与行为：工具调用折叠方式（全部展开 / 只有一个时折叠 / 多个时折叠）、显示消息时间、屏幕常亮、回复完成提醒、发送震动
- 外观：主题三选，字号缩放 0.8×–1.6×，抽屉宽度可调
- 端口与生成分开摆，一眼能找到要改的那一项

**工具调用修了两处**

以前模型看不到自己刚调用的工具结果，会出现「计算器已经算出来了，它还接着调三次」。现在同一回合里前面几轮跑过的调用和结果会一并带进上下文，模型看到结果就收手；同一次提问里同参数的工具只真跑一遍，重复要求直接复用上次结果。

工具卡片也压扁了：状态点 + 工具名一行，参数一行，结果最多三行，点一下才展开全部。多个调用折成一行摘要，展开能看到每个工具的名字。

**顺手修的**

修掉了长期存在的类型推断与废弃图标警告；「回复完成提醒」走系统通知渠道，只在打开开关时才申请通知权限。

**下载**
- Releases：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速镜像：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## Zhiwei Agent v1.2.0

This release fleshes out Settings, with two things in front: an API key pool that rotates, and token usage stats.

**API key pool**

Before, there was one key field. Use it enough and you get rate limited. Now there is a pool: one key per line, and lines starting with `#` are comments.

Turn on rotation and each request takes the next key in order. When the pool has anything in it, the single key field above is ignored. How many keys you have and how many work is visible at a glance.

Tap "Test all" and it pings each key once, labelling the result right next to it — invalid key, no permission, quota used up or rate limited are told apart. Keys are masked to a few characters each, so screenshots are safe.

**Usage stats**

Whatever the server reports is what gets counted — no local guessing, nothing uploaded. Settings shows total requests, input and output tokens, a per-model breakdown, and which model you used last. It lives on your phone only and can be cleared anytime.

**Finer-grained settings**

- Generation: temperature, Top-P, frequency penalty, presence penalty, max reply length, tucked into a collapsible section
- Agent: streaming, built-in tools, native tool calls (turn off for gateways that choke on the `tools` parameter), terminal command switch, plugin switch, tool round limit, plus a new "how many past messages to send"
- Memory & knowledge: a new "extra extraction rules" box — write things like "skip temporary schedule items" and it goes into the extraction prompt
- Display & behaviour: tool-call folding (always expanded / fold when single / fold when multiple), show message time, keep screen on, notify when a reply finishes, haptic on send
- Appearance: three theme modes, font scale 0.8×–1.6×, adjustable drawer width

**Two fixes around tool calls**

The model used to lose sight of the result it had just received, which showed up as "the calculator already answered, and it called it three more times". Calls and their results from earlier rounds of the same reply now go back into the context, so the model stops as soon as it has the answer. Within one question, an identical call with identical arguments runs once — repeats reuse the previous result.

Tool cards are flatter too: a status dot and the tool name on one line, arguments on one line, up to three lines of result, and a tap expands everything. Several calls fold into a single summary row that lists the tool names.

**Also fixed**

Cleared out long-standing type-inference and deprecated-icon warnings. The finish notification uses a proper system channel and only asks for permission when you turn it on.

**Download**
- Releases: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287