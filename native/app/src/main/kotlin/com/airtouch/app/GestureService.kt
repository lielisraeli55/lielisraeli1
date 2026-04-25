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
        @Volatile
        private var instance: GestureService? = null

        fun dispatchTap(x: Float, y: Float) {
            val s = instance
            if (s == null) {
                Log.w(TAG, "AccessibilityService not connected; ignoring tap")
                return
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    val path = Path().apply { moveTo(x, y) }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 60)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    s.dispatchGesture(gesture, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "dispatchGesture failed", e)
                }
            }
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
