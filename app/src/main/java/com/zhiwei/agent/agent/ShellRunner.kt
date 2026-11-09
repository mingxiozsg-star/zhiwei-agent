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
 * 轻量本地终端执行器。
 * 不以 root 运行，权限等同于应用自身：能读写自己的沙箱目录与有权限的公共目录。
 */
object ShellRunner {

    private const val MAX_CHARS = 12000

    /** 终端默认工作目录：应用私有 home */
    fun homeDir(contextBase: File): File {
        val home = File(contextBase, "home")
        if (!home.exists()) home.mkdirs()
        return home
    }

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