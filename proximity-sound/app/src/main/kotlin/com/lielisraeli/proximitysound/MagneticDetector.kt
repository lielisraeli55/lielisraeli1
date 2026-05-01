package com.lielisraeli.proximitysound

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Metal / ferrous-object detector built on the magnetometer.
 *
 * The magnetometer reports the ambient magnetic field in 3 axes (μT). Earth's
 * field is normally 25-65 μT and changes slowly. When a piece of iron / steel
 * (or another phone, which contains magnets in vibrator motor + speakers) gets
 * close, the field magnitude jumps by tens to hundreds of μT.
 *
 * We maintain a slowly-adapting baseline (EWMA) of the resting magnitude and
 * fire `onNear` whenever the current reading deviates from the baseline by
 * more than `thresholdMicroTesla`. The baseline only updates while we're NOT
 * in the triggered state, so a metal object held close indefinitely keeps
 * firing instead of being absorbed into the baseline.
 */
class MagneticDetector(
    private val thresholdMicroTesla: Float = 30f,
    private val onNear: () -> Unit,
    private val onFar: () -> Unit,
) : SensorEventListener {

    private var baseline: Float = -1f
    private val baselineAlpha: Float = 0.02f
    private var triggered: Boolean = false

    // Exposed for UI / debugging.
    var lastMagnitude: Float = 0f
        private set
    var lastDeviation: Float = 0f
        private set
    var lastBaseline: Float = 0f
        private set

    fun reset() {
        baseline = -1f
        triggered = false
        lastMagnitude = 0f
        lastDeviation = 0f
        lastBaseline = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        lastMagnitude = magnitude

        if (baseline < 0f) {
            baseline = magnitude
            lastBaseline = baseline
            return
        }

        val deviation = abs(magnitude - baseline)
        lastDeviation = deviation
        lastBaseline = baseline

        val nowTriggered = deviation > thresholdMicroTesla
        if (nowTriggered && !triggered) {
            triggered = true
            onNear()
        } else if (!nowTriggered && triggered) {
            // Only release once we've returned well below the threshold (hysteresis).
            if (deviation < thresholdMicroTesla * 0.5f) {
                triggered = false
                onFar()
            }
        }

        // Adapt baseline only while at rest, so a held-close magnet keeps firing.
        if (!nowTriggered) {
            baseline = baseline * (1f - baselineAlpha) + magnitude * baselineAlpha
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
