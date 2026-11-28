# 知微智能体 · Zhiwei Agent

> A local-first AI agent for Android. 本地运行的手机端 AI 智能体。

---

## 中文

一个装在自己手机里的 AI 智能体。接任意 OpenAI 兼容接口，能聊天、能调用工具、能执行本地命令，所有对话数据只存在手机本地。

### 功能

- **对话**：兼容 OpenAI / DeepSeek / OpenRouter / 各类中转站 / 本地 Ollama 等 `/v1/chat/completions` 接口，支持流式输出与随时中断，一回合只占一条消息，工具调用内嵌其中；顶栏就能把「终端」和「工作区」当浮层盖在对话上，再点一次收起，不用跳页
- **角色卡**：名字、头像、一句话身份、性格说话风格、额外要求、开场白都能自己改，支持人设 JSON 导出与粘贴导入，角色卡会拼进系统提示词，优先级高于内置人设
- **附件**：点输入栏的 ＋ 选文件，自动复制进工作区 `uploads/`，以 chip 形式挂在输入框上方，发完自动清空。图片会压缩后直接随对话发给模型，模型真的能看图；文本类附件内容直接内联进提问，其余类型给出工作区路径，模型可以用「读文件」自己取
- **密钥池**：一行一个 Key，可自动轮换；一键逐个测活，Key 无效 / 无权限 / 额度用尽分开提示
- **容错重试**：超时、限流、5xx、断连都会自动重试（默认 6 次，可在设置里改），密钥池不止一个 Key 时重试必定换一个，退避最多等 6 秒；重试用完仍失败会自己补一轮「继续」，不把任务掐断。网关不认流式只回整段 JSON、模型只返回思考内容、上游真的返回空内容，这三种以前会「额度扣了但界面一个字没有」的情况，现在都能正常显示或明确报错
- **思考与正文分家**：思维链（reasoning / thinking）单独走一条通道，折叠在气泡里，点一下才展开，绝不会被当成正文吐出来；万一模型只回思考不给正文，会自动补要一次，不惯着
- **工具调用**：内置 34 个工具，模型可按需自行调用
  - 当前时间、计算器（支持一次传多个表达式批处理）、设备信息（机型/系统/电量/内存）、读剪贴板、记忆检索、知识库检索
  - 打开链接、抓取网页正文、联网搜索（内置必应，**不用填 Key**；填了 Tavily 就优先走 Tavily，长文摘要更细）
  - 终端命令（默认关闭。开启后模型可执行 shell，走的是常驻会话，`cd` / `export` 与你在终端页里写的配置都生效。执行通道可选 **自动 / 沙箱 / Shizuku / Root**：自动按 Root → Shizuku → 沙箱顺位降级，有 root 就是 uid 0，装了 Shizuku 并授权就是 shell 权限，都没有就退回应用自己的沙箱。选错了不会硬失败，只会往下掉一级）
  - 读文件、写文件、列目录（工作区内），截屏（首次用需在系统弹窗里授权一次，截好的图会直接带回对话让模型看图作答）
  - 自动操作：点击坐标、滑动、输入文字、按返回/主页/最近任务、按文字点击控件（需开启无障碍服务）
