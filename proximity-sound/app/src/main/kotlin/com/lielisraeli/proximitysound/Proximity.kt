package com.lielisraeli.proximitysound

import android.hardware.Sensor

/**
 * Anything noticeably less than the sensor's reported max range counts as "near".
 *
 * Most phones expose a binary proximity sensor (0 cm = near, maxRange = far), but
 * a growing number — especially newer Samsung / Xiaomi devices that emulate the
 * sensor in software — report mid-range values like 3 cm. A fixed threshold of
 * 1.5 cm misses those. Subtracting a small epsilon from `maximumRange` matches
 * both: binary sensors clearly cross it, analog sensors cross it the moment
 * they drop below max.
 */
fun nearThresholdFor(sensor: Sensor): Float {
    val max = sensor.maximumRange
    // Need at least a small gap below max so noise on "far" readings doesn't trigger.
    return (max - 0.5f).coerceAtLeast(0.5f)
}
