package com.zhiwei.agent.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * 只用来向系统要一次截屏授权，拿到结果就立刻自己关掉。
 * 没有界面（透明主题），不会霸占用户的屏幕。
 */
class ScreenCaptureActivity : Activity() {

    private val reqCode = 0x5C0E

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScreenCapture.isGranted()) {
            ScreenCapture.startCapture(this)
            finish()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            finish()
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), reqCode)
        } catch (_: Exception) {
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqCode) return
        if (resultCode == RESULT_OK && data != null) {
            ScreenCapture.remember(resultCode, data)
            ScreenCapture.startCapture(this)
        }
        finish()
    }
}
