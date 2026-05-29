package com.lielisraeli.proximitysound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lielisraeli.proximitysound.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private var nearThreshold: Float = 5f
    private var proximityEvents: Int = 0

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            renderMagnetic()
            refreshHandler.postDelayed(this, 200)
        }
    }

    private val metalDetector = MagneticDetector(
        thresholdMicroTesla = 30f,
        onNear = { /* visualised via renderMagnetic */ },
        onFar = { /* visualised via renderMagnetic */ },
    )

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            enableAndStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        proximitySensor?.let { nearThreshold = nearThresholdFor(it) }

        if (proximitySensor == null && magneticSensor == null) {
            binding.statusText.text = getString(R.string.no_sensor)
            binding.toggleButton.isEnabled = false
            return
        }

        binding.sensorInfoText.text = buildString {
            append(getString(R.string.proximity_label))
            append(": ")
            append(if (proximitySensor != null)
                getString(R.string.proximity_info_fmt, proximitySensor!!.maximumRange, nearThreshold)
            else getString(R.string.unavailable))
            append("\n")
            append(getString(R.string.metal_label))
            append(": ")
            append(if (magneticSensor != null) getString(R.string.metal_info)
            else getString(R.string.unavailable))
        }

        binding.toggleButton.setOnClickListener { toggle() }
        binding.testSoundButton.setOnClickListener { playOnceForTest() }
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magneticSensor?.let {
            metalDetector.reset()
            sensorManager.registerListener(metalDetector, it, SensorManager.SENSOR_DELAY_UI)
        }
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        sensorManager.unregisterListener(metalDetector)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val distance = event.values[0]
        proximityEvents++
        val isNear = distance < nearThreshold
        binding.proximityValue.text = getString(R.string.distance_fmt, distance)
        binding.proximityValue.setTextColor(if (isNear) 0xFFFF5252.toInt() else 0xFFFFFFFF.toInt())
        binding.proximityEvents.text = getString(
            R.string.events_fmt,
            proximityEvents,
            if (isNear) getString(R.string.near) else getString(R.string.far)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun renderMagnetic() {
        if (magneticSensor == null) return
        val mag = metalDetector.lastMagnitude
        val dev = metalDetector.lastDeviation
        val baseline = metalDetector.lastBaseline
        val triggered = dev > 30f
        binding.magneticValue.text = getString(R.string.magnetic_fmt, mag, dev)
        binding.magneticValue.setTextColor(if (triggered) 0xFFFF5252.toInt() else 0xFFFFFFFF.toInt())
        binding.magneticBaseline.text = getString(R.string.magnetic_baseline_fmt, baseline)
    }

    private fun toggle() {
        if (prefs().getBoolean(KEY_ENABLED, false)) {
            disableAndStop()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableAndStart()
            }
        }
    }

    private fun enableAndStart() {
        prefs().edit().putBoolean(KEY_ENABLED, true).apply()
        ProximityService.start(this)
        renderState()
    }

    private fun disableAndStop() {
        prefs().edit().putBoolean(KEY_ENABLED, false).apply()
        ProximityService.stop(this)
        renderState()
    }

    private fun renderState() {
        val enabled = prefs().getBoolean(KEY_ENABLED, false)
        binding.statusText.text = getString(
            if (enabled) R.string.status_running else R.string.status_stopped
        )
        binding.toggleButton.text = getString(
            if (enabled) R.string.btn_stop else R.string.btn_start
        )
    }

    private fun playOnceForTest() {
        try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.sound) ?: return
                afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    companion object {
        const val PREFS = "proximity_sound_prefs"
        const val KEY_ENABLED = "enabled"
    }
}
