## 中文

v1.7.0 是这段时间攒下来的改动，主线三件事：终端终于像个终端，更新不用再跳浏览器，切模型不用再翻设置。

**终端：常驻会话，环境补齐**

以前每敲一条命令就起一个 `sh -c`，`cd` 进去下一条又回到原点，`export` 也不留。现在改成一条常驻 shell：

- `cd`、`export`、`alias` 全都留得住，跟在自己机器上开终端一样
- PATH 补齐 `/system/bin`、`/vendor/bin`、`/apex/com.android.runtime/bin`、`/data/local/bin` 等，`ls`、`ps`、`getprop`、`ip` 直接能用
- 工作区首次初始化就建好 `uploads / notes / scripts / bin / downloads / .tmp`，开箱可用
- 命令跑不完不用杀应用：顶栏 ✕ 中断，↻ 重开会话
- 顶栏常显当前目录，点一下复制
- 加了一条常用命令条（`ls -la`、`df -h`、`ps -A`、`ip addr`…），点一下填进输入行

界面上的终端和模型调用的终端是两条独立会话，AI 跑工具不会打断你手敲的东西。

**内置更新：下完直接装**

- 「关于」页点「下载」把安装包下到应用私有目录，进度条实时走，下完点「安装」直接调起系统安装器
- 三条加速镜像轮着试，一条断了自动换下一条；下了一半的包断点续传，不从头再来
- 下过的同版本包本机复用，不重复下载
- 走应用私有目录 + FileProvider，不需要任何存储权限
- 想自己挑线路下载的，入口还留着

**模型切换：聊天页顶栏直接点**

顶栏那行「模型名 · 记忆 · 知识」现在可以点了：

- 收藏过的模型置顶，当前正在用的打勾
- 一键刷新模型列表
- 一键收藏 / 取消收藏当前模型

不用再进设置翻到「模型与 API」。

**竖框加了快捷键**

折叠栏中间多一排：新对话 / 终端 / 工作区，一键直达。会话多了还能直接搜，标题和最后一条消息都参与匹配。

**提示词升到 v5**

新增一层「纪律」：只做用户要的事、不顺手加需求；工具是手段不是表演，不重复调同一个；一次没成换办法，连续两次失败先问；动手前先过「目标 / 怎么算做完 / 会不会动到别的东西」。

你自己改过的提示词不会被覆盖，只有还是旧版默认文案的才会自动升级。

**工作区支持多级目录**

能点进子目录、能回上一层、能新建文件夹、能重命名、能复制路径、能删整个目录。排序改成目录优先 + 名称升序，不再按修改时间乱跳。

**关于页重做**

顶部加了品牌头（名字 + 版本 + 一句话定位），镜像开关、内置更新、更新说明都收在这一页。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

v1.7.0 collects the work piled up since 1.6.0. Three threads: the terminal finally behaves like a terminal, updates no longer bounce you to a browser, and switching models no longer means digging through settings.

**Terminal: persistent session, full environment**

Each command used to spawn a fresh `sh -c`, so a `cd` was lost by the next line and `export` never stuck. It is now one long-lived shell:

- `cd`, `export` and `alias` all persist, like a real terminal on your own machine
- PATH now covers `/system/bin`, `/vendor/bin`, `/apex/com.android.runtime/bin`, `/data/local/bin` and friends, so `ls`, `ps`, `getprop` and `ip` work out of the box
- The workspace is seeded on first init with `uploads / notes / scripts / bin / downloads / .tmp`
- No need to kill the app when a command hangs: ✕ interrupts, ↻ restarts the session
- The current directory shows in the top bar; tap to copy it
- A quick-command strip (`ls -la`, `df -h`, `ps -A`, `ip addr`…) fills the input with one tap

The on-screen terminal and the one the model drives are separate sessions, so tool calls will not interrupt what you are typing.

**Built-in updates: download, then install**

- Tap "Download" on the About page and the APK lands in app-private storage with a live progress bar; tap "Install" to open the system installer
- Three mirror routes are tried in turn and fall through automatically; partial downloads resume instead of restarting
- An already-downloaded build of the same version is reused
- App-private directory plus FileProvider, no storage permission required
- The manual-download link stays if you prefer picking a route yourself

**Model switching from the chat top bar**

The "model · memories · knowledge" line is now tappable:

- Favourite models pinned on top, the active one ticked
- Refresh the model list in one tap
- Favourite or unfavourite the current model in one tap

No more detour through Settings, Model and API.

**Drawer shortcuts**

A row in the middle of the drawer: new chat, terminal, workspace. Conversations are searchable too, matching both the title and the last message.

**System prompt v5**

A new discipline layer: do only what was asked and do not invent extra scope; tools are a means, not a performance, so do not call the same one twice; when an approach fails, switch, and after two failures ask; before acting, check the goal, the definition of done, and what else might be touched.

Prompts you have edited yourself are never overwritten. Only untouched stock text gets upgraded.

**Workspace gains real directory navigation**

Enter subdirectories, go back up, create folders, rename entries, copy paths, delete whole directories. Sorting is now directories first, then by name, instead of jumping around by modification time.

**About page reworked**

A brand header up top with the name, version and a one-line positioning statement, then the mirror toggle, built-in update and release notes, all on one page.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
