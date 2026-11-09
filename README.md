# 知微智能体 · Zhiwei Agent

> A local-first AI agent for Android. 本地运行的手机端 AI 智能体。

---

## 中文

一个装在自己手机里的 AI 智能体。接任意 OpenAI 兼容接口，能聊天、能调用工具、能执行本地命令，所有对话数据只存在手机本地。

### 功能

- **对话**：兼容 OpenAI / DeepSeek / OpenRouter / 各类中转站 / 本地 Ollama 等 `/v1/chat/completions` 接口，支持流式输出与随时中断
- **工具调用**：内置 8 个工具，模型可按需自行调用
  - 当前时间、计算器、设备信息（机型/系统/电量/内存）、读剪贴板
  - 打开链接、抓取网页正文、联网搜索（需自备 Tavily Key）
  - 终端命令（默认关闭，开启后模型可在应用沙箱内执行 shell）
- **终端**：独立的终端页，快捷命令一键填入，输出实时滚动
- **会话**：多会话、重命名、删除、导出 Markdown、长按消息复制或重新生成
- **外观**：樱粉×冰蓝配色，跟随系统 / 浅色 / 深色三态切换
- **隐私**：不经过任何第三方中转服务器，会话只落在 `filesDir/conversations.json`

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

终端以应用自身权限运行（非 root），只能访问应用沙箱与自己有权限的目录。

---

## English

An AI agent that lives in your phone. Point it at any OpenAI-compatible endpoint — it chats, calls tools, and runs local commands. Everything stays on the device.

### Features

- **Chat** — works with OpenAI / DeepSeek / OpenRouter / relay services / local Ollama via `/v1/chat/completions`, with streaming output and instant stop
- **Tool calling** — 8 built-in tools the model can invoke on its own
  - current time, calculator, device info (model / OS / battery / memory), read clipboard
  - open URL, fetch page text, web search (bring your own Tavily key)
  - shell command (off by default; when enabled the model may run shell inside the app sandbox)
- **Terminal** — dedicated tab, quick commands, live scrolling output
- **Sessions** — multiple conversations, rename, delete, export Markdown, long-press to copy or regenerate
- **Look** — sakura pink × ice blue theme, system / light / dark modes
- **Privacy** — no third-party relay server; conversations live in `filesDir/conversations.json`

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

The terminal runs with the app's own privileges (no root) and can only touch the app sandbox and directories the app already has access to.

---

- 官方 QQ 群 / Official QQ group: **1131325287**
- 联系邮箱 / Contact: **ailixiya@qycxkjit.wecom.work**
- 更多产品 / More: [始源创想科技IT](https://github.com/mingxiozsg-star)
