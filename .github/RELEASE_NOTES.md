## 知微智能体 v1.3.0

这一版加了两块新东西——**设备操控**和**提示词库**，顺手把工具调用和界面收拾了一遍。

**设备操控：说话就能用**

设置里打开「允许设备操控」，下面这些直接对知微说就行，不用记命令：

- 「声音小一点」→ 直接改音量，还会把现在的音量报给你
- 「手电筒打开」→ 开关闪光灯
- 「屏幕亮一点」→ 调屏幕亮度（第一次用会引导你去开「修改系统设置」权限）
- 「打开微信」→ 按应用名找并启动，找不到包名就按名字模糊匹配
- 「震动一下」→ 短震动
- 「念一下」→ 把回复朗读出来
- 「把这段复制到剪贴板」「跳到 WiFi 设置」→ 剪贴板与系统设置页
- 「手机上装了哪些应用」→ 列出可启动的应用，也可以按名字筛

没装、没权限、没手电筒硬件，都会直接说清是哪一种，不糊弄。

**提示词库：常用问法点一下就填**

聊天页输入栏左边的 ＋ 现在就是提示词库，点一条直接填进输入框。第一版自带 5 条：总结要点、翻译成中文、润色、写代码、列计划。设置 → 提示词库里能自己增删改，存在本机。提示词里可以写 `{time}` 和 `{model}`，填入时自动替换成当前时间和当前模型名。

**工具调用：预算代替次数上限，一个算式一次算完**

- 时间、计算、设备信息、记忆检索这类本地只读工具不占预算；只有真动设备的（音量、亮度、开应用、上网抓取等）才计费。默认预算从 4 提到 12。
- 预算用完了不会把对话掐断，会直接告诉你：回一句「继续」就接着做，或者去设置里把预算调大。
- 计算器支持一次传多个表达式，批处理比一个一个算快得多。
- 同一次提问里，同样的工具、同样的参数只真跑一遍，模型再要就复用上次结果。
- 执行失败的工具会在卡片上标「【执行失败】」，不用点开才知道。

**界面收拾干净了**

折叠栏的功能入口从两排方块图标改成一列（图标在左、文字在右），不再挤成一团；记忆库、知识库、工具与插件已经在折叠栏里，就从顶栏菜单和输入栏 ＋ 里删掉了，重复的入口只留一处。

**下载**
- Releases：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速镜像：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## Zhiwei Agent v1.3.0

Two new things this release — **device control** and a **prompt library** — plus a pass over tool calls and the UI.

**Device control: just say it**

Turn on "Allow device control" in Settings and the following work by voice or text, no commands to memorise:

- "Volume down a bit" → changes volume and tells you the new level
- "Turn on the torch" → toggles the flash
- "Screen brighter" → adjusts brightness (first time it walks you to the "modify system settings" permission)
- "Open WeChat" → launches by app name, falling back to a fuzzy name match
- "Buzz once" → a short vibration
- "Read it out" → speaks the reply
- "Copy this to the clipboard", "Jump to Wi-Fi settings"
- "What apps are installed?" → lists launchable apps, filterable by name

Missing app, missing permission, no flash hardware — each case says which one it is instead of hand-waving.

**Prompt library: tap to fill**

The ＋ on the left of the chat input is now your prompt library — tap one and it drops into the input box. Five are seeded: summarise, translate to Chinese, polish, write code, make a plan. Add, edit and delete them under Settings → Prompt library; everything stays on device. Use `{time}` and `{model}` inside a prompt and they get filled in with the current time and model name.

**Tool calls: a budget instead of a round cap, and one shot per sum**

- Local read-only tools (time, calculator, device info, memory search) are free. Only tools that actually touch the device (volume, brightness, launching apps, fetching pages) count. The default budget goes from 4 to 12.
- Running out of budget no longer cuts the conversation off. It tells you: reply "continue" and it keeps going, or raise the budget in Settings.
- The calculator takes several expressions in one call — batch beats one at a time.
- Within one question, an identical call with identical arguments runs once; repeats reuse the previous result.
- Failed tools are marked "【执行失败】" on the card, no need to expand to find out.

**Cleaner UI**

The drawer's feature entries went from two rows of square icons to a single column (icon left, label right) instead of a pile. Memory, knowledge and plugins already live in the drawer, so their entries were removed from the top-bar menu and the input ＋. Each entry now appears in one place only.

**Download**
- Releases: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
