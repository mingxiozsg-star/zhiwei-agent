package com.zhiwei.agent.agent

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 命令执行通道。
 *
 * 递进关系：沙箱（应用自身权限）→ Shizuku（adb/shell 权限，uid 2000）→ Root（uid 0）。
 * 权限越高能碰的东西越多，能做的事也越危险，所以由用户在设置里显式选，默认 auto 顺位降级。
 */
enum class ShellMode(val key: String, val label: String) {
    AUTO("auto", "自动"),
    SANDBOX("sandbox", "沙箱"),
    SHIZUKU("shizuku", "Shizuku"),
    ROOT("root", "Root");

    companion object {
        fun of(raw: String): ShellMode {
            val k = raw.trim().lowercase()
            return entries.firstOrNull { it.key == k } ?: AUTO
        }
    }
}

/**
 * 运行环境。跟 [ShellMode] 是两个正交的维度：
 * [ShellMode] 决定借谁的权限，[Environment] 决定用什么用户空间。
 *
 * 安卓原生是 toybox + bionic，轻、快、零启动开销，日常单条命令用它；
 * Linux 是 Alpine + proot，有 apk 包管理、GNU 工具链、完整的 /usr 结构，
 * 编译、装软件、跑脚本用它。两套并存，随时切，不用二选一。
 */
enum class Environment(val key: String, val label: String) {
    ANDROID("android", "安卓原生"),
    LINUX("linux", "Linux");

    companion object {
        fun of(raw: String): Environment {
            val k = raw.trim().lowercase()
            return entries.firstOrNull { it.key == k } ?: ANDROID
        }
    }
}

/**
 * Root 通道。
 *
 * 没有 root 时连 su 二进制都找不到，检测成本极低；有 su 时跑一次 `id` 确认真的拿到了 uid=0，
 * Magisk 那类管理器会在这里弹一次授权框。结果缓存下来，避免每条命令都去敲一遍 su。
 */
object RootShell {

    private val suPaths = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
        "/data/adb/magisk/su"
    )

    /** 0 未知，1 可用，2 不可用 */
    @Volatile
    private var state = 0

    fun hasBinary(): Boolean {
        if (suPaths.any { File(it).exists() }) return true
        val path = System.getenv("PATH") ?: return false
        return path.split(':').any { it.isNotBlank() && File(it, "su").exists() }
    }

    fun granted(force: Boolean = false): Boolean {
        if (!hasBinary()) {
            state = 2
            return false
        }
        if (!force && state != 0) return state == 1
        val ok = probe()
        state = if (ok) 1 else 2
        return ok
    }

    fun reset() {
        state = 0
    }

    /**
     * 试两种常见调用形式：`su -c cmd`（Magisk / KernelSU）和 `su 0 cmd`（老 SuperSU）。
     * 都失败就当没有 root，绝不假装成功。
     */
    private fun probe(): Boolean {
        val forms = listOf(
            arrayOf("su", "-c", "id"),
            arrayOf("su", "0", "id")
        )
        for (form in forms) {
            val out = runCatching {
                val p = ProcessBuilder(*form).redirectErrorStream(true).start()
                val done = p.waitFor(9000, TimeUnit.MILLISECONDS)
                if (!done) {
                    p.destroy()
                    ""
                } else {
                    p.inputStream.bufferedReader().readText()
                }
            }.getOrDefault("")
            if (out.contains("uid=0")) return true
        }
        return false
    }
}

/** Shizuku 通道：借 Shizuku 服务（通常是 shell uid 2000）起进程 */
object ShizukuShell {

    fun alive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(context: Context): Boolean = runCatching {
        if (!alive()) return false
        if (Shizuku.isPreV11()) {
            Shizuku.checkRemotePermission(context.packageName) == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    /** 弹 Shizuku 的授权框；用户没装 Shizuku 或点了拒绝都会返回 false */
    fun request(): Boolean = runCatching {
        Shizuku.requestPermission(0)
        true
    }.getOrDefault(false)

    private fun service(): IShizukuService? = runCatching {
        val binder = Shizuku.getBinder() ?: return@runCatching null
        IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
    }.getOrNull()

    fun open(command: Array<String>, env: Map<String, String>, dir: String): Process? = runCatching {
        val svc = service() ?: return@runCatching null
        val remote = svc.newProcess(command, env.map { "${it.key}=${it.value}" }.toTypedArray(), dir)
        RemoteProcess(remote) as Process
    }.getOrNull()
}

/**
 * 把 Shizuku 远端进程包装成本地 [Process]，这样 [ShellSession] 不用关心底下是哪种通道。
 */
private class RemoteProcess(private val remote: IRemoteProcess) : Process() {

    private val stdin: OutputStream by lazy {
        ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
    }
    private val stdout: InputStream by lazy {
        ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
    }
    private val stderr: InputStream by lazy {
        ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
    }

    override fun getOutputStream(): OutputStream = stdin

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int = remote.waitFor()

    override fun exitValue(): Int = remote.exitValue()

    override fun destroy() {
        runCatching { remote.destroy() }
    }

    override fun isAlive(): Boolean = runCatching { remote.alive() }.getOrDefault(false)
}

/** 通道选择与状态汇总 */
object Priv {

    /** 把用户选的模式换成当前真正能用的最高通道 */
    fun resolve(context: Context?, want: ShellMode): ShellMode = when (want) {
        ShellMode.SANDBOX -> ShellMode.SANDBOX
        ShellMode.ROOT -> if (RootShell.granted()) ShellMode.ROOT else ShellMode.SANDBOX
        ShellMode.SHIZUKU ->
            if (context != null && ShizukuShell.granted(context)) ShellMode.SHIZUKU else ShellMode.SANDBOX
        ShellMode.AUTO -> when {
            RootShell.granted() -> ShellMode.ROOT
            context != null && ShizukuShell.granted(context) -> ShellMode.SHIZUKU
            else -> ShellMode.SANDBOX
        }
    }

    /** 给模型看的能力清单，让它知道自己现在能伸手到哪 */
    fun statusText(context: Context): String = buildString {
        append("沙箱：可用（应用自身权限，能读自己目录）\n")
        val suBin = RootShell.hasBinary()
        val rootOk = suBin && RootShell.granted()
        append("Root：")
        append(
            when {
                rootOk -> "已授权，可以执行任意系统命令"
                suBin -> "检测到 su，但没有获得授权"
                else -> "未检测到 su"
            }
        )
        append('\n')
        append("Shizuku：")
        append(
            when {
                !ShizukuShell.alive() -> "未运行（未安装或服务没启动）"
                ShizukuShell.granted(context) -> "已授权，可用 shell 权限执行命令"
                else -> "已运行，但本应用还没拿到授权"
            }
        )
        if (RootShell.hasBinary() && !rootOk) append("\n提示：超管管理器里给本应用授权后即可用 Root 通道。")
        if (ShizukuShell.alive() && !ShizukuShell.granted(context)) {
            append("\n提示：到设置 → 工具与权限页点「申请 Shizuku 授权」。")
        }
    }
}
