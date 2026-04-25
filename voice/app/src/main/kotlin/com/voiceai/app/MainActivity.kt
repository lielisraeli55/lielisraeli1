package com.voiceai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnPerms: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var statusOverlay: TextView
    private lateinit var statusMic: TextView
    private lateinit var statusContacts: TextView
    private lateinit var statusCall: TextView
    private lateinit var statusSms: TextView
    private lateinit var statusNotif: TextView

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btn_overlay)
        btnPerms = findViewById(R.id.btn_perms)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)

        statusOverlay = findViewById(R.id.status_overlay)
        statusMic = findViewById(R.id.status_mic)
        statusContacts = findViewById(R.id.status_contacts)
        statusCall = findViewById(R.id.status_call)
        statusSms = findViewById(R.id.status_sms)
        statusNotif = findViewById(R.id.status_notif)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }
        }

        btnPerms.setOnClickListener { askRuntimePermissions() }

        btnStart.setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, BubbleService::class.java))
            finish()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, BubbleService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun askRuntimePermissions() {
        val toRequest = mutableListOf<String>()
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(it)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (toRequest.isNotEmpty()) permissionsLauncher.launch(toRequest.toTypedArray())
    }

    private fun has(perm: String) = ContextCompat.checkSelfPermission(this, perm) ==
            PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val micOk = has(Manifest.permission.RECORD_AUDIO)
        val contactsOk = has(Manifest.permission.READ_CONTACTS)
        val callOk = has(Manifest.permission.CALL_PHONE)
        val smsOk = has(Manifest.permission.SEND_SMS)
        val notifOk = Build.VERSION.SDK_INT < 33 ||
                has(Manifest.permission.POST_NOTIFICATIONS)

        statusOverlay.text = if (overlayOk) "✓ פעיל" else "✗ נדרש"
        statusMic.text = if (micOk) "✓ פעיל" else "✗ נדרש"
        statusContacts.text = if (contactsOk) "✓ פעיל" else "✗ נדרש"
        statusCall.text = if (callOk) "✓ פעיל" else "✗ נדרש"
        statusSms.text = if (smsOk) "✓ פעיל" else "✗ נדרש"
        statusNotif.text = if (notifOk) "✓ פעיל" else "✗ מומלץ"

        for ((tv, ok) in listOf(
            statusOverlay to overlayOk,
            statusMic to micOk,
            statusContacts to contactsOk,
            statusCall to callOk,
            statusSms to smsOk,
        )) tv.setTextColor(if (ok) 0xFF00FF88.toInt() else 0xFFFF6666.toInt())
        statusNotif.setTextColor(if (notifOk) 0xFF00FF88.toInt() else 0xFFFFAA00.toInt())

        btnOverlay.visibility = if (overlayOk) View.GONE else View.VISIBLE

        val ready = overlayOk && micOk
        btnStart.isEnabled = ready
        btnStart.alpha = if (ready) 1f else 0.5f
    }
}
