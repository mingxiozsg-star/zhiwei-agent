## 知微智能体 v1.0.0

第一个版本。装在自己手机里的 AI 智能体：聊天、工具调用、终端，数据全在本机。

**聊天**
接任意 OpenAI 兼容接口（OpenAI、DeepSeek、中转站、本地 Ollama 都行）。回复边生成边显示，不想看了一半可以直接停。长按消息可以复制、删除，或者让知微重新生成一次。

**工具**
模型会自己决定什么时候用工具：
- 问"现在几点" → 读手机时间
- 问"算一下 (128+72)*3/8" → 本地算，不靠模型心算
- 问"这台手机什么型号、还有多少电" → 读设备信息
- 给一个网址 → 抓正文、或者直接调起浏览器打开
- 查实时消息 → 联网搜索（需自备 Tavily Key）
- 想让它翻文件、看系统状态 → 终端命令工具（默认关闭，在设置里开）

每一步调用都会在对话里显示出来，结果以可折叠卡片展示，点一下展开全文。

**终端**
底部第二个页签就是终端，命令在应用沙箱内执行。常用命令做成一行快捷按钮，点一下就填进输入框。

**会话**
左侧抽屉管理会话：新建、切换、重命名、删除，也可以把整段对话导出成 Markdown。

**设置**
接口地址、API Key、模型（支持一键获取列表）、温度、tokens 上限、系统提示词、工具开关、主题三态（跟随系统 / 浅色 / 深色）全部可调，改完即生效。

**下载**
- Releases：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速镜像：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## Zhiwei Agent v1.0.0

First release. An AI agent that lives in your phone: chat, tool calling, terminal — all data stays local.

**Chat**
Works with any OpenAI-compatible endpoint (OpenAI, DeepSeek, relays, local Ollama). Replies stream in as they are generated and can be stopped mid-way. Long-press a message to copy, delete, or regenerate.

**Tools**
The model decides when to use them:
- "what time is it" → reads the phone clock
- "work out (128+72)*3/8" → computed locally, not guessed
- "what phone is this, how much battery" → reads device info
- given a URL → fetches the page text, or opens it in your browser
- live facts → web search (bring your own Tavily key)
- browse files or check system state → shell command tool (off by default, enable in Settings)

Every call shows up in the conversation, and results render as collapsible cards — tap to expand.

**Terminal**
The second tab is a terminal; commands run inside the app sandbox. Common commands are one-tap chips that fill the input box.

**Sessions**
The left drawer manages conversations: create, switch, rename, delete, or export the whole thread as Markdown.

**Settings**
Endpoint, API key, model (with one-tap model list), temperature, token cap, system prompt, tool switches and a three-state theme (system / light / dark) — changes apply instantly.

**Download**
- Releases: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
