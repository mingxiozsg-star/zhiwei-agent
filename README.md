# 知微智能体 · Zhiwei Agent

> A local-first AI agent for Android. 本地运行的手机端 AI 智能体。

---

## 中文

一个装在自己手机里的 AI 智能体。接任意 OpenAI 兼容接口，能聊天、能调用工具、能执行本地命令，所有对话数据只存在手机本地。

### 功能

- **对话**：兼容 OpenAI / DeepSeek / OpenRouter / 各类中转站 / 本地 Ollama 等 `/v1/chat/completions` 接口，支持流式输出与随时中断，一回合只占一条消息，工具调用内嵌其中
- **密钥池**：一行一个 Key，可自动轮换；一键逐个测活，Key 无效 / 无权限 / 额度用尽分开提示
- **工具调用**：内置 23 个工具，模型可按需自行调用
  - 当前时间、计算器（支持一次传多个表达式批处理）、设备信息（机型/系统/电量/内存）、读剪贴板、记忆检索、知识库检索
  - 打开链接、抓取网页正文、联网搜索（需自备 Tavily Key）
  - 终端命令（默认关闭，开启后模型可在应用沙箱内执行 shell）
- **设备操控**：音量、亮度、手电筒、震动、写剪贴板、跳系统设置页、列出/打开应用、媒体播放控制、朗读回复。用说话的方式就能用（"声音小一点""开手电""打开微信"），默认开启，可在设置里整体关掉
- **工具预算**：时间/计算/设备信息这类本地只读工具不占预算，只有实际操作设备的才计费；默认每回合 12 次，用完了会提示回一句「继续」，不会直接把对话掐断。同一次提问里同参数的工具只真跑一遍，重复要求复用上次结果
- **记忆库**：聊完自动沉淀长期事实，新对话自动带上；可手动增删置顶，也能按需重抽
- **知识库**：粘贴正文或只给网址自动抓取，回答时按段引用
- **提示词库**：常用问法存本机，聊天页输入栏的 ＋ 里点一下就填进输入框；自带 5 条，可自行增删改，支持 `{time}` / `{model}` 变量
- **自定义插件**：HTTP 接口 / 本机命令 / MCP 工具三种，导入后可先试跑
- **用量统计**：只记服务端返回的 token，按模型分账，全部留在本机，可随时清零
- **终端**：独立的终端页，快捷命令一键填入，输出实时滚动
- **会话**：多会话、重命名、删除、导出 Markdown、长按消息复制或重新生成
- **设置**：接口与密钥池、智能体（含设备操控开关、每回合工具预算）、提示词库、记忆与知识、生成参数（温度/Top-P/频率惩罚/存在惩罚/最大长度）、外观（主题、字号缩放、抽屉宽度）、显示与行为（工具折叠方式、消息时间、屏幕常亮、完成提醒、发送震动）
- **外观**：樱粉×冰蓝配色，跟随系统 / 浅色 / 深色三态切换
- **隐私**：不经过任何第三方中转服务器，会话只落在 `filesDir/conversations.json`，用量只落在 `filesDir/stats.json`

### 安装

到 [Releases](../../releases) 下载 `app-release.apk`，直接安装。仓库自带固定签名，后续版本可以直接覆盖安装，不会出现"签名冲突"。

### 快速开始

1. 打开底部「设置」→「接口」
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

`<queries>` 里声明了启动器、语音识别与 TTS 三组 intent，用于列出可启动的应用、朗读回复；不声明的话 Android 11+ 查不到这些应用。

终端以应用自身权限运行（非 root），只能访问应用沙箱与自己有权限的目录。

---

## English

An AI agent that lives in your phone. Point it at any OpenAI-compatible endpoint — it chats, calls tools, and runs local commands. Everything stays on the device.

### Features

- **Chat** — works with OpenAI / DeepSeek / OpenRouter / relay services / local Ollama via `/v1/chat/completions`, with streaming output and instant stop. One turn takes one message, tool calls inline
- **Key pool** — one key per line, optionally rotating; test them all in one tap, with invalid / unauthorised / quota-exhausted told apart
- **Tool calling** — 23 built-in tools the model can invoke on its own
  - current time, calculator (takes several expressions in one batch call), device info (model / OS / battery / memory), read clipboard, memory search, knowledge search
  - open URL, fetch page text, web search (bring your own Tavily key)
  - shell command (off by default; when enabled the model may run shell inside the app sandbox)
- **Device control** — volume, brightness, torch, vibration, clipboard write, jump to system settings, list/open apps, media playback control, speak the reply. Just say it ("volume down", "torch on", "open WeChat"). On by default and switchable off in Settings
- **Tool budget** — local read-only tools (time, calculator, device info) are free; only tools that touch the device are billed. 12 per turn by default, and running out prompts you to reply "continue" instead of cutting the conversation off. Within one question, an identical call with identical arguments runs once; repeats reuse the previous result
- **Memory** — long-term facts distilled from your chats and carried into new conversations; add, edit, pin or re-extract at will
- **Knowledge** — paste text or just hand it a URL; answers cite the matching paragraphs
- **Prompt library** — save your usual phrasings on device and tap one in the chat input's ＋ to fill it in; five are seeded, you can add, edit or delete them, and `{time}` / `{model}` get substituted
- **Plugins** — HTTP endpoint, local command or MCP tool; test-run before enabling
- **Usage stats** — counts only what the server reports, broken down per model, stored on device, clearable anytime
- **Terminal** — dedicated tab, quick commands, live scrolling output
- **Sessions** — multiple conversations, rename, delete, export Markdown, long-press to copy or regenerate
- **Settings** — endpoint and key pool, agent (including the device-control switch and per-turn tool budget), prompt library, memory and knowledge, generation (temperature / Top-P / penalties / max length), appearance (theme, font scale, drawer width), display and behaviour (tool-call folding, message time, keep screen on, finish notification, haptic on send)
- **Look** — sakura pink × ice blue theme, system / light / dark modes
- **Privacy** — no third-party relay server; conversations in `filesDir/conversations.json`, usage in `filesDir/stats.json`

### Install

Grab `app-release.apk` from [Releases](../../releases). A fixed keystore is bundled in this repo, so later versions install right over earlier ones without signature conflicts.

### Quick start

1. Open **Settings → Endpoint**
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

The `<queries>` block declares three intent groups — launcher, speech recognition and TTS — so it can list launchable apps and speak replies. Without it, Android 11+ cannot see those apps.

The terminal runs with the app's own privileges (no root) and can only touch the app sandbox and directories the app already has access to.

---

- 官方 QQ 群 / Official QQ group: **1131325287**
- 联系邮箱 / Contact: **ailixiya@qycxkjit.wecom.work**
- 更多产品 / More: [始源创想科技IT](https://github.com/mingxiozsg-star)