- **特权通道**：设置 →「工具与权限」页新增「命令执行通道」一栏，实时显示 Root / Shizuku / 当前生效通道，带「申请 Shizuku 授权」与「重新检测通道」两个按钮。模型可以用 `check_privilege` 随时自查现在能伸手到哪，不用瞎试权限。Root 只在真的拿到 `uid=0` 时才认，绝不假装成功
- **自动操作权限**：设置 →「工具与权限」页有「设备自动化权限」一栏，实时显示无障碍服务与「修改系统设置」的授权状态，点一下直接跳到对应系统页面。开启无障碍后模型才能真正代替手指操作屏幕，这也是无 root 环境里能拿到的最强控制权
- **设备操控**：音量、亮度、手电筒、震动、写剪贴板、跳系统设置页、列出/打开应用、媒体播放控制、朗读回复。用说话的方式就能用（"声音小一点""开手电""打开微信"），默认开启，可在设置里整体关掉
- **工具预算**：时间/计算/设备信息这类本地只读工具不占预算，只有实际操作设备的才计费；默认每回合 12 次，用完了会提示回一句「继续」，不会直接把对话掐断。同一次提问里同参数的工具只真跑一遍，重复要求复用上次结果
- **记忆库**：聊完自动沉淀长期事实，新对话自动带上；可手动增删置顶，也能按需重抽
- **知识库**：粘贴正文或只给网址自动抓取，回答时按段引用
- **提示词库**：常用问法存本机，聊天页右上角 ⋮ →「快捷短语」点一下就填进输入框；自带 5 条，可自行增删改，支持 `{time}` / `{model}` 变量
- **自定义插件**：HTTP 接口 / 本机命令 / MCP 工具三种，导入后可先试跑
- **用量统计**：只记服务端返回的 token，按模型分账，全部留在本机，可随时清零
- **终端**：常驻 shell 会话，`cd` / `export` / `alias` 都留得住，跟真终端一样；PATH 补齐 `/system/bin`、`/vendor/bin`、`/apex/com.android.runtime/bin` 等，`ls`、`ps`、`getprop`、`ip` 直接能用。顶栏常显当前目录（可点复制），跑不完点 ✕ 中断、↻ 重开会话；下面一条常用命令条（`ls -la`、`df -h`、`ps -A`、`ip addr`…）点一下填进输入行。界面终端与 AI 调工具用的终端是两条独立会话，互不打断
- **工作区**：智能体的 home 目录——可进子目录、回上一层、新建文件夹、重命名、复制路径、删除；目录优先 + 名称升序。首次初始化自动建好 `uploads / notes / scripts / bin / downloads / .tmp`，跟终端是同一个目录，AI 在终端里写出来的文件立刻能看到
- **模型切换**：聊天页顶栏那行「模型 · 记忆 · 知识」可点，弹出模型列表——收藏的置顶、当前用的打勾，能一键刷新列表、一键收藏/取消收藏，不用绕进设置
- **会话**：多会话、重命名、删除、导出文本、长按消息删除、重新生成；折叠栏支持按标题和最后一条消息搜索
- **设置**：十三类、两级结构——首页只列「标题 ＋ 图标 ＋ 一行小字简介」，点进分类才是具体开关：模型与 API、智能体人设、技能 Skills、工具与权限、使用统计、主题与字体、对话管理、工作区管理、联动扩展、网络与安全、通知、快捷键与手势、关于与赞助。还没做的功能如实标「开发中」，不装作能用
- **内置更新**：「关于」页检查新版本后，直接把安装包下到应用私有目录——进度条实时走，三条加速镜像自动降级、断点续传，下过同版本复用；下完点一下就调起系统安装器，全程不跳浏览器、不需要存储权限
- **外观**：樱粉×冰蓝配色，跟随系统 / 浅色 / 深色三态切换
- **隐私**：不经过任何第三方中转服务器，会话只落在 `filesDir/conversations.json`，用量只落在 `filesDir/stats.json`

### 安装

到 [Releases](../../releases) 下载 `app-release.apk`，直接安装。仓库自带固定签名，后续版本可以直接覆盖安装，不会出现"签名冲突"。

### 快速开始

1. 打开折叠栏 →「设置」→「模型与 API」→ 接口地址
2. 填 `API 地址`（例如 `https://api.openai.com/v1`）和 `API Key`
3. 点「获取列表」挑一个模型，或直接手写模型名
4. 回到「对话」，开始聊

### 构建

```bash
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 正式包（使用仓库内 keystore/zhiwei-agent.jks 签名）
```

正式包由 GitHub Actions 自动构建，见 `.github/workflows/build-release.yml`。

### 权限说明

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 调用模型接口、抓取网页、联网搜索 |
| `ACCESS_NETWORK_STATE` | 判断网络可用性 |
| `POST_NOTIFICATIONS` | 可选，只有打开「回复完成提醒」时才申请 |
| `VIBRATE` | 设备操控里的震动、发送震动反馈 |
| `WRITE_SETTINGS` | 可选，只有你主动让知微调屏幕亮度时才需要手动开启 |
| `BIND_ACCESSIBILITY_SERVICE` | 声明本应用的无障碍服务，用于模拟点击 / 滑动 / 输入；服务本身必须在系统「无障碍」设置页手动开启，不会自动生效 |

