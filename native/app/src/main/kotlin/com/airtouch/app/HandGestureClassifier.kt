package com.airtouch.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

enum class HandGesture { POINTING, OPEN_PALM, PINCH, FIST, UNKNOWN }

object HandGestureClassifier {

    fun pinchRatio(lm: List<NormalizedLandmark>): Float {
        val t = lm[4]; val i = lm[8]
        val dx = t.x() - i.x(); val dy = t.y() - i.y(); val dz = t.z() - i.z()
        val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val sx = lm[0].x() - lm[9].x(); val sy = lm[0].y() - lm[9].y()
        val scale = hypot(sx, sy).coerceAtLeast(0.001f)
        return dist / scale
    }

    private fun fingerExtended(lm: List<NormalizedLandmark>, tip: Int, pip: Int, mcp: Int): Boolean {
        val wrist = lm[0]
        val dTip = hypot(lm[tip].x() - wrist.x(), lm[tip].y() - wrist.y())
        val dPip = hypot(lm[pip].x() - wrist.x(), lm[pip].y() - wrist.y())
        val dMcp = hypot(lm[mcp].x() - wrist.x(), lm[mcp].y() - wrist.y())
        return dTip > dPip * 1.02f && dTip > dMcp * 1.10f
    }

    private fun thumbExtended(lm: List<NormalizedLandmark>): Boolean {
        val wrist = lm[0]
        val dTip = hypot(lm[4].x() - wrist.x(), lm[4].y() - wrist.y())
        val dIp = hypot(lm[3].x() - wrist.x(), lm[3].y() - wrist.y())
        return dTip > dIp * 1.05f
    }

    fun classify(lm: List<NormalizedLandmark>, pinchOnThreshold: Float = 0.20f): HandGesture {
        val ratio = pinchRatio(lm)
        if (ratio < pinchOnThreshold) return HandGesture.PINCH

        val idx = fingerExtended(lm, 8, 6, 5)
        val mid = fingerExtended(lm, 12, 10, 9)
        val rng = fingerExtended(lm, 16, 14, 13)
        val pky = fingerExtended(lm, 20, 18, 17)
        val thb = thumbExtended(lm)

        val extendedCount = listOf(idx, mid, rng, pky, thb).count { it }

        return when {
            extendedCount == 0 || (!idx && !mid && !rng && !pky) -> HandGesture.FIST
            idx && mid && rng && pky -> HandGesture.OPEN_PALM
            idx && !mid && !rng && !pky -> HandGesture.POINTING
            else -> HandGesture.UNKNOWN
        }
    }
}
