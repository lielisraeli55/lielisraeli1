package com.airtouch.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.View

class OverlayView(context: Context) : View(context) {

    private var pos: PointF? = null
    private var pinching = false
    private var closeness = 0f
    private var clickProgress = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val handler = Handler(Looper.getMainLooper())

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun update(p: PointF?, pinched: Boolean, near: Float) {
        pos = p
        pinching = pinched
        closeness = near
        handler.post { invalidate() }
    }

    fun flashClick() {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 320
        animator.addUpdateListener {
            clickProgress = it.animatedValue as Float
            invalidate()
        }
        handler.post { animator.start() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pos ?: return

        val color = when {
            pinching -> 0xFFFFAA00.toInt()
            closeness > 0.4f -> blend(0xFF00C8FF.toInt(), 0xFFFFE24D.toInt(), closeness)
            else -> 0xFF00C8FF.toInt()
        }

        ringPaint.color = color
        ringPaint.setShadowLayer(20f, 0f, 0f, color)
        dotPaint.color = color
        dotPaint.setShadowLayer(16f, 0f, 0f, color)

        val ringR = if (pinching) 22f else 28f
        val dotR = if (pinching) 7f else 10f

        canvas.drawCircle(p.x, p.y, ringR, ringPaint)
        canvas.drawCircle(p.x, p.y, dotR, dotPaint)

        if (clickProgress > 0f && clickProgress < 1f) {
            val burstR = ringR + (60f * clickProgress)
            val alpha = ((1f - clickProgress) * 255f).toInt().coerceIn(0, 255)
            burstPaint.color = (0x00FF88 or (alpha shl 24))
            canvas.drawCircle(p.x, p.y, burstR, burstPaint)
        }
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
