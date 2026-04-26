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
    object ReadAloud : Command() {
        override fun shortDescription() = "קורא טקסט מהמצלמה"
    }
    data class NotificationReader(val on: Boolean) : Command() {
        override fun shortDescription() =
            if (on) "הקראת הודעות פעילה" else "הקראת הודעות כבויה"
    }
    object StopSpeaking : Command() {
        override fun shortDescription() = "השתקה"
    }
    object RecordVoiceMessage : Command() {
        override fun shortDescription() = "מקליט הודעה קולית"
    }
}

object CommandParser {

    fun parse(rawText: String): Command? {
        val t = preprocess(rawText)
        val lower = t.lowercase()

        // ----- Stop speaking
        if (Regex("(?:תפסיק לדבר|שתוק|תשתוק|תפסיק להקריא|די|stop|לא לדבר)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.StopSpeaking
        }

        // ----- Read aloud (OCR via camera)
        if (Regex("(?:תקרא|קרא).{0,15}?(?:לי\\s+)?(?:מה כתוב|את הטקסט|טקסט|מה רשום|את הכיתוב|לי טקסט|מהמצלמה)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.ReadAloud
        }
        if (Regex("(?:מה כתוב|מה רשום)\\s*(?:כאן|פה|שם)?", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.ReadAloud
        }
        if (Regex("(?:תאר|תספר).{0,5}?(?:לי\\s+)?(?:תמונה|מה אתה רואה|מה רואים|תוכן)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.ReadAloud
        }

        // ----- Notification reader toggle
        if (Regex("(?:תקרא|תקריא|הקרא|הקריא).{0,15}?(?:הודעות|ההודעות|notifications)|הפעל הקראת הודעות|תפעיל הקראת הודעות", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.NotificationReader(true)
        }
        if (Regex("(?:כבה|תכבה|בטל).{0,15}?(?:הקראת הודעות|הקראה|notifications)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.NotificationReader(false)
        }

        // ----- Voice memo recording
        if (Regex("(?:הקלט|תקליט).{0,10}?(?:הודעה|הודעה קולית|voice message|voice memo)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.RecordVoiceMessage
        }
        if (Regex("(?:שלח|תשלח).{0,10}?הודעה קולית", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Command.RecordVoiceMessage
        }

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
            "(?:תפתח|פתח|תריץ|open|launch)\\s+(?:לי\\s+)?(?:את\\s+)?(?:ה)?(.+)",
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

    /**
     * Normalises common polite prefixes and infinitive verbs into their
     * imperative form so the rest of the patterns can stay simple.
     *
     * Examples:
     *   "אתה יכול לפתוח לי את הוואטסאפ" -> "פתח לי את הוואטסאפ"
     *   "תוכל בבקשה לחייג ליוסי"        -> "חייג ליוסי"
     *   "אפשר לשמוע הודעה של חבר"       -> "שמע הודעה של חבר"
     */
    private fun preprocess(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")

        // Strip polite request prefixes (keep the infinitive ל attached to the verb that follows)
        s = s.replace(
            Regex(
                "^(?:אתה\\s+יכול(?:ה)?|תוכל(?:י)?|אפשר|בבקשה|נא|תהיה\\s+טוב|תהי\\s+טובה)" +
                    "\\s+(?:בבקשה\\s+)?",
                RegexOption.IGNORE_CASE
            ),
            ""
        )

        // Convert infinitive verb at start to imperative form (after stripping prefix above
        // any leading "ל" attached to the verb root remains, fix it now)
        val infinitiveMap = listOf(
            "לפתוח" to "פתח",
            "להפעיל" to "הפעל",
            "להשמיע" to "השמע",
            "לנגן" to "נגן",
            "לשים" to "שים",
            "להתקשר" to "התקשר",
            "לחייג" to "חייג",
            "לחפש" to "חפש",
            "לשלוח" to "שלח",
            "לקרוא" to "קרא",
            "להקריא" to "הקרא",
            "להקליט" to "הקלט",
            "להפסיק" to "הפסק",
            "לכבות" to "כבה",
            "להדליק" to "הדלק",
            "לנווט" to "נווט",
            "לראות" to "ראה",
            "לתאר" to "תאר",
            "לספר" to "ספר",
            "להוריד" to "הורד"
        )
        for ((inf, imp) in infinitiveMap) {
            s = s.replace(Regex("^$inf\\b"), imp)
            s = s.replace(Regex("\\s$inf\\b"), " $imp")
        }

        return s.trim()
    }
}
