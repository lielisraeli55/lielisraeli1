package com.voiceai.app

sealed class Command {
    abstract fun shortDescription(): String

    data class PlayOnSpotify(val query: String) : Command() {
        override fun shortDescription() = "ספוטיפיי: $query"
    }
    data class CallByName(val name: String) : Command() {
        override fun shortDescription() = "מתקשר ל$name"
    }
    data class WhatsAppMessage(val name: String, val message: String) : Command() {
        override fun shortDescription() = "וואטסאפ ל$name"
    }
    data class WhatsAppOpen(val name: String) : Command() {
        override fun shortDescription() = "וואטסאפ עם $name"
    }
    data class OpenApp(val name: String) : Command() {
        override fun shortDescription() = "פותח: $name"
    }
    data class SendSms(val name: String, val message: String) : Command() {
        override fun shortDescription() = "SMS ל$name"
    }
    data class WebSearch(val query: String) : Command() {
        override fun shortDescription() = "חיפוש: $query"
    }
    data class Navigate(val destination: String) : Command() {
        override fun shortDescription() = "ניווט ל$destination"
    }
    data class SetTimer(val minutes: Int) : Command() {
        override fun shortDescription() = "טיימר $minutes דקות"
    }
    data class Flashlight(val on: Boolean) : Command() {
        override fun shortDescription() = if (on) "פנס דולק" else "פנס כבוי"
    }
}

object CommandParser {

    fun parse(rawText: String): Command? {
        val t = rawText.trim()
            .replace("ה", "ה")
            .replace(Regex("\\s+"), " ")
        val lower = t.lowercase()

        // ----- WhatsApp message: "תשלח וואטסאפ ל<X> <message>"
        Regex(
            "(?:תשלח|שלח).{0,15}?(?:וואטסאפ|whatsapp|וטסאפ).{0,15}?(?:ל|אל)\\s+(\\S+)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.WhatsAppMessage(it.groupValues[1].clean(), it.groupValues[2].clean())
        }
        // ----- WhatsApp open chat
        Regex(
            "(?:תפתח|פתח).{0,15}?(?:וואטסאפ|whatsapp|וטסאפ).{0,15}?(?:עם|ל|אל)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.WhatsAppOpen(it.groupValues[1].clean())
        }
        Regex(
            "(?:וואטסאפ|whatsapp|וטסאפ)\\s+(?:ל|אל|עם)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.WhatsAppOpen(it.groupValues[1].clean())
        }

        // ----- SMS
        Regex(
            "(?:תשלח|שלח).{0,10}?(?:הודעה|sms|אסמס).{0,15}?(?:ל|אל)\\s+(\\S+)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.SendSms(it.groupValues[1].clean(), it.groupValues[2].clean())
        }

        // ----- Spotify
        Regex(
            "(?:תפעיל|תשמיע|תנגן|תשים).{0,30}?(?:בספוטיפיי?|בspotify|ספוטיפיי?|spotify)(?:\\s+(?:את\\s+)?)?(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.PlayOnSpotify(it.groupValues[1].clean())
        }
        Regex(
            "(?:בספוטיפיי?|בspotify|ספוטיפיי?|spotify)(?:\\s+(?:את\\s+)?)(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.PlayOnSpotify(it.groupValues[1].clean())
        }
        Regex(
            "(?:תפעיל|תשמיע|תנגן|תשים)\\s+(?:את\\s+)?(.+?)(?:\\s+בספוטיפיי?| בspotify| בספוטיפיי?|$)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            val q = it.groupValues[1].clean()
            if (q.isNotEmpty()) return Command.PlayOnSpotify(q)
        }

        // ----- Calls
        Regex(
            "(?:תתקשר|תחייג|התקשר|תקשר).{0,5}?(?:ל|אל)?\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            val name = it.groupValues[1].clean()
            if (name.isNotEmpty()) return Command.CallByName(name)
        }

        // ----- Open app
        Regex(
            "(?:תפתח|פתח|תריץ)\\s+(?:את\\s+)?(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            val app = it.groupValues[1].clean()
            if (app.isNotEmpty()) return Command.OpenApp(app)
        }

        // ----- Web search
        Regex(
            "(?:תחפש|חפש|חיפוש).{0,5}?(?:ב?גוגל|בגוגל|google)?\\s*(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            val q = it.groupValues[1].clean()
            if (q.isNotEmpty()) return Command.WebSearch(q)
        }

        // ----- Navigate
        Regex(
            "(?:נווט|תנווט|נווטיני|להגיע)\\s+(?:ל|אל)?\\s*(.+)",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.Navigate(it.groupValues[1].clean())
        }

        // ----- Timer
        Regex(
            "(?:הפעל|תפעיל|תשים|שים).{0,15}?(?:טיימר|שעון).{0,5}?(?:ל|של)?\\s*(\\d+)\\s*(?:דקות|דקה|דק)?",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return Command.SetTimer(it.groupValues[1].toIntOrNull() ?: 5)
        }

        // ----- Flashlight
        if (Regex("(?:הדלק|הפעל|תדליק|תפעיל).{0,5}?(?:פנס|פלאש)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.Flashlight(true)
        }
        if (Regex("(?:כבה|תכבה|סגור|סגור את).{0,5}?(?:פנס|פלאש)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.Flashlight(false)
        }

        // ----- Bare "spotify <query>" (English fallback)
        if (lower.startsWith("spotify ")) {
            return Command.PlayOnSpotify(t.substring(8).trim())
        }
        if (lower.startsWith("call ")) {
            return Command.CallByName(t.substring(5).trim())
        }
        if (lower.startsWith("whatsapp ")) {
            return Command.WhatsAppOpen(t.substring(9).trim())
        }

        return null
    }

    private fun String.clean(): String =
        this.trim().trim('"', '\'', ',', '.', '?', '!').trim()
}
