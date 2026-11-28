package com.zhiwei.agent.agent

import android.content.Context
import java.io.File
import java.io.OutputStream
import com.zhiwei.agent.data.AppSettings

/** 一条命令跑完的结果 */
data class TermResult(
    val output: String,
    /** 跑完之后 shell 所在目录，跟真终端一样会变 */
    val cwd: String,
    val exitCode: Int,
    val timedOut: Boolean
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * 常驻 shell 会话。
 *
 * 跟原来「一条命令起一个 sh -c 进程」的差别：这里的 sh 一直活着，
 * `cd`、`export`、`alias` 都留得住，跟在自己电脑上开终端没区别。
 * 每条命令跑完由哨兵行带回退出码和当前目录，用来判断有没有跑完。
 */
class ShellSession(
    private val home: File,
    private val extraEnv: Map<String, String> = emptyMap(),
    private val profile: List<String> = emptyList(),
    /** 用户选定的通道；实际生效的通道由 [Priv.resolve] 降级后写进 [activeMode] */
    private val wantMode: ShellMode = ShellMode.SANDBOX,
    private val context: Context? = null,
    /** 运行环境：安卓原生 shell 还是内置 Linux */
    private val environment: Environment = Environment.ANDROID
) {

    private var process: Process? = null
    private var sink: OutputStream? = null
    private val out = StringBuilder()
    private val execLock = Any()

    /** 会话真正跑起来的通道，供界面与模型查看 */
    @Volatile var activeMode: ShellMode = ShellMode.SANDBOX
        private set

    @Volatile private var cwdPath: String =
        if (environment == Environment.LINUX) "/root" else home.absolutePath
    @Volatile private var running = false

    fun homeDir(): String = home.absolutePath

    fun currentCwd(): String = cwdPath

    fun isAlive(): Boolean =
        if (environment == Environment.LINUX) context?.let { LinuxEnv.installed(it) } == true
        else process?.isAlive == true

    /** 配置没变就不用重建会话；变了就重建，让新环境变量/启动脚本生效 */
    fun matches(env: Map<String, String>, prof: List<String>, mode: ShellMode, linux: Environment): Boolean =
        extraEnv == env && profile == prof && wantMode == mode && environment == linux

    /** Linux 环境不是常驻进程，每条命令单起一次 proot，所以“是否存活”按环境是否装好算 */
    fun isLinux(): Boolean = environment == Environment.LINUX

    /** 会话里能找到命令的目录：系统自带的优先，应用自己的 bin 兜底 */
    private fun pathEnv(): String = listOf(
        File(home, "bin").absolutePath,
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/vendor/xbin",
        "/apex/com.android.runtime/bin",
        "/apex/com.android.art/bin",
        "/data/local/bin",
        "/data/local/tmp"
    ).distinct().joinToString(":")

    private fun ensure() {
        val p = process
        if (p != null && p.isAlive) return
        val tmp = File(home, ".tmp").also { it.mkdirs() }
        val envMap = buildMap {
            put("HOME", home.absolutePath)
            put("TMPDIR", tmp.absolutePath)
            put("PATH", pathEnv())
            put("LANG", "en_US.UTF-8")
            put("LC_ALL", "en_US.UTF-8")
            put("TERM", "xterm-256color")
            put("SHELL", "sh")
            put("PWD", home.absolutePath)
            // 用户配置的额外变量最后写入，允许覆盖上面这些默认值
            putAll(extraEnv)
        }
        // 先按用户选的通道起，起不来就逐级往下退：Root → Shizuku → 沙箱
        val np = startWithFallback(envMap) ?: run {
            activeMode = ShellMode.SANDBOX
            startLocal(envMap)
        }
        process = np
        sink = np.outputStream
        cwdPath = home.absolutePath
        synchronized(out) { out.setLength(0) }
        running = true
        val pump = Thread {
            try {
                val ins = np.inputStream
                val buf = ByteArray(4096)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    val s = String(buf, 0, n)
                    synchronized(out) { out.append(s) }
                }
            } catch (_: Exception) {
            } finally {
                running = false
            }
        }
        pump.isDaemon = true
        pump.start()
        // 会话开起来先对齐环境，免得跟系统默认值不一样
        val profileBlock = profile.joinToString("\n") { it }
        sprint(
            "cd \"${home.absolutePath}\" 2>/dev/null\n" +
                "export PATH=\"${pathEnv()}\"\n" +
                "export HOME=\"${home.absolutePath}\"\n" +
                "export TMPDIR=\"${tmp.absolutePath}\"\n" +
                "export LANG=en_US.UTF-8\n" +
                "export TERM=xterm-256color\n" +
                "umask 022\n" +
                if (profileBlock.isEmpty()) "" else profileBlock + "\n"
        )
    }

    /**
     * 按 wantMode 起进程，失败则逐级降级。
     *
     * Root 用 `su -c sh`，Shizuku 走 newProcess —— 两者都只是把 sh 换成「有更高权限的 sh」，
     * 上层命令执行逻辑一行都不用改。
     */
    private fun startWithFallback(envMap: Map<String, String>): Process? {
        val order = when (wantMode) {
            ShellMode.ROOT -> listOf(ShellMode.ROOT, ShellMode.SHIZUKU, ShellMode.SANDBOX)
            ShellMode.SHIZUKU -> listOf(ShellMode.SHIZUKU, ShellMode.SANDBOX)
            ShellMode.SANDBOX -> listOf(ShellMode.SANDBOX)
            ShellMode.AUTO -> listOf(ShellMode.ROOT, ShellMode.SHIZUKU, ShellMode.SANDBOX)
        }
        for (mode in order) {
            val real = if (mode == ShellMode.SANDBOX) ShellMode.SANDBOX else Priv.resolve(context, mode)
            if (real != mode) continue
            val proc = when (mode) {
                ShellMode.ROOT -> runCatching {
                    ProcessBuilder("su", "-c", "sh")
                        .directory(home)
                        .redirectErrorStream(true)
                        .also { pb -> pb.environment().putAll(envMap) }
                        .start()
                }.getOrNull()
                ShellMode.SHIZUKU -> ShizukuShell.open(arrayOf("sh"), envMap, home.absolutePath)
                else -> startLocal(envMap)
            }
            if (proc != null) {
                activeMode = mode
                return proc
            }
        }
        activeMode = ShellMode.SANDBOX
        return null
    }

    private fun startLocal(envMap: Map<String, String>): Process {
        val pb = ProcessBuilder("sh")
        pb.directory(home)
        pb.redirectErrorStream(true)
        pb.environment().putAll(envMap)
        return pb.start()
    }

    private fun sprint(text: String) {
        try {
            sink?.let {
                it.write(text.toByteArray())
                it.flush()
            }
        } catch (_: Exception) {
        }
    }

    /** 跑一条命令：把输出、退出码与结束时的目录一起带回来 */
    fun run(command: String, timeoutMs: Long = 20000, maxChars: Int = 20000): TermResult {
        val cmd0 = command.trim()
        // Linux 环境不常驻进程：每条命令单起一次 proot，把 cwd 记在会话里做“cd 留得住”
        if (environment == Environment.LINUX) return runLinux(cmd0, timeoutMs, maxChars)
        return runAndroid(cmd0, timeoutMs, maxChars)
    }

    /**
     * 在 proot 里跑一条命令。
     *
     * proot 是一次性进程，没有常驻 shell 可挂，所以 `cd` 要单独处理：把 cd 的落脚点解析出来
     * 存进 cwdPath，下一条命令用 -w 带进去，观感就跟常驻会话一样。
     */
    private fun runLinux(command: String, timeoutMs: Long, maxChars: Int): TermResult {
        val ctx = context ?: return TermResult("Linux 环境需要 Context，但没传进来", cwdPath, -1, false)
        if (!LinuxEnv.installed(ctx)) {
            return TermResult(
                "内置 Linux 还没装好。到「工具与权限 → 运行环境」点「安装 / 修复」即可（rootfs 约 3.9MB，解压一次）",
                cwdPath, -1, false
            )
        }
        if (command.isEmpty()) return TermResult("", cwdPath, 0, false)

        // 纯 cd：只改工作目录，不起进程
        val cdOnly = Regex("^cd\\s+(.+)$").find(command)
        if (cdOnly != null) {
            val want = cdOnly.groupValues[1].trim().trim('"', '\'')
            val next = if (want == "~" || want.isEmpty()) "/root"
            else if (want.startsWith("/")) want
            else joinLinuxPath(cwdPath, want)
            val probe = LinuxEnv.run(ctx, "cd \"$next\" 2>/dev/null && pwd || pwd", 15_000)
            val real = probe.output.lineSequence().lastOrNull { it.trim().startsWith("/") }?.trim()
            if (probe.exitCode == 0 && real != null && real.isNotEmpty() && !real.startsWith("cd: ")) {
                cwdPath = real
                return TermResult("", cwdPath, 0, false)
            }
            return TermResult(probe.output.trim().ifEmpty { "目录不存在：$next" }, cwdPath, probe.exitCode, false)
        }

        // 同一条命令里带 cd，跑完要把落脚点带回来
        val wrapped = "cd \"$cwdPath\" 2>/dev/null; $command\n__zw_rc=\$?\nprintf '__ZWPWD__%s\\n' \"\$PWD\"\nexit \$__zw_rc"
        val r = LinuxEnv.run(ctx, wrapped, timeoutMs)
        var body = r.output
        val marker = "__ZWPWD__"
        val mi = body.lastIndexOf(marker)
        if (mi >= 0) {
            val tail = body.substring(mi + marker.length).lineSequence().firstOrNull()?.trim()
            if (!tail.isNullOrEmpty()) cwdPath = tail
            body = body.substring(0, mi)
        }
        val text = body.trimEnd()
        val clipped = if (text.length > maxChars) text.take(maxChars) + "\n[输出被截断]" else text
        return TermResult(clipped, cwdPath, r.exitCode, false)
    }

    private fun joinLinuxPath(base: String, rel: String): String {
        val parts = (base.trimEnd('/') + "/" + rel).split('/')
        val stack = ArrayDeque<String>()
        for (p in parts) {
            when (p) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(p)
            }
        }
        return "/" + stack.joinToString("/")
    }

    private fun runAndroid(cmd: String, timeoutMs: Long, maxChars: Int): TermResult =
        synchronized(execLock) {
            if (cmd.isEmpty()) return TermResult("", cwdPath, 0, false)
            ensure()
            if (cmd == "exit" || cmd == "logout") {
                reset()
                return TermResult("会话已重启", home.absolutePath, 0, false)
            }
            val s = sink
            if (s == null || process?.isAlive != true) {
                return TermResult("终端会话不可用", cwdPath, -1, false)
            }
            val token = "__ZW" + System.nanoTime().toString(36) + "__"
            synchronized(out) { out.setLength(0) }
            try {
                s.write((cmd + "\n").toByteArray())
                // 哨兵：把退出码和 PWD 打到一行里，用来判断这条命令跑完了
                s.write(("printf '" + token + "%s|%s\\n' \"\$?\" \"\$PWD\"\n").toByteArray())
                s.flush()
            } catch (e: Exception) {
                reset()
                return TermResult("终端会话已断开：${e.message}", cwdPath, -1, false)
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val text = synchronized(out) { out.toString() }
                val idx = text.indexOf(token)
                if (idx >= 0) {
                    val after = text.substring(idx + token.length)
                    val nl = after.indexOf('\n')
                    if (nl >= 0) {
                        val head = after.substring(0, nl)
                        val code = head.substringBefore('|').trim().toIntOrNull() ?: 0
                        val pwd = head.substringAfter('|', "").trim().ifBlank { cwdPath }
                        cwdPath = pwd
                        return TermResult(clip(text.substring(0, idx)), pwd, code, false, )
                    }
                }
                if (!isAlive()) {
                    reset()
                    return TermResult(clip(text) + "\n[会话已结束]", home.absolutePath, -1, false)
                }
                if (System.currentTimeMillis() > deadline) {
                    reset()
                    return TermResult(clip(text) + "\n[命令超时，已中断并重启会话]", cwdPath, -1, true)
                }
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                }
            }
            // 编译器要求的兜底返回，走不到这里
            @Suppress("UNREACHABLE_CODE")
            return TermResult("", cwdPath, -1, true)
        }

    private fun clip(text: String): String {
        val s = text.replace("\r\n", "\n").trimEnd()
        return if (s.length > MAX_OUT) s.take(MAX_OUT) + "\n[输出过长已截断]" else s
    }

    fun reset() {
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        sink = null
        running = false
        cwdPath = if (environment == Environment.LINUX) "/root" else home.absolutePath
        synchronized(out) { out.setLength(0) }
    }

    fun close() = reset()

    companion object {
        private const val MAX_OUT = 24000
    }
}

