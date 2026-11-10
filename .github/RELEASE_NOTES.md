## 知微智能体 v1.1.0

这一版把一次对话真正做成"一条消息"，同时补上记忆库、知识库和自定义插件。

**一条消息 = 一个回合**

以前跑一轮下来，界面里会多出好几条记录：你说一句、工具一条、回复一条。现在只剩你说的那一句和知微那一条。

工具调用直接内嵌在这条回复里，写着调用了什么、参数是什么、结果是什么。一次用了多个工具会自动折起来，点一下展开看每一步；还在跑的时候能随时叫停。助手不再显示头像。

**折叠栏改成"挤开"**

底部那三个大页签撤了，全部收进左侧折叠栏的宫格：对话、终端、记忆、知识、插件、设置。折叠栏是半屏宽，展开时把对话页整体推开，而不是盖在上面。

**记忆库**

聊着聊着，它会自己把值得长期记住的事记下来：你的身份、偏好、习惯、在做的项目。

下次开新对话，回答前会先把相关记忆带上——比如你说过一次常用哪个中转接口，新对话里不用再讲一遍。记忆页可以手动增删、置顶，也可以让它现在就从当前对话重新抽一遍。

**知识库**

把自己的资料丢进去（直接粘贴正文，或者只填一个网址让它去抓），回答时按需引用。资料按段落切块，命中什么引什么。

**自定义工具与插件**

可以把现成的插件描述粘进来，也支持 MCP 服务端的工具列表。类型有三种：HTTP 接口、本机命令、MCP 工具。导入后可以先试跑一次看结果，确认没问题再启用。

**其他**

输入栏改成 Agent 风格：左侧加号是快捷入口，回车直接发，忙的时候按钮变停止。终端换成普通终端的样子——黑底等宽、绿字命令、红字报错、底部一个 `$` 提示符。设置页精简，温度和提示词这类参数收进"高级参数"里折起来。

**下载**
- Releases：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速镜像：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## Zhiwei Agent v1.1.0

This release turns one round of talk into one single message, and adds memory, knowledge and custom plugins.

**One message = one turn**

Previously a single round left several entries in the thread: your line, then a tool entry, then a reply. Now there are only your line and Zhiwei's line.

Tool calls are embedded right inside that reply — what was called, with what arguments, and what came back. Several tools in one turn fold into one group; tap to expand each step. You can stop it at any point while it runs. The assistant no longer shows an avatar.

**The drawer now shifts instead of covering**

The three bottom tabs are gone, folded into a grid inside the left drawer: Chat, Terminal, Memory, Knowledge, Plugins, Settings. The drawer is half-width and pushes the chat page aside instead of covering it.

**Memory**

Over time it writes down what is worth keeping: who you are, your preferences, habits, and the projects you are working on.

Open a new conversation and the relevant memories come along — if you once mentioned your usual relay endpoint, you will not have to repeat it. The Memory page lets you add, edit, pin or delete entries, and re-extract from the current conversation on demand.

**Knowledge**

Drop your own material in — paste the text, or just give a URL and let it fetch the page. Answers cite it as needed. Documents are split into paragraphs and only the matching pieces are used.

**Custom tools and plugins**

Paste an existing plugin description, or the tool list of an MCP server. Three kinds are supported: HTTP endpoint, local command, MCP tool. Import, run a test call to see the result, then enable it.

**Also**

The input bar is now agent-style: a plus button on the left for quick actions, Enter to send, and the button turns into Stop while busy. The terminal looks like a real terminal — dark monospace, green commands, red errors, a `$` prompt at the bottom. Settings are trimmed, with temperature and prompt tucked into a collapsible "Advanced" section.

**Download**
- Releases: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