`<queries>` 里声明了启动器、语音识别与 TTS 三组 intent，用于列出可启动的应用、朗读回复；不声明的话 Android 11+ 查不到这些应用。

终端以应用自身权限运行（非 root），只能访问应用沙箱与自己有权限的目录。

无障碍服务是本应用在无 root 条件下唯一的全局控制通道：开启后可以点击坐标、滑动、向焦点输入框写文字、按返回 / 主页 / 最近任务，也可以按屏幕上的文字找到控件并点击。它读不到你的输入内容以外的隐私数据，也不会把截图或屏幕内容上传到任何服务器——所有动作都在本机执行，只由模型的工具调用触发。随时可以在系统「无障碍」设置里关掉。

---

## English

An AI agent that lives in your phone. Point it at any OpenAI-compatible endpoint — it chats, calls tools, and runs local commands. Everything stays on the device.

### Features

- **Chat** — works with OpenAI / DeepSeek / OpenRouter / relay services / local Ollama via `/v1/chat/completions`, with streaming output and instant stop. One turn takes one message, tool calls inline; the top bar lays **Terminal** and **Workspace** over the chat as overlays — tap again to dismiss, no page jumping
- **Persona card** — name, avatar, one-line identity, personality, extra instructions and greeting are all yours to edit, with JSON export and paste-import. The card is appended to the system prompt and outranks the built-in persona
- **Attachments** — tap ＋ in the input bar and pick files; they land in the workspace `uploads/` as chips above the input. Images are downscaled and sent with the conversation, so the model genuinely sees them; text attachments are inlined into the question, and anything else gets a workspace path the model can fetch with **read file**
- **Key pool** — one key per line, optionally rotating; test them all in one tap, with invalid / unauthorised / quota-exhausted told apart
- **Retries that actually retry** — timeouts, rate limits, 5xx and dropped connections are retried automatically (6 times by default, adjustable), always switching to another key when the pool has more than one, backing off up to 6 seconds; if retries run out the agent adds its own "continue" instead of killing the task. A gateway that ignores `stream:true` and returns one plain JSON blob, a model that only returns its reasoning, or genuinely empty upstream output — the three cases that used to burn tokens and print nothing — now either render or fail with a clear reason
- **Reasoning kept out of the answer** — the thinking chain (reasoning / thinking) travels on its own channel and stays folded inside the bubble, one tap to expand; it is never printed as the answer. If a model hands back nothing but reasoning, the agent asks once for the actual answer instead of letting it slide
- **Tool calling** — 34 built-in tools the model can invoke on its own
  - current time, calculator (takes several expressions in one batch call), device info (model / OS / battery / memory), read clipboard, memory search, knowledge search
  - open URL, fetch page text, web search (built-in Bing, **no key needed**; add a Tavily key and it takes priority for longer snippets)
  - shell command (off by default. Once enabled the model may run shell through the persistent session, so `cd` / `export` and your terminal-page settings carry over. Pick the execution channel — **auto / sandbox / Shizuku / root**: auto falls back root → Shizuku → sandbox, so root means uid 0, an authorised Shizuku means shell privileges, and neither means the app sandbox. A wrong pick degrades one level instead of failing hard)
  - read file, write file, list directory (inside the workspace), and screenshot (one system grant the first time; the capture is fed straight back into the conversation so the model answers from the picture)
  - UI automation: tap a coordinate, swipe, type into the focused field, press back / home / recents, tap a widget by its text (requires the accessibility service)
