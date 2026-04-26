package com.voiceai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceRecorderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceRecorder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private var output: BufferedOutputStream? = null
    private var outputFile: File? = null
    private var totalPcmBytes: Long = 0
    private var sessionId: Int = 0
    private var running = false
    private var stopped = false

    private lateinit var hint: TextView
    private lateinit var timer: Chronometer

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

        try {
            startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            Toast.makeText(this, "כשל בהקלטה: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startRecording() {
        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val file = File(dir, "voicemsg-${System.currentTimeMillis()}.wav")
        outputFile = file

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        val bufferSize = (minBuf * 2).coerceAtLeast(8192)

        sessionId = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()

        audioRecord = if (Build.VERSION.SDK_INT >= 23) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(ENCODING)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setSessionId(sessionId)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferSize
            )
        }
        if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord init failed")
        }

        AudioFilters.attach(audioRecord!!.audioSessionId)

        output = BufferedOutputStream(FileOutputStream(file))
        writePlaceholderWavHeader(output!!)

        audioRecord!!.startRecording()
        running = true
        timer.base = SystemClock.elapsedRealtime()
        timer.start()
        hint.text = "מקליט (סינון רעשים פעיל)..."

        recordThread = Thread {
            val buf = ByteArray(bufferSize)
            try {
                while (running && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord!!.read(buf, 0, buf.size)
                    if (read > 0) {
                        output?.write(buf, 0, read)
                        totalPcmBytes += read
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord.read returned $read")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "record loop error", e)
            }
        }.also { it.start() }
    }

    private fun stopAndShare() {
        if (stopped) return
        stopped = true
        running = false

        try { audioRecord?.stop() } catch (_: Exception) {}
        try { recordThread?.join(800) } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        AudioFilters.release(sessionId)
        try { output?.flush(); output?.close() } catch (_: Exception) {}

        timer.stop()

        val file = outputFile ?: run { finish(); return }
        if (!file.exists() || totalPcmBytes < 4096) {
            file.delete()
            Toast.makeText(this, "ההקלטה קצרה מדי", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            patchWavHeader(file, totalPcmBytes)
        } catch (e: Exception) {
            Log.e(TAG, "patch header failed", e)
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
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
        running = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        AudioFilters.release(sessionId)
        try { output?.close() } catch (_: Exception) {}
        outputFile?.delete()
        finish()
    }

    override fun onDestroy() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        AudioFilters.release(sessionId)
        try { output?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    /* ---------- WAV helpers ---------- */

    private fun writePlaceholderWavHeader(out: BufferedOutputStream) {
        val header = buildWavHeader(0)
        out.write(header)
    }

    private fun patchWavHeader(file: File, pcmBytes: Long) {
        val totalDataLen = pcmBytes
        val totalLen = totalDataLen + 36
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLE(totalLen.toInt()))
            raf.seek(40)
            raf.write(intLE(totalDataLen.toInt()))
        }
    }

    private fun buildWavHeader(dataLen: Int): ByteArray {
        val totalLen = dataLen + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val header = ByteArray(44)
        // "RIFF"
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        intLEInto(header, 4, totalLen)
        // "WAVE"
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // "fmt " sub-chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        intLEInto(header, 16, 16)         // sub-chunk size
        shortLEInto(header, 20, 1)        // PCM
        shortLEInto(header, 22, 1)        // channels
        intLEInto(header, 24, SAMPLE_RATE)
        intLEInto(header, 28, byteRate)
        shortLEInto(header, 32, (1 * 16 / 8))
        shortLEInto(header, 34, 16)
        // "data" sub-chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        intLEInto(header, 40, dataLen)
        return header
    }

    private fun intLE(v: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    }

    private fun intLEInto(dst: ByteArray, offset: Int, v: Int) {
        dst[offset]     = (v       and 0xFF).toByte()
        dst[offset + 1] = (v shr  8 and 0xFF).toByte()
        dst[offset + 2] = (v shr 16 and 0xFF).toByte()
        dst[offset + 3] = (v shr 24 and 0xFF).toByte()
    }

    private fun shortLEInto(dst: ByteArray, offset: Int, v: Int) {
        dst[offset]     = (v       and 0xFF).toByte()
        dst[offset + 1] = (v shr  8 and 0xFF).toByte()
    }
}
