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

class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private var overlayParams: WindowManager.LayoutParams? = null

    private var landmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val screenSize = DisplayMetrics()
    private var screenW = 0f
    private var screenH = 0f

    // Cursor state
    private var smoothX = 0f
    private var smoothY = 0f
    private var hasPos = false

    // Sensitivity zone — small finger motions in the central [LO, HI] camera band
    // cover the full screen, so the user only has to move the FINGER, not the whole arm.
    private val SENS_LO = 0.25f
    private val SENS_HI = 0.75f

    // Pinch state
    private var isPinched = false
    private var pinchOnFrames = 0
    private var pinchOffFrames = 0

    // Gesture state
    private var currentGesture = HandGesture.UNKNOWN
    private var gestureFrames = 0
    private var lastFiredGesture: HandGesture? = null
    private var lastGestureFireMs = 0L

    // Palm position history for swipe detection
    private val palmHistory = ArrayDeque<Triple<Long, Float, Float>>()
    private var lastSwipeMs = 0L

    companion object {
        const val PINCH_ON = 0.20f
        const val PINCH_OFF = 0.32f
        const val PINCH_STABILITY = 2
        const val SMOOTH = 0.45f
        const val GESTURE_STABLE_FRAMES = 3
        const val FIST_HOLD_MS = 600L
        const val SWIPE_COOLDOWN_MS = 800L
        const val GESTURE_COOLDOWN_MS = 700L
        const val PALM_HISTORY_MS = 350L
        const val SWIPE_MIN_DX = 0.25f   // fraction of screen width
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
            val channel = NotificationChannel(NOTIF_CHANNEL, "Air Touch overlay", NotificationManager.IMPORTANCE_LOW)
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
            x = 0; y = 0
        }
        try { windowManager.addView(overlayView, overlayParams) } catch (e: Exception) {
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
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
    }

    private fun processFrame(proxy: ImageProxy) {
        val landmarker = landmarker ?: run { proxy.close(); return }
        val ts = System.currentTimeMillis()
        try {
            val raw = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val oriented = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val r = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                if (r !== raw) raw.recycle()
                r
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
            resetState()
            overlayView.update(null, false, 0f, HandGesture.UNKNOWN)
            return
        }
        val lm = result.landmarks()[0]

        // ----- Cursor (driven by index fingertip with sensitivity expansion) -----
        val ix = lm[8].x().coerceIn(0f, 1f)
        val iy = lm[8].y().coerceIn(0f, 1f)

        // Map [SENS_LO, SENS_HI] band to full screen so small finger motions
        // cover the whole screen and whole-arm drift is clamped.
        val nx = ((SENS_HI - ix) / (SENS_HI - SENS_LO)).coerceIn(0f, 1f)
        val ny = ((iy - SENS_LO) / (SENS_HI - SENS_LO)).coerceIn(0f, 1f)
        val rawX = nx * screenW
        val rawY = ny * screenH

        if (!hasPos) { smoothX = rawX; smoothY = rawY; hasPos = true }
        else {
            smoothX += (rawX - smoothX) * SMOOTH
            smoothY += (rawY - smoothY) * SMOOTH
        }

        // ----- Gesture classification -----
        val gesture = HandGestureClassifier.classify(lm, PINCH_ON)

        // Stable frame counter
        if (gesture == currentGesture) {
            gestureFrames++
        } else {
            currentGesture = gesture
            gestureFrames = 1
        }

        // Track pinch via hysteresis specifically (more responsive than gesture state)
        val pinchRatio = HandGestureClassifier.pinchRatio(lm)
        if (pinchRatio < PINCH_ON) {
            pinchOnFrames++; pinchOffFrames = 0
            if (!isPinched && pinchOnFrames >= PINCH_STABILITY) {
                isPinched = true
                fireTap(smoothX, smoothY)
            }
        } else if (pinchRatio > PINCH_OFF) {
            pinchOffFrames++; pinchOnFrames = 0
            if (isPinched && pinchOffFrames >= PINCH_STABILITY) isPinched = false
        }

        // Palm tracking for swipes — record palm-center positions while OPEN_PALM
        val now = System.currentTimeMillis()
        if (gesture == HandGesture.OPEN_PALM && gestureFrames >= GESTURE_STABLE_FRAMES) {
            val px = (1f - lm[9].x()) * screenW   // mirror
            val py = lm[9].y() * screenH
            palmHistory.addLast(Triple(now, px, py))
            // Drop old entries
            while (palmHistory.isNotEmpty() && now - palmHistory.first().first > PALM_HISTORY_MS) {
                palmHistory.removeFirst()
            }
            checkSwipe(now)
        } else {
            palmHistory.clear()
        }

        // Fist held → home
        if (gesture == HandGesture.FIST &&
            gestureFrames * 33 >= FIST_HOLD_MS &&  // ~33ms per frame (rough)
            now - lastGestureFireMs > GESTURE_COOLDOWN_MS &&
            lastFiredGesture != HandGesture.FIST
        ) {
            lastFiredGesture = HandGesture.FIST
            lastGestureFireMs = now
            GestureService.goHome()
            overlayView.flashAction("HOME")
        }

        // Reset last-fired marker when gesture changes away
        if (gesture != HandGesture.FIST && lastFiredGesture == HandGesture.FIST) {
            lastFiredGesture = null
        }

        val closeness = ((PINCH_OFF - pinchRatio) / (PINCH_OFF - PINCH_ON)).coerceIn(0f, 1f)
        overlayView.update(PointF(smoothX, smoothY), isPinched, closeness, gesture)
    }

    private fun checkSwipe(now: Long) {
        if (palmHistory.size < 2) return
        if (now - lastSwipeMs < SWIPE_COOLDOWN_MS) return
        val first = palmHistory.first()
        val last = palmHistory.last()
        val dx = last.second - first.second
        val dy = last.third - first.third
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)
        if (absDx > SWIPE_MIN_DX * screenW && absDx > absDy * 1.6f) {
            lastSwipeMs = now
            palmHistory.clear()
            val midY = screenH * 0.5f
            if (dx > 0) {
                // Hand moved right (in mirrored screen coords) → swipe page right→left
                GestureService.dispatchSwipe(screenW * 0.85f, midY, screenW * 0.15f, midY, 200)
                overlayView.flashAction("SWIPE ◀")
            } else {
                GestureService.dispatchSwipe(screenW * 0.15f, midY, screenW * 0.85f, midY, 200)
                overlayView.flashAction("SWIPE ▶")
            }
        }
    }

    private fun fireTap(x: Float, y: Float) {
        GestureService.dispatchTap(x, y)
        overlayView.flashClick()
    }

    private fun resetState() {
        hasPos = false
        isPinched = false
        pinchOnFrames = 0; pinchOffFrames = 0
        currentGesture = HandGesture.UNKNOWN
        gestureFrames = 0
        palmHistory.clear()
        lastFiredGesture = null
    }

    override fun onDestroy() {
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        try { landmarker?.close() } catch (_: Exception) {}
        analysisExecutor.shutdown()
        try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        super.onDestroy()
    }
}
