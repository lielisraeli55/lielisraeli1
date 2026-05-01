package com.lielisraeli.proximitysound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lielisraeli.proximitysound.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Result ignored — service still runs even if user denies the
            // notifications permission, but the foreground notification will be silent.
            enableAndStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            binding.statusText.text = getString(R.string.no_sensor)
            binding.toggleButton.isEnabled = false
            return
        }
        binding.maxRangeText.text = getString(R.string.max_range_fmt, sensor.maximumRange)

        binding.toggleButton.setOnClickListener { toggle() }
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun toggle() {
        if (prefs().getBoolean(KEY_ENABLED, false)) {
            disableAndStop()
        } else {
            // Android 13+ needs runtime POST_NOTIFICATIONS for the foreground notification to show.
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

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    companion object {
        const val PREFS = "proximity_sound_prefs"
        const val KEY_ENABLED = "enabled"
    }
}
