package com.airtouch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.sqrt

class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private var overlayParams: WindowManager.LayoutParams? = null

    private var landmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val screenSize = DisplayMetrics()

    private var lastTimestampMs = 0L
    private var screenW = 0f
    private var screenH = 0f

    private var isPinched = false
    private var pinchOnFrames = 0
    private var pinchOffFrames = 0
    private var smoothX = 0f
    private var smoothY = 0f
    private var hasPos = false

    companion object {
        const val PINCH_ON = 0.18f
        const val PINCH_OFF = 0.30f
        const val PINCH_STABILITY = 2
        const val SMOOTH = 0.35f
        const val NOTIF_CHANNEL = "airtouch_overlay"
        const val NOTIF_ID = 101
        const val TAG = "AirTouch"
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        initLandmarker()
        startCamera()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL,
                "Air Touch overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Air Touch פעיל")
            .setContentText("שליטה ביד מעל אפליקציות")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun addOverlay() {
        val display = windowManager.defaultDisplay
        display.getRealMetrics(screenSize)
        screenW = screenSize.widthPixels.toFloat()
        screenH = screenSize.heightPixels.toFloat()

        overlayView = OverlayView(this)

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(overlayView, overlayParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun initLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result, _ -> onLandmarkResult(result) }
                .setErrorListener { e -> Log.e(TAG, "Landmarker error", e) }
                .build()
            landmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init landmarker", e)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindAnalysis()
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, mainExecutor)
    }

    private fun bindAnalysis() {
        val provider = cameraProvider ?: return
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(analysisExecutor) { proxy -> processFrame(proxy) }
        provider.unbindAll()
        provider.bindToLifecycle(
            this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis
        )
    }

    private fun processFrame(proxy: ImageProxy) {
        val landmarker = landmarker ?: run { proxy.close(); return }
        val ts = System.currentTimeMillis()
        try {
            val raw = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val oriented = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rotated !== raw) raw.recycle()
                rotated
            } else raw
            val mpImage = BitmapImageBuilder(oriented).build()
            landmarker.detectAsync(mpImage, ts)
        } catch (e: Exception) {
            Log.e(TAG, "frame error", e)
        } finally {
            proxy.close()
        }
    }

    private fun onLandmarkResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            hasPos = false
            isPinched = false
            pinchOnFrames = 0
            pinchOffFrames = 0
            overlayView.update(null, false, 0f)
            return
        }
        val lm = result.landmarks()[0]
        val indexTip = lm[8]
        val thumbTip = lm[4]
        val wrist = lm[0]
        val midMcp = lm[9]

        val rawX = (1f - indexTip.x()) * screenW
        val rawY = indexTip.y() * screenH

        if (!hasPos) {
            smoothX = rawX
            smoothY = rawY
            hasPos = true
        } else {
            smoothX += (rawX - smoothX) * SMOOTH
            smoothY += (rawY - smoothY) * SMOOTH
        }

        val dx = indexTip.x() - thumbTip.x()
        val dy = indexTip.y() - thumbTip.y()
        val dz = indexTip.z() - thumbTip.z()
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val sx = wrist.x() - midMcp.x()
        val sy = wrist.y() - midMcp.y()
        val scale = sqrt(sx * sx + sy * sy).coerceAtLeast(0.001f)
        val ratio = dist / scale

        if (ratio < PINCH_ON) {
            pinchOnFrames++
            pinchOffFrames = 0
            if (!isPinched && pinchOnFrames >= PINCH_STABILITY) {
                isPinched = true
                triggerClick(smoothX, smoothY)
            }
        } else if (ratio > PINCH_OFF) {
            pinchOffFrames++
            pinchOnFrames = 0
            if (isPinched && pinchOffFrames >= PINCH_STABILITY) {
                isPinched = false
            }
        }

        val closeness = ((PINCH_OFF - ratio) / (PINCH_OFF - PINCH_ON))
            .coerceIn(0f, 1f)
        overlayView.update(PointF(smoothX, smoothY), isPinched, closeness)
    }

    private fun triggerClick(x: Float, y: Float) {
        GestureService.dispatchTap(x, y)
        overlayView.flashClick()
    }

    override fun onDestroy() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) { }
        try {
            landmarker?.close()
        } catch (_: Exception) { }
        analysisExecutor.shutdown()
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) { }
        super.onDestroy()
    }
}
