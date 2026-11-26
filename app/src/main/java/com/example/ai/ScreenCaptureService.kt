package com.example.ai

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.ai.ACTION_START_CAPTURE"
        const val ACTION_STOP = "com.example.ai.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data_intent"
        private const val NOTIFICATION_ID = 4109

        private val _isSharing = MutableStateFlow(false)
        val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

        private val _latestFrame = MutableStateFlow<Bitmap?>(null)
        val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastCaptureMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanup()
                stopSelf()
            }
        }, mainHandler)

        startVirtualDisplay()
        _isSharing.value = true
        return START_NOT_STICKY
    }

    private fun startVirtualDisplay() {
        val dm = resources.displayMetrics
        val width = (dm.widthPixels / 2).coerceAtLeast(360)
        val height = (dm.heightPixels / 2).coerceAtLeast(640)
        val density = dm.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                // Keep a fresh scaled snapshot roughly every 1500ms so AI vision has zero lag
                if (now - lastCaptureMs < 1500L && _latestFrame.value != null) return@setOnImageAvailableListener
                val planes = img.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val rawBmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(rawBmp, 0, 0, width, height)
                    if (cropped != rawBmp) rawBmp.recycle()
                    _latestFrame.value = cropped
                    lastCaptureMs = now
                }
            } catch (_: Exception) {
            } finally {
                img.close()
            }
        }, mainHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "NoorAiScreenShare",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "noor_screen_share")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Noor AI Screen Share Active")
            .setContentText("Noor AI can see your current Noor Software screen")
            .setOngoing(true)
            .addAction(0, "Stop sharing", stopIntent)
            .build()
    }

    private fun cleanup() {
        _isSharing.value = false
        _latestFrame.value = null
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { mediaProjection?.stop() }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
