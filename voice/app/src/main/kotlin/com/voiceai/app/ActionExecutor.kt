package com.voiceai.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat

object ActionExecutor {

    fun execute(context: Context, cmd: Command) {
        when (cmd) {
            is Command.PlayOnSpotify -> openSpotifySearch(context, cmd.query)
            is Command.CallByName -> callByName(context, cmd.name)
            is Command.WhatsAppMessage -> sendWhatsApp(context, cmd.name, cmd.message)
            is Command.WhatsAppOpen -> openWhatsApp(context, cmd.name)
            is Command.OpenApp -> openAppByName(context, cmd.name)
            is Command.SendSms -> sendSms(context, cmd.name, cmd.message)
            is Command.WebSearch -> webSearch(context, cmd.query)
            is Command.Navigate -> navigate(context, cmd.destination)
            is Command.SetTimer -> setTimer(context, cmd.minutes)
            is Command.Flashlight -> setFlashlight(context, cmd.on)
        }
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /* ---- Spotify ---- */
    private fun openSpotifySearch(context: Context, query: String) {
        if (query.isBlank()) { toast(context, "מה להפעיל בספוטיפיי?"); return }
        val pm = context.packageManager
        val installed = isPackageInstalled(pm, "com.spotify.music") ||
                isPackageInstalled(pm, "com.spotify.lite")

        val direct = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
        }
        if (installed && launch(context, direct)) return

        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}"))
        if (launch(context, web)) return

        toast(context, "Spotify לא מותקן")
    }

    /* ---- Phone calls ---- */
    private fun callByName(context: Context, name: String) {
        val number = lookupContactNumber(context, name)
        if (number == null) {
            toast(context, "איש קשר לא נמצא: $name")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            launch(context, dial)
            toast(context, "אישור הרשאת CALL_PHONE כדי לחייג ישר")
            return
        }
        val call = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        if (!launch(context, call)) toast(context, "לא הצלחתי לחייג")
    }

    /* ---- WhatsApp ---- */
    private fun sendWhatsApp(context: Context, name: String, message: String) {
        val number = lookupContactNumber(context, name)?.cleanPhone()
        if (number == null) {
            toast(context, "איש קשר לא נמצא: $name")
            return
        }
        val uri = "https://wa.me/$number?text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (!launch(context, intent)) toast(context, "WhatsApp לא מותקן")
    }

    private fun openWhatsApp(context: Context, name: String) {
        val number = lookupContactNumber(context, name)?.cleanPhone()
        if (number == null) {
            toast(context, "איש קשר לא נמצא: $name")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
        if (!launch(context, intent)) toast(context, "WhatsApp לא מותקן")
    }

    /* ---- Open App ---- */
    private fun openAppByName(context: Context, query: String) {
        val pm = context.packageManager
        val q = query.trim().lowercase()

        val knownAliases = mapOf(
            "ספוטיפיי" to "com.spotify.music",
            "ספוטיפי" to "com.spotify.music",
            "spotify" to "com.spotify.music",
            "וואטסאפ" to "com.whatsapp",
            "וטסאפ" to "com.whatsapp",
            "whatsapp" to "com.whatsapp",
            "טלגרם" to "org.telegram.messenger",
            "telegram" to "org.telegram.messenger",
            "פייסבוק" to "com.facebook.katana",
            "facebook" to "com.facebook.katana",
            "אינסטגרם" to "com.instagram.android",
            "instagram" to "com.instagram.android",
            "יוטיוב" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "מצלמה" to "android.media.action.IMAGE_CAPTURE",
            "מפות" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "כרום" to "com.android.chrome",
            "chrome" to "com.android.chrome",
            "טיקטוק" to "com.zhiliaoapp.musically",
            "tiktok" to "com.zhiliaoapp.musically",
            "גוגל" to "com.google.android.googlequicksearchbox",
            "google" to "com.google.android.googlequicksearchbox",
            "שעון" to "com.android.deskclock",
            "מחשבון" to "com.google.android.calculator",
            "calculator" to "com.google.android.calculator",
            "הגדרות" to "com.android.settings",
            "settings" to "com.android.settings",
            "טלפון" to "com.android.dialer",
            "phone" to "com.android.dialer",
            "אנשי קשר" to "com.android.contacts",
            "contacts" to "com.android.contacts",
            "גלריה" to "com.google.android.apps.photos",
            "תמונות" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "spotify" to "com.spotify.music"
        )

        val pkg = knownAliases.entries.firstOrNull { q.contains(it.key) }?.value
        if (pkg != null) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                launch(context, intent); return
            }
        }

        val all = pm.getInstalledApplications(0)
        val match = all.firstOrNull {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.contains(q)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) { launch(context, intent); return }
        }
        toast(context, "אפליקציה לא נמצאה: $query")
    }

    /* ---- SMS ---- */
    private fun sendSms(context: Context, name: String, message: String) {
        val number = lookupContactNumber(context, name)
        if (number == null) {
            toast(context, "איש קשר לא נמצא: $name")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
        }
        if (!launch(context, intent)) toast(context, "לא הצלחתי לפתוח SMS")
    }

    /* ---- Web search ---- */
    private fun webSearch(context: Context, query: String) {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        launch(context, intent)
    }

    /* ---- Navigate ---- */
    private fun navigate(context: Context, destination: String) {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(destination)}")).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (!launch(context, intent)) {
            launch(context, Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/${Uri.encode(destination)}")))
        }
    }

    /* ---- Timer ---- */
    private fun setTimer(context: Context, minutes: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Voice AI")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        launch(context, intent)
    }

    /* ---- Flashlight ---- */
    private fun setFlashlight(context: Context, on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id != null) {
                cm.setTorchMode(id, on)
            } else {
                toast(context, "אין פנס במכשיר")
            }
        } catch (e: Exception) {
            toast(context, "שגיאה בפנס")
        }
    }

    /* ---- Helpers ---- */

    private fun lookupContactNumber(context: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) return null
        val needle = name.trim()
        if (needle.matches(Regex("^[+\\d][\\d\\- ]+$"))) return needle.replace(" ", "").replace("-", "")

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$needle%"), null)?.use { c ->
            if (c.moveToFirst()) return c.getString(1)
        }
        return null
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun String.cleanPhone(): String {
        var s = this.replace("[\\s-]".toRegex(), "")
        if (s.startsWith("0")) s = "972" + s.substring(1)
        if (s.startsWith("+")) s = s.substring(1)
        return s
    }
}
