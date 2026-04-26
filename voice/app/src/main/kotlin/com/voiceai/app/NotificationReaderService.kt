package com.voiceai.app

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log

class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifReader"
        private const val PREFS = "voiceai_prefs"
        private const val KEY_ENABLED = "notif_reader_enabled"

        private val IGNORE_PACKAGES = setOf(
            "com.voiceai.app",
            "com.android.systemui",
            "android"
        )

        fun isEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, on: Boolean) {
            prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        }

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun isListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val ours = "${context.packageName}/${NotificationReaderService::class.java.name}"
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(flat)
            while (splitter.hasNext()) if (splitter.next() == ours) return true
            return false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isEnabled(this)) return
        if (sbn.packageName in IGNORE_PACKAGES) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val msg = listOf(text, bigText).firstOrNull { it.isNotEmpty() }.orEmpty()

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) { "אפליקציה" }

        val toSpeak = buildString {
            append("הודעה מ$appLabel.")
            if (title.isNotEmpty()) { append(" "); append(title); append(".") }
            if (msg.isNotEmpty()) { append(" "); append(msg) }
        }
        if (toSpeak.length > "הודעה מ$appLabel.".length) {
            Log.d(TAG, "speaking: $toSpeak")
            TtsManager.speak(applicationContext, toSpeak)
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "NotificationListener connected")
    }
}
