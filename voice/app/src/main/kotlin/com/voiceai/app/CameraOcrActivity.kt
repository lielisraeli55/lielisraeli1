package com.voiceai.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class CameraOcrActivity : AppCompatActivity() {

    private lateinit var preview: PreviewView
    private lateinit var hint: TextView
    private lateinit var resultText: TextView
    private var imageCapture: ImageCapture? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var captured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_ocr)
        preview = findViewById(R.id.preview)
        hint = findViewById(R.id.hint)
        resultText = findViewById(R.id.result)
        findViewById<View>(R.id.capture).setOnClickListener { capture() }
        findViewById<View>(R.id.cancel).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "אין הרשאת מצלמה — אשר באפליקציה תחילה", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startCamera()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!captured && !isFinishing) capture()
        }, 1500)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val previewUC = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUC, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "כשל בפתיחת מצלמה: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        if (captured) return
        val ic = imageCapture ?: return
        captured = true
        hint.text = "מעבד..."
        ic.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    val oriented = if (rotation != 0) {
                        val m = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                    } else bitmap
                    runOcr(oriented)
                } catch (e: Exception) {
                    Log.e("OCR", "capture decode failed", e)
                    runOnUiThread {
                        hint.text = "שגיאה בקריאת תמונה"
                        finishDelayed(2000)
                    }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("OCR", "capture error", exception)
                runOnUiThread {
                    hint.text = "שגיאת מצלמה"
                    finishDelayed(2000)
                }
            }
        })
    }

    private fun runOcr(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                runOnUiThread {
                    if (text.isEmpty()) {
                        hint.text = "לא נמצא טקסט"
                        TtsManager.speak(this, "לא נמצא טקסט")
                        finishDelayed(2500)
                    } else {
                        hint.text = "קורא..."
                        resultText.text = text
                        TtsManager.speak(this, text)
                        finishDelayed((text.length * 80L).coerceAtLeast(4000L).coerceAtMost(20000L))
                    }
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    hint.text = "שגיאת OCR"
                    Toast.makeText(this, e.message ?: "?", Toast.LENGTH_SHORT).show()
                    finishDelayed(2500)
                }
            }
    }

    private fun finishDelayed(ms: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, ms)
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
