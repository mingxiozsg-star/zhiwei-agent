## 中文

v1.8.0 给 AI 补上了「手」和「眼睛」——它现在能真的替你操作屏幕。

**设备自动化（需开启无障碍服务）**

新增 6 个工具：读屏幕控件、点击坐标、滑动、输入文字、按返回/主页/最近任务、按文字点击控件。

模型看不见你的屏幕，靠猜坐标基本点不中，所以第一步是 `ui_read_screen`：它把当前窗口里所有可点击的控件连同文字和中心坐标一起列出来，模型照着点，命中率比盲猜高一个数量级。

无 root 环境下这是能拿到的最高控制权。服务必须在系统「无障碍」设置里手动开启，应用不会、也无法自己打开。

**权限入口用起来不费劲**

设置 →「工具与权限」新增「设备自动化权限」一栏：

- 无障碍服务与「修改系统设置」的授权状态实时显示，改完回到本页立刻同步
- 每项后面一个按钮，直接跳到对应的系统页面，不用自己翻设置
- 工具清单里也能看到自动操作工具当前是「已上线」还是「待开启无障碍」

**终端配置真正生效到 AI 手上**

上一版加的「终端环境配置」只有你在终端页里跑命令才用得上。这一版 AI 调「终端命令」工具时走的是同一条常驻会话，`cd`、`export` 和你写的环境变量、启动脚本全部继承，AI 跑完还会把当前目录带回来。

另外设备信息工具补了屏幕分辨率与像素密度，AI 要算点击坐标时不用再瞎估。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

v1.8.0 gives the agent hands and eyes — it can now actually operate the screen for you.

**Device automation (requires the accessibility service)**

Six new tools: read on-screen widgets, tap a coordinate, swipe, type text, press back / home / recents, and tap a widget by its text.

The model cannot see your screen, and guessing coordinates mostly misses. So step one is `ui_read_screen`: it lists every clickable widget in the current window along with its label and centre coordinates. The model taps from that list, which lands far more often than blind guessing.

Without root this is the strongest control an app can get. The service must be enabled by hand in system **Accessibility** settings — the app will not and cannot turn it on itself.

**Permissions, without the scavenger hunt**

Settings → **Tools & permissions** gains a **Device automation** block:

- Live status for the accessibility service and "modify system settings", refreshed as soon as you come back from the system page
- One button per row that deep-links straight to the matching system screen
- The tool list now shows whether the automation tools are live or still waiting on accessibility

**Terminal config now reaches the model too**

The terminal environment config added last version only applied to commands you typed in the terminal page. The model's shell-command tool now shares the same persistent session, so `cd`, `export`, your environment variables and your startup script all carry over — and it reports the working directory back when it finishes.

Device info also gained screen resolution and density, so the agent no longer has to guess when computing tap coordinates.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287