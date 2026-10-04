package com.cangqiong.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.*

class BubbleService : Service() {

    companion object {
        private const val CHANNEL_ID = "cangqiong"
        private const val NOTIF_ID = 1
        @Volatile var isRunning = false
    }

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    private val overlay by lazy { OverlayManager(this) }
    private val translator by lazy { Translator() }
    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastHash = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startFgSpecialUse()
        showBubble()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_CAPTURE") {
            val code = intent.getIntExtra("code", -1)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>("data")
            if (code != -1 && data != null) startTranslate(code, data)
        }
        return START_STICKY
    }

    private fun themed(): Context = ContextThemeWrapper(this, R.style.Theme_Cangqiong)

    private fun startFgSpecialUse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotif(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotif())
        }
    }

    private fun promoteToMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIF_ID, buildNotif(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } catch (_: Exception) {}
        }
    }

    private fun showBubble() {
        try {
            val view = LayoutInflater.from(themed()).inflate(R.layout.bubble, null)
            bubbleParams = WindowManager.LayoutParams(
                140, 140,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40; y = 400
            }

            var dx = 0f; var dy = 0f
            var downX = 0f; var downY = 0f
            var moved = false

            view.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = e.rawX - bubbleParams.x
                        dy = e.rawY - bubbleParams.y
                        downX = e.rawX; downY = e.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = (e.rawX - dx).toInt()
                        bubbleParams.y = (e.rawY - dy).toInt()
                        wm.updateViewLayout(view, bubbleParams)
                        if (Math.abs(e.rawX - downX) > 15 || Math.abs(e.rawY - downY) > 15) moved = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleMenu()
                        true
                    }
                    else -> false
                }
            }

            bubbleView = view
            wm.addView(view, bubbleParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Bubble error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleMenu() {
        try {
            if (menuView != null) {
                wm.removeView(menuView)
                menuView = null
                return
            }
            val view = LayoutInflater.from(themed()).inflate(R.layout.menu, null)
            menuParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bubbleParams.x + 160
                y = bubbleParams.y
            }

            val switch = view.findViewById<MaterialSwitch>(R.id.switchTranslate)

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            val trackColors = ColorStateList(states, intArrayOf(
                Color.parseColor("#39FF14"),
                Color.parseColor("#555555")
            ))
            val thumbColors = ColorStateList(states, intArrayOf(
                Color.parseColor("#39FF14"),
                Color.parseColor("#AAAAAA")
            ))
            switch.trackTintList = trackColors
            switch.thumbTintList = thumbColors

            switch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val i = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("request_capture", true)
                    }
                    startActivity(i)
                } else {
                    stopTranslate()
                }
            }

            var dx = 0f; var dy = 0f
            view.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    dx = e.rawX - menuParams.x
                    dy = e.rawY - menuParams.y
                    true
                } else if (e.action == MotionEvent.ACTION_MOVE) {
                    menuParams.x = (e.rawX - dx).toInt()
                    menuParams.y = (e.rawY - dy).toInt()
                    wm.updateViewLayout(view, menuParams)
                    true
                } else false
            }

            menuView = view
            wm.addView(view, menuParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Menu error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun startTranslate(resultCode: Int, data: Intent) {
        promoteToMediaProjection()
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "cap", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        scope.launch { translator.prepare() }
        captureJob = scope.launch {
            while (isActive) {
                delay(2000)
                captureOnce()
            }
        }
    }

    private fun captureOnce() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bmp = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)

            val h = cropped.hashCode()
            if (h == lastHash) return
            lastHash = h

            val input = InputImage.fromBitmap(cropped, 0)
            recognizer.process(input).addOnSuccessListener { result ->
                val blocks = result.textBlocks.mapNotNull { b ->
                    val rect = b.boundingBox ?: return@mapNotNull null
                    val text = b.text.replace("\n", " ").trim()
                    if (text.length < 2) return@mapNotNull null
                    TranslatedBlock(text, rect)
                }
                scope.launch {
                    val out = blocks.map { blk ->
                        TranslatedBlock(translator.translate(blk.text), blk.rect)
                    }
                    withContext(Dispatchers.Main) { overlay.show(out) }
                }
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun stopTranslate() {
        captureJob?.cancel()
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
        overlay.clear()
        startFgSpecialUse()
    }

    private fun buildNotif(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Cangqiong",
                NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cangqiong")
            .setContentText("Bubble aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        stopTranslate()
        bubbleView?.let { runCatching { wm.removeView(it) } }
        menuView?.let { runCatching { wm.removeView(it) } }
        translator.close()
        recognizer.close()
        isRunning = false
        super.onDestroy()
    }
}
