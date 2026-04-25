package com.voiceai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class VoiceRecorderActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private lateinit var hint: TextView
    private lateinit var timer: Chronometer
    private var stopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recorder)
        hint = findViewById(R.id.hint)
        timer = findViewById(R.id.timer)

        findViewById<View>(R.id.stop_btn).setOnClickListener { stopAndShare() }
        findViewById<View>(R.id.cancel_btn).setOnClickListener { cancel() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "אישור מיקרופון נדרש", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startRecording()
    }

    private fun startRecording() {
        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val file = File(dir, "voicemsg-${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder())
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Toast.makeText(this@VoiceRecorderActivity, "כשל בהקלטה: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        timer.base = SystemClock.elapsedRealtime()
        timer.start()
        hint.text = "מקליט..."
    }

    private fun stopAndShare() {
        if (stopped) return
        stopped = true
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Toast.makeText(this, "אין מספיק קול להקלטה", Toast.LENGTH_SHORT).show()
            outputFile?.delete()
            finish()
            return
        }
        timer.stop()
        recorder = null
        val file = outputFile ?: run { finish(); return }
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "ההקלטה ריקה", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(send, "שלח הקלטה דרך"))
        } catch (e: Exception) {
            Toast.makeText(this, "אין אפליקציה לשיתוף", Toast.LENGTH_SHORT).show()
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }

    private fun cancel() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        finish()
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
