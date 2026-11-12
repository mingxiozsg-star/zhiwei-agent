package com.zhiwei.agent.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 抓一帧就收工的前台服务。
 * Android 10 起，MediaProjection 必须由 mediaProjection 类型的前台服务持有，
 * 否则 getMediaProjection 会直接抛异常。
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_CAPTURE = "com.zhiwei.agent.action.CAPTURE"
        private const val CHANNEL_ID = "zhiwei_screen_capture"
        private const val NOTIF_ID = 0x5C0F
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action != ACTION_CAPTURE) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 抓帧要等一帧到达，放到后台线程做，别卡住主线程
        Thread {
            try {
                captureOnce()
            } catch (_: Exception) {
            } finally {
                Handler(Looper.getMainLooper()).post { stopSelf() }
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "截屏", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("知微正在截屏")
            .setContentText("截取当前屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun captureOnce() {
        val pair = ScreenCapture.take() ?: return
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        val projection = try {
            mpm.getMediaProjection(pair.first, pair.second)
        } catch (_: Exception) {
            null
        } ?: return
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val thread = HandlerThread("zhiwei-capture")
        thread.start()
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        var virtual: VirtualDisplay? = null
        var shot: Bitmap? = null

        reader.setOnImageAvailableListener({ r ->
            val image = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (image != null) {
                try {
                    shot = imageToBitmap(image, width, height)
                } finally {
                    image.close()
                }
                latch.countDown()
            }
        }, handler)

        try {
            virtual = projection.createVirtualDisplay(
                "zhiwei-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
            latch.await(4, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try {
                virtual?.release()
            } catch (_: Exception) {
            }
            try {
                reader.close()
            } catch (_: Exception) {
            }
            try {
                projection.stop()
            } catch (_: Exception) {
            }
            thread.quitSafely()
        }

        val bmp = shot ?: return
        val path = save(bmp)
        bmp.recycle()
        if (path != null) ScreenCapture.onShotSaved(path)
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = width + rowPadding / pixelStride
        val full = try {
            Bitmap.createBitmap(padded, height, Bitmap.Config.ARGB_8888).also {
                it.copyPixelsFromBuffer(buffer)
            }
        } catch (_: Exception) {
            return null
        }
        if (padded == width) return full
        return try {
            val cropped = Bitmap.createBitmap(full, 0, 0, width, height)
            full.recycle()
            cropped
        } catch (_: Exception) {
            full
        }
    }

    private fun save(bmp: Bitmap): String? = try {
        val dir = File(ShellRunner.homeDir(filesDir), "shots")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        f.absolutePath
    } catch (_: Exception) {
        null
    }
}
