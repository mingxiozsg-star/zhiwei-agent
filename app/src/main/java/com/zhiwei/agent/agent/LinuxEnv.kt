package com.zhiwei.agent.agent

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * 内置 Linux 环境（Alpine minirootfs + proot）。
 *
 * 为什么是 proot 而不是 chroot：chroot 要 CAP_SYS_CHROOT，应用的 uid 甚至 shell 的 uid 2000
 * 都拿不到，实测 `chroot` 直接 `Operation not permitted`。proot 走 ptrace 在用户态模拟，
 * 不需要任何 capability，代价是有 syscall 开销。
 *
 * 为什么二进制放在 jniLibs：targetSdk 35 上 SELinux 禁止从应用可写目录 exec 任意文件，
 * 只有 nativeLibraryDir 豁免。所以 proot 与它的 loader 以 .so 后缀打进 jniLibs，安装时由
 * 系统解压到 /data/app/.../lib/arm64/，那里可以直接执行。rootfs 是数据不是代码，
 * 解压到 filesDir 没问题。
 */
object LinuxEnv {

    /** 内置 rootfs 的版本号；改了内置包就改这个，触发已装环境重建 */
    const val VERSION = "alpine-3.20.9"

    private const val ASSET_ROOTFS = "linux/alpine-3.20.9-arm64.rootfs"

    /** 内置源，国内可直连 */
    private val MIRRORS = listOf(
        "https://mirrors.aliyun.com/alpine/v3.20",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.20",
        "https://dl-cdn.alpinelinux.org/alpine/v3.20"
    )

    /** 状态：未解压 / 解压中 / 可用 / 失败 */
    enum class State(val label: String) {
        ABSENT("未安装"),
        INSTALLING("安装中"),
        READY("可用"),
        BROKEN("安装失败")
    }

    @Volatile
    var state: State = State.ABSENT
        private set

    @Volatile
    var lastError: String = ""
        private set

    fun rootfsDir(context: Context): File = File(context.filesDir, "linux/rootfs")