/**
 * 会话管理：界面上的终端和 AI 调用的终端各用一条会话，互不打断。
 * 应用进程活着期间不用重复起 shell，切页面回来 cwd 还在。
 */
object Term {

    private val sessions = HashMap<String, ShellSession>()

    @Synchronized
    private fun of(
        key: String,
        home: File,
        extraEnv: Map<String, String>,
        profile: List<String>,
        mode: ShellMode,
        context: Context?,
        env: Environment
    ): ShellSession {
        // 运行环境变了就换一条会话，免得安卓 shell 和 proot 共用同一个 cwd 概念
        val slot = "$key@${env.key}"
        val exist = sessions[slot]
        if (exist != null && exist.homeDir() == home.absolutePath && exist.matches(extraEnv, profile, mode, env)) {
            return exist
        }
        exist?.close()
        val s = ShellSession(home, extraEnv, profile, mode, context, env)
        sessions[slot] = s
        return s
    }

    /** 用户在终端页里用的会话 */
    fun ui(home: File, settings: AppSettings, context: Context? = null): ShellSession =
        of("ui", home, settings.terminalEnv(), settings.terminalProfile(), settings.shellMode(), context, settings.linuxEnv())

    /** 模型通过「终端命令」工具用的会话，跟界面上的分开，避免互相打断 */
    fun tool(home: File, settings: AppSettings, context: Context? = null): ShellSession =
        of("tool", home, settings.terminalEnv(), settings.terminalProfile(), settings.shellMode(), context, settings.linuxEnv())
}
