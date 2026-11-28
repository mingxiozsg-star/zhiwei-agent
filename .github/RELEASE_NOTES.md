## 中文

v1.9.2 修掉内置 Linux 启动不了，并把工作区交回给模型自己管。

**内置 Linux 修好了**

v1.9.1 里点「安装」后跑命令会直接报：

```
CANNOT LINK EXECUTABLE ".../libproot.so": library "libtalloc.so.2" not found
```

原因是两套命名规则打架：proot 二进制里写的依赖名是 `libtalloc.so.2` 和 `libandroid-shmem.so`（Termux 的叫法），但 Android 只从 `nativeLibraryDir` 里加载 `lib*.so` 形态的文件，打进包里的名字只能是 `libtalloc.so` 和 `libandroidshmem.so`。名字对不上，动态链接器就找不到依赖。

这次直接改二进制：把 `DT_NEEDED` 里的依赖名改成 APK 内的实际文件名，同步两个库的 `SONAME`，再清掉编译时残留的 `/data/data/com.termux/files/usr/lib` 搜索路径——留着它只会把查找方向带偏。现在 proot 的依赖在同一个目录里就能自洽解析。

**工作区真的能改了**

之前只有列目录、读文件、写文件三个工具。想建个空目录、删个文件、改个名，模型没有任何手段，只能干看着——这就是「建目录返回不了」。

补齐六个工具：

- `make_dir` 建目录，父级自动创建
- `delete_path` 删除文件或目录，删完复查一次确认真的没了
- `move_path` 移动 / 重命名
- `copy_path` 复制，目录递归
- `append_file` 追加，不覆盖原内容
- `file_info` 查存在性、类型、大小、读写权限、修改时间

同时把系统提示词升到 v6，新增【改自己】一节：明确告诉模型工作区是它的地盘，可以自己建目录、写脚本、攒笔记，不用每次先问。想加能力又没现成工具，就写进 `scripts/` 用 shell 跑。

删除和移动做了路径校验，只能在工作区内动手，越界会被直接拦下，工作区根目录不允许删。

**下载**

- GitHub：https://github.com/mingxiozsg-star/zhiwei-agent/releases
- 加速：https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

官方 QQ 群：1131325287

---

## English

v1.9.2 fixes the bundled Linux failing to start, and hands the workspace back to the model.

**Bundled Linux now actually runs**

In v1.9.1, running a command after installing failed outright:

```
CANNOT LINK EXECUTABLE ".../libproot.so": library "libtalloc.so.2" not found
```

Two naming conventions were fighting each other: the proot binary asks for `libtalloc.so.2` and `libandroid-shmem.so` (the Termux names), but Android only loads `lib*.so` from `nativeLibraryDir`, so the files shipped as `libtalloc.so` and `libandroidshmem.so`. The names never matched, and the dynamic linker gave up.

This release patches the binary itself: `DT_NEEDED` now points at the real in-APK filenames, both libraries' `SONAME`s were updated to match, and the leftover `/data/data/com.termux/files/usr/lib` search path was stripped — keeping it only misdirected resolution. proot's dependencies now resolve cleanly from a single directory.

**The workspace is writable at last**

There used to be exactly three file tools: list, read, write. Creating an empty directory, deleting a file, or renaming one was simply impossible — hence "making a directory goes nowhere".

Six new tools:

- `make_dir` — create directories, parents included
- `delete_path` — remove files or directories, then verify they're actually gone
- `move_path` — move or rename
- `copy_path` — copy, recursive for directories
- `append_file` — append without clobbering existing content
- `file_info` — existence, type, size, read/write permission, mtime

The system prompt moves to v6 with a new "Editing yourself" section: the workspace is the model's own turf, and it can create directories, write scripts and keep notes without asking every time. When it needs a capability no tool provides, it writes one into `scripts/` and runs it through shell.

Deletes and moves are path-checked and confined to the workspace; anything outside is refused, and the workspace root cannot be deleted.

**Download**

- GitHub: https://github.com/mingxiozsg-star/zhiwei-agent/releases
- Mirror: https://ghfast.top/https://github.com/mingxiozsg-star/zhiwei-agent/releases

Official QQ group: 1131325287