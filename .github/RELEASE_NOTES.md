## 中文

v1.9.0 把 AI 的手伸出了沙箱——它现在可以借 Root 或 Shizuku 的权限执行命令，真正操控整台设备。

**命令执行通道**

设置 →「工具与权限」新增「命令执行通道」一栏：

- 四档可选：自动 / 沙箱 / Shizuku / Root
- 自动按 Root → Shizuku → 沙箱顺位降级，选错了不会硬失败，只会往下掉一级
- 实时显示 Root 与 Shizuku 的检测结果和当前真正生效的通道
- 「申请 Shizuku 授权」直接弹授权框；「重新检测通道」清掉探测缓存重来一次

Root 通道只在真的拿到 `uid=0` 时才认，探测时同时试 `su -c id` 与 `su 0 id` 两种形式，都会失败就当没有 root，不假装成功。结果缓存起来，不会每条命令都去敲一遍 su。

**AI 能自查权限**

新增工具 `check_privilege`，把三条通道的可用状态、当前设置与实际生效通道、屏幕自动化是否开启、搜索走的哪条线一次性摊开给模型。它不用再靠猜来试权限，也不会在沙箱里反复撞墙。

**内置网络搜索，不用再填 Key**

`web_search` 原本要自备 Tavily Key 才会注册，现在改为始终可用：没填 Key 时走内置必应搜索，填了就优先走 Tavily（长文摘要更细），Tavily 失败自动回落必应。装上就能搜。

**终端工具接上强权限**

开启「沙箱终端工具」后，模型调「终端命令」时会走上面选的通道，会话依然是常驻的，`cd`、`export` 和你写的环境变量、启动脚本全部继承。Root 通道下它看到的就是 `uid=0`，能读系统目录、改系统属性、调 `pm` / `am` / `settings`。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

v1.9.0 reaches past the sandbox — the agent can now run commands through Root or Shizuku and genuinely drive the whole device.

**Command execution channel**

Settings → **Tools & permissions** gains a **Command channel** block:

- Four options: auto / sandbox / Shizuku / root
- Auto falls back root → Shizuku → sandbox, so a wrong pick degrades one level instead of failing hard
- Live status for root and Shizuku detection, plus which channel is actually in effect
- **Request Shizuku permission** pops the authorisation dialog; **Re-probe channels** clears the cache and re-checks

The root channel counts only when it really gets `uid=0`. Probing tries both `su -c id` and `su 0 id`, and treats two failures as "no root" rather than pretending otherwise. The result is cached, so it does not knock on `su` for every single command.

**The agent can check its own reach**

A new `check_privilege` tool lays out the status of all three channels, the configured versus effective channel, whether screen automation is on, and which search backend is in use. No more guessing at permissions or bouncing off sandbox walls.

**Built-in web search, no key required**

`web_search` used to register only when a Tavily key was present. It is now always available: with no key it uses built-in Bing, with a key Tavily takes priority for longer snippets, and a Tavily failure falls back to Bing automatically.

**The shell tool now inherits real privileges**

With the sandbox shell tool enabled, the model's shell commands run through whichever channel you picked, still in the persistent session — `cd`, `export`, your environment variables and startup script all carry over. Under root it sees `uid=0` and can read system directories, set properties, and drive `pm` / `am` / `settings`.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287