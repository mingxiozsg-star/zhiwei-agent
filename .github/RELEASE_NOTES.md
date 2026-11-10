## 知微智能体 v1.4.0

这一版主要干两件事：把「额度扣了、界面一个字没有」修掉，再把工作区、内置更新和输入栏入口补上。

**「有消耗、没输出」修好了**

以前有三种情况会让你花了钱却什么都看不到，现在都不会了：

- 网关不认流式请求，直接回一整段 JSON —— 以前只按流式逐行读，等于什么都没读到；现在两种格式都认
- 模型只把思考写进 reasoning 字段，不返回正文 —— 现在把思考内容显示出来并标注「模型没有返回正文」
- 上游真的返回空内容 —— 不再留一个空气泡，直接说清是空内容，并且算作可以重试

**报错不再把任务掐断**

- 超时、限流、5xx、断连都会自动重试，默认 6 次（设置里可调 0~10），间隔逐步拉长，最多等 6 秒
- 密钥池里不止一个 Key 时，重试必定换一个 Key
- 重试用完仍然失败，会自动补一轮「继续」接着做，而不是停在那儿等你催
- 重试进度就显示在输入栏上方：第 2/6 次重试：请求过于频繁或额度用尽

**输入栏多了两个入口：AI 电脑 / 工作区**

你：在工作区里建个 sort.py，写个冒泡排序
知微：写好并跑了一遍 → 你切到「工作区」就能看到 sort.py，点开看内容，长按删掉。

- 「AI 电脑」就是终端沙箱，「工作区」是智能体的 home 目录，跟终端是同一个目录
- 这两个入口只放在输入栏一处，折叠栏里不再重复放

**设置按用途分成十类，最底下是「关于我们」**

接口 / API 密钥池 / 智能体 / 提示词库 / 记忆与知识 / 生成参数 / 外观 / 显示与行为 / 用量统计 / 数据，最底下是「关于我们」。

「关于我们」里可以直接检查更新：有新版本就显示版本号、更新说明、安装包大小，一键跳转下载（镜像或原站）。只检查不自己装。

**系统提示词升级到 v2**

先给结论、能自己查的就自己查、多步任务先列 2~5 步计划再逐步做完、工具报错不等于任务结束、不编造事实和链接、删除支付这类不可逆操作先说明。自己改过提示词的不会被动，只有从没改过的才会自动换成新版。

**下载**

- Releases：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速镜像：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## Zhiwei Agent v1.4.0

Two things this release: the "tokens burned, nothing on screen" bug is gone, and Workspace, built-in update check and the input-bar entries are in.

**"Tokens burned, nothing printed" is fixed**

Three cases used to cost you money and show you nothing. None of them do now:

- The gateway ignores the streaming request and returns one plain JSON blob — the old code only read line-by-line SSE, so it saw nothing. Both formats are handled now.
- The model writes only into `reasoning` and returns no content — its thinking is shown and labelled "the model returned no content".
- Upstream genuinely returns nothing — no more blank bubble; it says so plainly, and counts as retryable.

**Errors no longer kill the task**

- Timeouts, rate limits, 5xx and dropped connections retry automatically — 6 times by default (0–10 in Settings), backing off up to 6 seconds
- With more than one key in the pool, every retry switches to a different key
- If retries run out, the agent adds its own "continue" instead of stopping and waiting for you
- Progress shows above the input bar: "retry 2/6: too many requests or quota exhausted"

**Two entries on the input bar: AI computer / Workspace**

You: create sort.py in the workspace with a bubble sort
Agent: written and run → switch to Workspace and sort.py is there; tap to read it, long-press to delete it.

- **AI computer** is the terminal sandbox; **Workspace** is the agent's home directory — the same directory the terminal uses
- Both live only on the input bar; the drawer no longer duplicates them

**Settings grouped into ten sections, with About us at the bottom**

Endpoint / API key pool / Agent / Prompt library / Memory & knowledge / Generation / Appearance / Display & behaviour / Usage stats / Data — with About us at the bottom.

About us checks for updates right there: a new version shows its tag, notes and APK size, with one-tap jumps to the download (mirror or original). It checks and links, never installs by itself.

**System prompt upgraded to v2**

Lead with the conclusion; look things up yourself where you can; for multi-step jobs list a 2–5 step plan and work through it; a tool error is not the end of the task; never invent facts or links; explain before irreversible actions like deletes and payments. If you edited your prompt, nothing changes — only untouched ones migrate.

**Download**

- Releases: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
