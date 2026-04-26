package com.voiceai.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

object TtsManager {
    private const val TAG = "TtsManager"
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = mutableListOf<String>()

    fun init(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val heb = Locale("he", "IL")
                val supported = tts?.isLanguageAvailable(heb) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (supported >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = heb
                } else {
                    tts?.language = Locale.getDefault()
                    Log.w(TAG, "Hebrew not available, falling back to default")
                }
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
                ready = true
                synchronized(pending) {
                    pending.forEach { speakNow(it) }
                    pending.clear()
                }
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        if (!ready) {
            init(context)
            synchronized(pending) { pending.add(text) }
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val id = "voiceai-${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
