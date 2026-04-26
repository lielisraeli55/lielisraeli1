package com.voiceai.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class VoiceCaptureActivity : AppCompatActivity() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var transcript: TextView
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_voice)
        transcript = findViewById(R.id.transcript)
        hint = findViewById(R.id.hint)
        findViewById<android.view.View>(R.id.cancel).setOnClickListener { finish() }
        TtsManager.init(applicationContext)
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            transcript.text = "מנוע זיהוי דיבור לא זמין במכשיר זה"
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    hint.text = "מקשיב..."
                }
                override fun onBeginningOfSpeech() {
                    hint.text = "מקשיב..."
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    hint.text = "מעבד..."
                }
                override fun onError(error: Int) {
                    transcript.text = errorText(error)
                    hint.text = "שגיאה"
                    finishDelayed(2000)
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    transcript.text = if (text.isEmpty()) "לא נשמע כלום" else text
                    if (text.isNotEmpty()) {
                        val cmd = CommandParser.parse(text)
                        if (cmd != null) {
                            hint.text = cmd.shortDescription()
                            ActionExecutor.execute(this@VoiceCaptureActivity, cmd)
                            finishDelayed(800)
                        } else {
                            hint.text = "לא הצלחתי לזהות פקודה"
                            finishDelayed(2200)
                        }
                    } else {
                        finishDelayed(1500)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    list?.firstOrNull()?.let { transcript.text = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "iw-IL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "iw-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר עכשיו")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 23) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }
        recognizer?.startListening(intent)
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK -> "אין חיבור אינטרנט"
        SpeechRecognizer.ERROR_AUDIO -> "בעיית הקלטה"
        SpeechRecognizer.ERROR_CLIENT -> "שגיאת לקוח"
        SpeechRecognizer.ERROR_NO_MATCH -> "לא הבנתי, נסה שוב"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא נשמע כלום"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "אין הרשאת מיקרופון"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "המערכת עסוקה"
        else -> "שגיאה ($code)"
    }

    private fun finishDelayed(ms: Long) {
        transcript.postDelayed({ finish() }, ms)
    }

    override fun onDestroy() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }
}
