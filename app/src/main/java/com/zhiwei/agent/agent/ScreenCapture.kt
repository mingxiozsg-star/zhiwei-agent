package com.zhiwei.agent.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 截屏能力的调度中心。
 *
 * Android 不允许应用偷偷抓屏：必须先让用户在系统弹窗里授权一次（MediaProjection），
 * 之后同一进程内就能反复截图。授权这一步只能在一个 Activity 里完成，所以借
 * [ScreenCaptureActivity] 这个透明页转一圈；真正的抓帧在 [ScreenCaptureService] 里做
 * （Android 10 起要求由 mediaProjection 类型的前台服务持有 projection）。
 */
object ScreenCapture {

    /** 最近一次截图落盘路径，供工具轮询 */
    @Volatile
    var lastShotPath: String? = null
        private set

    @Volatile
    private var lastShotAt: Long = 0L

    @Volatile
    private var grantedFlag = false

    @Volatile
    private var resultCode: Int = Activity.RESULT_CANCELED

    @Volatile
    private var resultData: Intent? = null

    fun isGranted(): Boolean = grantedFlag && resultData != null

    fun remember(code: Int, data: Intent?) {
        resultCode = code
        resultData = data
        grantedFlag = data != null && code == Activity.RESULT_OK
    }

    fun take(): Pair<Int, Intent>? {
        val d = resultData ?: return null
        return resultCode to d
    }

    fun reset() {
        grantedFlag = false
        resultData = null
    }

    /** 拉起系统授权弹窗（走透明的中间页） */
    fun requestPermission(context: Context) {
        val intent = Intent(context, ScreenCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun startCapture(context: Context) {
        val svc = Intent(context, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_CAPTURE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (_: Exception) {
        }
    }

    fun onShotSaved(path: String) {
        lastShotPath = path
        lastShotAt = System.currentTimeMillis()
    }

    /** 等一张比 sinceMs 更新的截图；超时返回 null */
    fun awaitShot(sinceMs: Long, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val p = lastShotPath
            if (p != null && lastShotAt > sinceMs) return p
            try {
                Thread.sleep(120)
            } catch (_: InterruptedException) {
                return null
            }
        }
        return null
    }
}
