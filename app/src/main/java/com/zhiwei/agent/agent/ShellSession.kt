package com.zhiwei.agent.agent

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
    private val profile: List<String> = emptyList()
) {

    private var process: Process? = null
    private var sink: OutputStream? = null
    private val out = StringBuilder()
    private val execLock = Any()

    @Volatile private var cwdPath: String = home.absolutePath
    @Volatile private var running = false

    fun homeDir(): String = home.absolutePath

    fun currentCwd(): String = cwdPath

    fun isAlive(): Boolean = process?.isAlive == true

    /** 配置没变就不用重建会话；变了就重建，让新环境变量/启动脚本生效 */
    fun matches(env: Map<String, String>, prof: List<String>): Boolean =
        extraEnv == env && profile == prof

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
        val pb = ProcessBuilder("sh")
        pb.directory(home)
        pb.redirectErrorStream(true)
        pb.environment().apply {
            this["HOME"] = home.absolutePath
            this["TMPDIR"] = tmp.absolutePath
            this["PATH"] = pathEnv()
            this["LANG"] = "en_US.UTF-8"
            this["LC_ALL"] = "en_US.UTF-8"
            this["TERM"] = "xterm-256color"
            this["SHELL"] = "sh"
            this["PWD"] = home.absolutePath
            // 用户配置的额外变量最后写入，允许覆盖上面这些默认值
            extraEnv.forEach { (k, v) -> this[k] = v }
        }
        val np = pb.start()
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
    fun run(command: String, timeoutMs: Long = 20000, maxChars: Int = 20000): TermResult =
        synchronized(execLock) {
            val cmd = command.trim()
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
        cwdPath = home.absolutePath
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
        profile: List<String>
    ): ShellSession {
        val exist = sessions[key]
        if (exist != null && exist.homeDir() == home.absolutePath && exist.matches(extraEnv, profile)) {
            return exist
        }
        exist?.close()
        val s = ShellSession(home, extraEnv, profile)
        sessions[key] = s
        return s
    }

    /** 用户在终端页里用的会话 */
    fun ui(home: File, settings: AppSettings): ShellSession =
        of("ui", home, settings.terminalEnv(), settings.terminalProfile())

    /** 模型通过「终端命令」工具用的会话，跟界面上的分开，避免互相打断 */
    fun tool(home: File, settings: AppSettings): ShellSession =
        of("tool", home, settings.terminalEnv(), settings.terminalProfile())
}