- **Privileged channels** — Settings → **Tools & permissions** gains a **Command channel** block showing live root / Shizuku / effective-channel status, with buttons to request Shizuku permission and to re-probe. The model can call `check_privilege` to see exactly how far it can reach instead of guessing. Root counts only when it really gets `uid=0` — no pretending
- **Automation permissions** — Settings → **Tools & permissions** has a **Device automation** block that shows live status for the accessibility service and "modify system settings", each with a button that deep-links to the matching system page. The accessibility service is the only way to really drive the screen without root, and it only starts doing anything after you enable it yourself
- **Device control** — volume, brightness, torch, vibration, clipboard write, jump to system settings, list/open apps, media playback control, speak the reply. Just say it ("volume down", "torch on", "open WeChat"). On by default and switchable off in Settings
- **Tool budget** — local read-only tools (time, calculator, device info) are free; only tools that touch the device are billed. 12 per turn by default, and running out prompts you to reply "continue" instead of cutting the conversation off. Within one question, an identical call with identical arguments runs once; repeats reuse the previous result
- **Memory** — long-term facts distilled from your chats and carried into new conversations; add, edit, pin or re-extract at will
- **Knowledge** — paste text or just hand it a URL; answers cite the matching paragraphs
- **Prompt library** — save your usual phrasings on device and tap one under ⋮ → **Quick phrases** in the chat top bar to fill it in; five are seeded, you can add, edit or delete them, and `{time}` / `{model}` get substituted
- **Plugins** — HTTP endpoint, local command or MCP tool; test-run before enabling
- **Usage stats** — counts only what the server reports, broken down per model, stored on device, clearable anytime
- **Terminal** — dedicated tab, quick commands, live scrolling output; reached via **AI computer** in the chat input bar
- **Workspace** — the agent's home directory laid out plainly: size, modified time, tap to preview, long-press to delete. Same directory the terminal uses, so anything the agent writes shows up immediately
- **Sessions** — multiple conversations, rename, delete, export text, long-press to delete a message, regenerate
- **Settings** — thirteen groups, two levels: the list shows only a title, an icon and a one-line summary, and the switches live one level down: Model & API, Persona, Skills, Tools & permissions, Usage, Theme & fonts, Conversations, Workspace, Integrations, Network & security, Notifications, Shortcuts & gestures, About & support. Anything not built yet is labelled "in progress" instead of being faked
- **Built-in update check** — tap it under About us to see the newest version, its notes and APK size, then jump to the download (mirror / original). It checks and links, nothing installs itself
- **Look** — sakura pink × ice blue theme, system / light / dark modes
- **Privacy** — no third-party relay server; conversations in `filesDir/conversations.json`, usage in `filesDir/stats.json`

### Install

Grab `app-release.apk` from [Releases](../../releases). A fixed keystore is bundled in this repo, so later versions install right over earlier ones without signature conflicts.

### Quick start

1. Open the drawer → **Settings → Model & API → endpoint**
2. Enter `API base URL` (e.g. `https://api.openai.com/v1`) and your `API key`
3. Tap **Fetch models** and pick one, or type a model name
4. Go back to **Chat** and start talking

### Build

```bash
./gradlew assembleDebug     # debug
./gradlew assembleRelease   # release (signed with keystore/zhiwei-agent.jks)
```

Release builds are produced by GitHub Actions — see `.github/workflows/build-release.yml`.

### Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | model API calls, page fetching, web search |
| `ACCESS_NETWORK_STATE` | network availability checks |
| `POST_NOTIFICATIONS` | optional; only requested when you turn on "notify when a reply finishes" |
| `VIBRATE` | device-control vibration and send haptics |
| `WRITE_SETTINGS` | optional; needed only when you ask the agent to adjust screen brightness |
| `BIND_ACCESSIBILITY_SERVICE` | declares the app's accessibility service for simulated taps / swipes / typing; the service itself must be switched on by hand in system **Accessibility** settings — it never enables itself |

The `<queries>` block declares three intent groups — launcher, speech recognition and TTS — so it can list launchable apps and speak replies. Without it, Android 11+ cannot see those apps.

The terminal runs with the app's own privileges (no root) and can only touch the app sandbox and directories the app already has access to.

The accessibility service is the only global control channel this app has without root: once enabled it can tap coordinates, swipe, write into the focused field, press back / home / recents, and find a widget by its on-screen text to tap. Nothing leaves the device — every action is executed locally and only ever triggered by a model tool call, and you can switch the service off in system settings at any time.

---

- 官方 QQ 群 / Official QQ group: **1131325287**
- 联系邮箱 / Contact: **ailixiya@qycxkjit.wecom.work**
- 更多产品 / More: [始源创想科技IT](https://github.com/mingxiozsg-star)
