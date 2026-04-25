package com.airtouch.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnCamera: Button
    private lateinit var btnNotif: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var statusOverlay: TextView
    private lateinit var statusAccessibility: TextView
    private lateinit var statusCamera: TextView
    private lateinit var statusNotif: TextView

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    private val notifPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btn_overlay)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnCamera = findViewById(R.id.btn_camera)
        btnNotif = findViewById(R.id.btn_notif)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)

        statusOverlay = findViewById(R.id.status_overlay)
        statusAccessibility = findViewById(R.id.status_accessibility)
        statusCamera = findViewById(R.id.status_camera)
        statusNotif = findViewById(R.id.status_notif)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnCamera.setOnClickListener {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }

        btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        btnStart.setOnClickListener {
            val intent = Intent(this, OverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = isAccessibilityServiceEnabled()
        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val notifOk = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        statusOverlay.text = if (overlayOk) "✓ פעיל" else "✗ נדרש"
        statusAccessibility.text = if (accOk) "✓ פעיל" else "✗ נדרש"
        statusCamera.text = if (camOk) "✓ פעיל" else "✗ נדרש"
        statusNotif.text = if (notifOk) "✓ פעיל" else "✗ מומלץ"

        statusOverlay.setTextColor(if (overlayOk) 0xFF00FF88.toInt() else 0xFFFF6666.toInt())
        statusAccessibility.setTextColor(if (accOk) 0xFF00FF88.toInt() else 0xFFFF6666.toInt())
        statusCamera.setTextColor(if (camOk) 0xFF00FF88.toInt() else 0xFFFF6666.toInt())
        statusNotif.setTextColor(if (notifOk) 0xFF00FF88.toInt() else 0xFFFFAA00.toInt())

        btnOverlay.visibility = if (overlayOk) View.GONE else View.VISIBLE
        btnAccessibility.visibility = if (accOk) View.GONE else View.VISIBLE
        btnCamera.visibility = if (camOk) View.GONE else View.VISIBLE
        btnNotif.visibility = if (notifOk) View.GONE else View.VISIBLE

        val ready = overlayOk && accOk && camOk
        btnStart.isEnabled = ready
        btnStart.alpha = if (ready) 1f else 0.5f
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, GestureService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val name = splitter.next()
            val component = ComponentName.unflattenFromString(name)
            if (component != null && component == expectedComponent) return true
        }
        return false
    }
}
