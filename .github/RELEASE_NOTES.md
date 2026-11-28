## 中文

v1.9.1 修了一个让人火大的问题：你让它跑命令，它跑不了。同时把一台真正的 Linux 装进了 APK。

**先修「调用不了」**

两个故障叠在一起，缺一个都修不好：

- 「终端命令」开关默认是关的，`run_shell` 压根没下发给模型。模型看不见这个工具，自然调不动。现在默认开启。
- 更隐蔽的是：旧版本把这个 `false` 存进了本地配置，光改代码默认值救不回来，升级上来的用户依然关着。所以做了次一次性迁移，升级后首次启动自动打开，之后你自己怎么改都尊重。
- `run_shell` 的工具描述重写了，把「当前运行环境」和「权限取决于哪条通道」直接写进去——描述是模型唯一的能力说明书，写不清楚它就不知道能干什么。

另外修了 proot 二进制不落盘的问题：AGP 默认把 `.so` 留在 APK 内 mmap，`nativeLibraryDir` 里没有实体文件，`exec` 直接失败。开启 `useLegacyPackaging` 后才真正解压到磁盘。

**内置 Linux 环境**

设置 →「工具与权限」新增「运行环境」一栏，两个维度正交，随便组合：

- **权限通道** 自动 / 沙箱 / Shizuku / Root —— 决定借谁的权限
- **运行环境** 安卓原生 / 内置 Linux —— 决定用户空间

内置 Linux 是 Alpine 3.20.9，rootfs 随 APK 分发（约 3.9MB，不联网下载），一键解压即可用。装完能 `apk add python3 git gcc curl`，有完整 `/usr` 和 GNU 工具链，`/sdcard` 自动挂载。两套环境并存，随时切，卸载只删 rootfs，不影响安卓原生终端。

用的是 proot 而不是 chroot。原因很实在：chroot 需要 `CAP_SYS_CHROOT`，普通应用和 shell 用户都拿不到（实测 `chroot: Operation not permitted`）。proot 走 ptrace 在用户态模拟，不需要任何特权，代价是有一定性能损耗。

已内置 `/etc/resolv.conf`（阿里 / 腾讯 / Cloudflare 三路 DNS）和 `/etc/apk/repositories`（阿里云 + 清华 + 官方三源），装完直接能联网装包。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

v1.9.1 fixes an infuriating bug: you ask it to run a command, and it can't. It also puts a real Linux inside the APK.

**First, the "it can't call the tool" fix**

Two faults stacked on top of each other; fixing only one does nothing:

- The shell tool switch defaulted to off, so `run_shell` was never sent to the model. The model cannot see a tool that isn't in its list, hence couldn't call it. Now on by default.
- The subtler half: older builds persisted that `false` into local settings, so changing the code default rescues nobody who upgraded — they stay off. A one-shot migration now flips it on first launch after upgrading, and afterwards your own choice is respected.
- The `run_shell` description was rewritten to state the current runtime and that privileges depend on the channel — the description is the model's only manual, so a vague one means it has no idea what it can do.

Also fixed proot binaries never landing on disk: AGP keeps `.so` inside the APK and mmaps them, leaving nothing in `nativeLibraryDir` to `exec`. Turning on `useLegacyPackaging` makes them real files.

**Bundled Linux runtime**

Settings → **Tools & permissions** gains a **Runtime** block. Two orthogonal dimensions, mix freely:

- **Privilege channel** auto / sandbox / Shizuku / root — whose privileges to borrow
- **Runtime** Android-native / bundled Linux — which userland to run in

The bundled Linux is Alpine 3.20.9, shipped inside the APK (~3.9 MB, no download), extracted with one tap. Then `apk add python3 git gcc curl` just works, with a full `/usr`, a GNU toolchain, and `/sdcard` bind-mounted. Both runtimes coexist and switch any time; uninstalling deletes only the rootfs and leaves the Android-native terminal alone.

It uses proot rather than chroot, for a concrete reason: chroot needs `CAP_SYS_CHROOT`, which neither an ordinary app nor the shell user holds (measured: `chroot: Operation not permitted`). proot emulates in user space via ptrace and needs no privileges at all, at some performance cost.

`/etc/resolv.conf` (Aliyun / Tencent / Cloudflare DNS) and `/etc/apk/repositories` (Aliyun + Tsinghua + official mirrors) come preconfigured, so package installs work right away.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287