    fun prootBin(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    fun loaderBin(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libprootloader.so")

    /** ABI 不对（x86 模拟器之类）时 proot 根本跑不起来，早点说清楚 */
    fun supported(): Boolean {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        return abi == "arm64-v8a"
    }

    fun installed(context: Context): Boolean =
        File(rootfsDir(context), "bin/busybox").exists()

    fun refresh(context: Context) {
        state = if (!supported()) State.BROKEN else if (installed(context)) State.READY else State.ABSENT
        if (!supported()) lastError = "当前 ABI（${android.os.Build.SUPPORTED_ABIS.firstOrNull()}）不支持内置 Linux，仅 arm64-v8a"
    }

    /**
     * 解压内置 rootfs。幂等：已经装好就直接返回。
     * 解压完顺手补齐 resolv.conf —— proot 里没有这个文件就解析不了域名，apk 会报
     * "temporary error" 而看起来像镜像挂了。
     */
    fun install(context: Context, onProgress: (String) -> Unit = {}): Boolean {
        if (installed(context)) {
            refresh(context)
            return true
        }
        if (!supported()) {
            state = State.BROKEN
            lastError = "仅支持 arm64-v8a"
            return false
        }
        return try {
            state = State.INSTALLING
            val dest = rootfsDir(context)
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()
            onProgress("解压 rootfs…")
            context.assets.open(ASSET_ROOTFS).use { raw ->
                GZIPInputStream(raw).use { gz -> extractTar(gz, dest) }
            }
            onProgress("写入 DNS 配置…")
            writeResolvConf(dest)
            writeApkRepos(dest)
            if (!installed(context)) {
                state = State.BROKEN
                lastError = "解压完成但缺少 /bin/busybox"
                return false
            }
            state = State.READY
            lastError = ""
            true
        } catch (e: Throwable) {
            state = State.BROKEN
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    fun uninstall(context: Context): Boolean {
        val ok = runCatching { rootfsDir(context).deleteRecursively() }.getOrDefault(false)
        state = State.ABSENT
        return ok
    }

    fun writeResolvConf(root: File) {
        val f = File(root, "etc")
        f.mkdirs()
        File(f, "resolv.conf").writeText(
            "nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 1.1.1.1\n"
        )
    }

    /** 三个源全写进去，apk 会按顺序取第一个能连的 */
    fun writeApkRepos(root: File) {
        val f = File(root, "etc/apk")
        f.mkdirs()
        val lines = MIRRORS.joinToString("\n") { "$it/main\n$it/community" }
        File(f, "repositories").writeText(lines + "\n")
    }

    /**
     * 拼出 proot 的完整命令行。
     *
     * -0 假装是 root：rootfs 里的 apk 之类会检查 uid，不假装就装不了东西。
     * -b 把 /dev /proc /sys 和用户存储挂进去，否则 rootfs 里看不到设备也读不到文件。
     */
    fun command(
        context: Context,
        commandLine: String,
        cwd: String = "/root",
        extraBinds: List<String> = emptyList()
    ): List<String> {
        val root = rootfsDir(context).absolutePath
        val tmp = File(context.filesDir, "linux/tmp").also { it.mkdirs() }
        val binds = listOf("/dev", "/proc", "/sys") + extraBinds
        val list = mutableListOf(
            prootBin(context).absolutePath,
            "-r", root,
            "-0",
            "-w", cwd,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys"
        )
        binds.drop(3).forEach { list.add("-b"); list.add(it) }
        list.add("/usr/bin/env")
        list.add("-i")
        list.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        list.add("HOME=/root")
        list.add("LANG=C.UTF-8")
        list.add("TERM=xterm-256color")
        list.add("PROOT_TMP_DIR=" + tmp.absolutePath)
        list.add("PROOT_LOADER=" + loaderBin(context).absolutePath)
        list.add("PROOT_NO_SECCOMP=1")
        list.add("/bin/sh")
        list.add("-c")
        list.add(commandLine)
        return list
    }

    /** 跑一条命令，返回合并后的输出。用于设置页的自检和包管理动作 */
    fun run(context: Context, commandLine: String, timeoutMs: Long = 60_000): ShellRun {
        if (!installed(context)) return ShellRun(-1, "Linux 环境还没装好", "/")
        val ld = context.applicationInfo.nativeLibraryDir
        val pb = ProcessBuilder(command(context, commandLine, "/root", listOf("/sdcard")))
        val env = pb.environment()
        env["LD_LIBRARY_PATH"] = ld
        env["PROOT_LOADER"] = loaderBin(context).absolutePath
        env["PROOT_TMP_DIR"] = File(context.filesDir, "linux/tmp").also { it.mkdirs() }.absolutePath
        env["PROOT_NO_SECCOMP"] = "1"
        return runCatching {
            val p = pb.redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread {
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { synchronized(out) { out.append(it).append('\n') } }
                }
            }
            reader.isDaemon = true
            reader.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                p.destroy()
                reader.join(800)
                return@runCatching ShellRun(-2, out.toString() + "\n[超时，已终止]", "/root")
            }
            reader.join(800)
            ShellRun(p.exitValue(), out.toString(), "/root")
        }.getOrElse { ShellRun(-3, "启动失败：${it.message}", "/root") }
    }

    // ---------- tar 解包 ----------

    /**
     * 自己解 tar，不引第三方库：minirootfs 是标准 ustar，只要支持普通文件、目录、软链、硬链，
     * 顺便吃掉 GNU 的 longname（'L'）——Alpine 里软链路径偶尔超 100 字节。
     */
    private fun extractTar(input: java.io.InputStream, dest: File) {
        val buf = ByteArray(512)
        var longName: String? = null
        val destCanon = dest.canonicalPath + File.separator
        while (true) {
            if (!readFully(input, buf)) break
            if (buf.all { it == 0.toByte() }) break
            val name = cstr(buf, 0, 100)
            val mode = cstr(buf, 100, 8).trim().toIntOrNull(8) ?: 0x1ED
            val size = cstr(buf, 124, 12).trim().toIntOrNull(8) ?: 0
            val type = buf[156].toInt().toChar()
            val linkName = cstr(buf, 157, 100)
            val prefix = cstr(buf, 345, 155)

            val fullRaw = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val effective = longName ?: fullRaw
            if (type == 'L') {
                longName = String(readData(input, size), Charsets.UTF_8).trimEnd('\u0000')
                continue
            }
            longName = null

            val rel = effective.trimStart('/').trimEnd('/')
            if (rel.isEmpty() || rel.startsWith("..") || rel.contains("/../")) {
                skipData(input, size)
                continue
            }
            val target = File(dest, rel)
            val canTarget = target.canonicalPath
            if (!canTarget.startsWith(destCanon) && canTarget != dest.canonicalPath) {
                skipData(input, size)
                continue
            }

            when (type) {
                '5' -> {
                    target.mkdirs()
                    skipData(input, size)
                }
                '2' -> {
                    target.parentFile?.mkdirs()
                    target.delete()
                    runCatching { android.system.Os.symlink(linkName, target.absolutePath) }
                    skipData(input, size)
                }
                '1' -> {
                    target.parentFile?.mkdirs()
                    val src = File(dest, linkName.trimStart('/'))
                    target.delete()
                    runCatching { android.system.Os.link(src.absolutePath, target.absolutePath) }
                        .onFailure { runCatching { src.copyTo(target, overwrite = true) } }
                    skipData(input, size)
                }
                '0', '\u0000', '7' -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        var left = size
                        val chunk = ByteArray(64 * 1024)
                        while (left > 0) {
                            val n = input.read(chunk, 0, minOf(chunk.size, left))
                            if (n < 0) break
                            out.write(chunk, 0, n)
                            left -= n
                        }
                    }
                    runCatching { android.system.Os.chmod(target.absolutePath, mode) }
                    skipTail(input, size)
                }
                else -> skipData(input, size)
            }
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off > 0
            off += n
        }
        return true
    }

    private fun readData(input: java.io.InputStream, size: Int): ByteArray {
        val data = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(data, off, size - off)
            if (n < 0) break
            off += n
        }
        skipTail(input, size)
        return if (off == size) data else data.copyOf(off)
    }

    private fun skipData(input: java.io.InputStream, size: Int) {
        if (size <= 0) return
        var left = size.toLong()
        val chunk = ByteArray(8192)
        while (left > 0) {
            val n = input.read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
            if (n < 0) break
            left -= n
        }
        skipTail(input, size)
    }

    /** tar 按 512 对齐，补齐剩余字节 */
    private fun skipTail(input: java.io.InputStream, size: Int) {
        val pad = (512 - (size % 512)) % 512
        if (pad > 0) input.skipNBytesSafely(pad.toLong())
    }

    private fun java.io.InputStream.skipNBytesSafely(n: Long) {
        var left = n
        val chunk = ByteArray(512)
        while (left > 0) {
            val read = read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
            if (read < 0) break
            left -= read
        }
    }

    private fun cstr(buf: ByteArray, off: Int, len: Int): String {
        var end = off
        val limit = off + len
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, off, end - off, Charsets.UTF_8)
    }
}

/** 一次命令执行的结果，跟 ShellRunner 的返回结构保持同形，方便上层复用 */
data class ShellRun(val exitCode: Int, val output: String, val cwd: String)
