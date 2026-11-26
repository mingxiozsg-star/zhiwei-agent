package com.zhiwei.agent.agent

import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * 本地终端：不以 root 运行，权限等同于应用自身。
 *
 * 这里只保留「一次性命令」的入口（跑完就退，不留状态），
 * 需要保留 cd / export 的持久终端在 [ShellSession] 里。
 */
object ShellRunner {

    private const val MAX_CHARS = 12000

    /** 终端默认工作目录：应用私有 home */
    fun homeDir(contextBase: File): File {
        val home = File(contextBase, "home")
        ensureLayout(home)
        return home
    }

    /**
     * 工作区标准目录：一进来就是能用的样子，不用自己先 mkdir。
     * uploads 放附件、notes 放笔记、scripts 放脚本、bin 放自己塞的可执行文件。
     */
    fun ensureLayout(home: File) {
        if (!home.exists()) home.mkdirs()
        listOf("uploads", "notes", "scripts", "bin", "downloads", ".tmp").forEach {
            val d = File(home, it)
            if (!d.exists()) d.mkdirs()
        }
    }

    /** 会话环境变量：把 PATH 补齐，别让 ls/grep 之类的命令找不到 */
    fun envMap(home: File): Map<String, String> = mapOf(
        "HOME" to home.absolutePath,
        "TMPDIR" to File(home, ".tmp").absolutePath,
        "PWD" to home.absolutePath,
        "SHELL" to "sh",
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8",
        "LC_ALL" to "en_US.UTF-8",
        "PATH" to listOf(
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
    )

    fun run(
        command: String,
        workDir: File,
        timeoutMs: Long = 20000,
        maxChars: Int = MAX_CHARS
    ): ShellResult {
        val cmd = command.trim()
        if (cmd.isEmpty()) return ShellResult(0, "", false)
        return try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.directory(workDir)
            pb.redirectErrorStream(true)
            pb.environment().putAll(envMap(workDir))
            val process = pb.start()
            val sb = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val worker = Thread {
                try {
                    val buf = CharArray(2048)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        synchronized(sb) {
                            if (sb.length < maxChars) sb.append(buf, 0, n)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            worker.isDaemon = true
            worker.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                worker.join(300)
                ShellResult(-1, snapshot(sb) + "\n[命令超时，已终止]", true)
            } else {
                worker.join(500)
                ShellResult(process.exitValue(), snapshot(sb), false)
            }
        } catch (e: Exception) {
            ShellResult(-1, "执行失败：${e.message}", false)
        }
    }

    private fun snapshot(sb: StringBuilder): String = synchronized(sb) {
        val s = sb.toString()
        if (s.length >= MAX_CHARS) s + "\n[输出被截断]" else s
    }
}