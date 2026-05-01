package com.lielisraeli.proximitysound

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.lielisraeli.proximitysound.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var maxRange: Float = 5f
    private var nearThreshold: Float = 5f
    private var isNear: Boolean = false
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (proximitySensor == null) {
            binding.statusText.text = getString(R.string.no_sensor)
            binding.distanceText.text = "—"
            return
        }

        maxRange = proximitySensor!!.maximumRange
        // "near" = under 1.5 cm, or under half of the sensor's reported max if smaller.
        nearThreshold = minOf(1.5f, maxRange * 0.5f)
        binding.maxRangeText.text = getString(R.string.max_range_fmt, maxRange)
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayer()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val distance = event.values[0]
        binding.distanceText.text = getString(R.string.distance_fmt, distance)

        val nowNear = distance < nearThreshold
        if (nowNear && !isNear) {
            isNear = true
            binding.statusText.text = getString(R.string.status_near)
            playSound()
        } else if (!nowNear && isNear) {
            isNear = false
            binding.statusText.text = getString(R.string.status_far)
            stopPlayer()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun playSound() {
        stopPlayer()
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // res/raw/sound.* — replace the bundled placeholder with your own audio file.
            val afd = resources.openRawResourceFd(R.raw.sound) ?: return
            afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            prepare()
            start()
        }
        player = mp
    }

    private fun stopPlayer() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: IllegalStateException) { /* already stopped */ }
            it.release()
        }
        player = null
    }
}
