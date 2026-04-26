package com.voiceai.app

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

object AudioFilters {

    private const val TAG = "AudioFilters"

    private data class Bundle(
        var ns: NoiseSuppressor? = null,
        var aec: AcousticEchoCanceler? = null,
        var agc: AutomaticGainControl? = null,
    )

    private val attached = mutableMapOf<Int, Bundle>()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == AudioEffect.ERROR_BAD_VALUE) return
        if (attached.containsKey(audioSessionId)) return
        val b = Bundle()

        if (NoiseSuppressor.isAvailable()) {
            try {
                b.ns = NoiseSuppressor.create(audioSessionId)?.also { it.enabled = true }
                Log.i(TAG, "NoiseSuppressor enabled on session $audioSessionId")
            } catch (e: Exception) { Log.w(TAG, "NoiseSuppressor failed", e) }
        } else Log.i(TAG, "NoiseSuppressor not available on this device")

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                b.aec = AcousticEchoCanceler.create(audioSessionId)?.also { it.enabled = true }
                Log.i(TAG, "AcousticEchoCanceler enabled on session $audioSessionId")
            } catch (e: Exception) { Log.w(TAG, "AcousticEchoCanceler failed", e) }
        }

        if (AutomaticGainControl.isAvailable()) {
            try {
                b.agc = AutomaticGainControl.create(audioSessionId)?.also { it.enabled = true }
                Log.i(TAG, "AutomaticGainControl enabled on session $audioSessionId")
            } catch (e: Exception) { Log.w(TAG, "AutomaticGainControl failed", e) }
        }

        attached[audioSessionId] = b
    }

    fun release(audioSessionId: Int) {
        val b = attached.remove(audioSessionId) ?: return
        try { b.ns?.release() } catch (_: Exception) {}
        try { b.aec?.release() } catch (_: Exception) {}
        try { b.agc?.release() } catch (_: Exception) {}
    }

    fun releaseAll() {
        for (id in attached.keys.toList()) release(id)
    }
}
