package com.airtouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class GestureService : AccessibilityService() {

    companion object {
        private const val TAG = "AirTouchGesture"
        @Volatile private var instance: GestureService? = null
        private val handler = Handler(Looper.getMainLooper())

        fun dispatchTap(x: Float, y: Float) {
            val s = instance ?: run { Log.w(TAG, "no service for tap"); return }
            handler.post {
                try {
                    val path = Path().apply { moveTo(x, y) }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 60)
                    s.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
                } catch (e: Exception) { Log.e(TAG, "dispatchTap failed", e) }
            }
        }

        fun dispatchSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 220) {
            val s = instance ?: run { Log.w(TAG, "no service for swipe"); return }
            handler.post {
                try {
                    val path = Path().apply {
                        moveTo(fromX, fromY)
                        lineTo(toX, toY)
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                    s.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
                } catch (e: Exception) { Log.e(TAG, "dispatchSwipe failed", e) }
            }
        }

        fun goHome() {
            val s = instance ?: run { Log.w(TAG, "no service for home"); return }
            handler.post { s.performGlobalAction(GLOBAL_ACTION_HOME) }
        }

        fun goBack() {
            val s = instance ?: run { Log.w(TAG, "no service for back"); return }
            handler.post { s.performGlobalAction(GLOBAL_ACTION_BACK) }
        }

        fun goRecents() {
            val s = instance ?: run { return }
            handler.post { s.performGlobalAction(GLOBAL_ACTION_RECENTS) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "GestureService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { /* not used */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
