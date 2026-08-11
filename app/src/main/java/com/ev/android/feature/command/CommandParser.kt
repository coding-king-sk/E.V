package com.ev.android.feature.command

import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.launcher.AppCatalog
import com.ev.android.feature.media.MediaAction

private enum class Verb { PLAY, OPEN, SEARCH }

/**
 * Turns Hinglish speech/text into an [EvCommand].
 *
 * Examples:
 *  "youtube pe paisa song lagao"          -> PlayMedia("paisa song", YouTube)
 *  "whatsapp kholo"                       -> OpenApp(WhatsApp)
 *  "rehan ko bolo ki main aa raha hoon"   -> SendWhatsApp("rehan", "main aa raha hoon")
 *  "rehan ko call lagao"                  -> CallContact("rehan")
 *  "5 minute ka timer lagao"              -> Timer(300)
 *  "kal subah 8 baje yaad dilana ki dawai" -> Reminder(kal 08:00, "dawai")
 *  "photo lo"                             -> TakePhoto(front = false)
 *  "instagram pe type karo hello"         -> TypeText("hello", Instagram)
 *  "torch on karo aur whatsapp kholo"     -> Multi([Device, OpenApp])
 */
object CommandParser {

    val youtube = AppTarget("YouTube", "com.google.android.youtube", "https://www.youtube.com")
    val whatsapp = AppTarget("WhatsApp", "com.whatsapp", "https://web.whatsapp.com")

    private val playVerbs = listOf(
        "laga do", "lagaa do", "lagado", "laga de", "lagade", "lagao", "lagaao", "laga",
        "baja do", "bajado", "baja de", "bajade", "bajao",
        "chala do", "chalado", "chala de", "chalao",
        "suna do", "sunado", "sunao", "song",
        "play karo", "play kar", "play kardo", "play",
    )

    private val openVerbs = listOf(
        "khol do", "kholdo", "khol de", "kholde", "kholo", "khol",
        "open karo", "open kar", "open kardo", "open",
        "chalu karo", "chalu kar", "start karo", "start",
    )

    private val searchVerbs = listOf(
        "search karo", "search kar", "search kardo", "search",
        "dhoond do", "dhoondo", "dhundo", "khojo", "find",
    )

    private val messageVerbs = listOf(
        "message bhej do", "message bhejo", "message bhejna", "message karo", "message kar",
        "msg bhej do", "msg bhejo", "msg karo",
        "sms bhej do", "sms bhejo", "sms karo", "sms kar do", "sms par",
        "text kar do", "text karo",
        "bhej do", "bhejo", "bhejna", "bhej",
        "likh do", "likho", "likh",
        "keh do", "kehna", "kaho",
        "bol do", "bolo", "bol",
    )

    private val callVerbs = listOf(
        "call laga do", "call lagao", "call kar do", "call kardo", "call karo",
        "phone laga do", "phone lagao", "phone karo",
        "dial karo", "dial", "call",
    )

    private val reminderVerbs = listOf(
        "yaad dila do", "yaad dilado", "yaad dila dena", "yaad dila dijiye",
        "yaad dilana", "yaad dilao", "yaad dila", "yaad karana", "yaad rakhna",
        "reminder laga do", "reminder lagao", "reminder set karo", "reminder",
        "remind me", "remind",
    )

    /**
     * Photo ke phrases.
     *
     * Akela "photo" jaan-boojh ke nahi rakha — warna "photo gallery kholo" bhi
     * camera chala deta. "selfie" akela theek hai, uska aur koi matlab nahi.
     */
    private val photoVerbs = listOf(
        "photo le lo", "photo lelo", "photo lo", "photo le",
        "photo khicho", "photo kheecho", "photo khich", "photo kheench",
        "photo click karo", "photo click", "photo nikalo",
        "picture lo", "pic lo", "tasveer lo", "snap lo",
        "selfie le lo", "selfie lo", "selfie le", "selfie khicho", "selfie",
        "take a photo", "take photo", "click a photo",
    )

    private val videoVerbs = listOf(
        "video bana do", "video banao", "video bana",
        "video record karo", "video record", "record video",
        "video shoot karo", "video le lo", "video lo",
        "recording shuru karo", "recording shuru",
    )

    private val frontCameraWords = listOf(
        "selfie", "front camera", "front cam", "aage wala camera", "saamne wala camera",
        "meri photo", "apni photo", "mera video",
    )

    private val typeVerbs = listOf(
        "type kar do", "type kardo", "type karo", "type kar", "type",
    )

    /** Only stripped from the START/END of a query so song names stay intact. */
    private val fillerWords = setOf(
        "mujhe", "muje", "please", "plz", "zara", "bhai", "abhi", "to", "na", "yaar",
    )

    /**
     * Jodne wale chhote shabd.
     *
     * "per", "pey" aur "peh" isliye hain kyunki Google ka recognizer Hinglish
     * me "pe" ko aksar "per" likh deta hai ("whatsapp per armaan ko…"). Inhe
     * yahan na rakhne se ye contact ke naam ka hissa ban jate the aur phir koi
     * contact match hi nahi hota tha.
     */
    private val connectors = setOf(
        "pe", "per", "pey", "peh", "par", "pr",
        "me", "mein", "main", "on", "in", "se", "ka", "ki", "ke",
    )

    /** Ek hi regex ke andar sab connectors — dono jagah yahi list chalti hai. */
    private const val CONNECTOR_PATTERN = "pe|per|pey|peh|par|pr|me|mein|main|on|in"

    private val messageNoiseWords = setOf(
        "message", "msg", "text", "sms",
        "whatsapp", "whatsaap", "watsapp", "whatsup", "wa",
    )

    private val callNoiseWords =
        setOf("call", "phone", "dial", "lagao", "laga", "karo", "kar", "kardo", "do", "milao")

    /** Reminder ke body me se ye sab nikal dena hai — ye time/verb ke shabd hain. */
    private val reminderNoiseWords = setOf(
        "yaad", "dila", "dilado", "dilana", "dilao", "dena", "dijiye", "karana", "rakhna",
        "reminder", "remind", "set", "lagao", "laga", "karo", "kar", "kardo", "do",
        "kal", "parso", "aaj", "subah", "shaam", "sham", "raat", "dopahar", "night",
        "morning", "evening", "baje", "baad", "bad", "later", "am", "pm",
        "minute", "minutes", "min", "mins", "minit", "second", "seconds", "sec", "secs",
        "ghanta", "ghante", "ghanto", "hour", "hours",
    )

    private val targetSplitRegex =
        Regex("^(.{2,40}?)\\s+($CONNECTOR_PATTERN)\\s+(.+)$")

    private val whatsappPrefixRegex =
        Regex("^(whatsapp|whats app|whatsaap|watsapp|whatsup|wa)\\s+($CONNECTOR_PATTERN)\\s+")

    private val koRegex = Regex("(^|\\s)ko($|\\s)")

    private val kiRegex = Regex("(^|\\s)(ki|that)\\s+")

    /** "torch on karo AUR whatsapp kholo" — yahan se do kaam alag hote hain. */
    private val joinRegex =
        Regex("\\s+(?:aur|phir|fir|uske baad|iske baad|baad me|then|and)\\s+")

    // ---------------------------------------------------- media vocabulary

    private val nextWords = listOf(
        "next gaana", "next gana", "next song", "next track", "next video", "next",
        "agla gaana", "agla gana", "agla song", "aage wala gaana",
        "gaana badlo", "gana badlo", "song badlo", "gaana change karo",
    )

    private val prevWords = listOf(
        "pichla gaana", "pichla gana", "pichla song", "pehle wala gaana",
        "previous song", "previous track", "previous", "last song",
    )

    private val pauseWords = listOf(
        "gaana roko", "gana roko", "song roko", "music roko", "video roko",
        "gaana rok do", "gana rok do", "rok do", "roko",
        "gaana band karo", "gana band karo", "song band karo", "music band karo",
        "pause karo", "pause kar do", "pause",
    )

    private val resumeWords = listOf(
        "gaana chalu karo", "gana chalu karo", "music chalu karo", "song chalu karo",
        "resume karo", "resume", "phir se chalao",
    )

    private val forwardWords = listOf(
        "aage badhao", "aage badha do", "aage karo", "aage kar do", "aage",
        "fast forward", "forward karo", "forward", "skip karo",
    )

    private val rewindWords = listOf(
        "peeche karo", "peeche kar do", "piche karo", "peeche", "piche",
        "rewind karo", "rewind", "replay",
    )

    private val whichWords = listOf("kaun sa", "kaunsa", "kaun si", "konsa", "kon sa", "which")

    private val identifyWords = listOf(
        "gaana pehchano", "gana pehchano", "song pehchano", "gaana pehchan",
        "identify song", "identify gaana", "shazam",
    )

    /** Words that may harmlessly hang around a media command. */
    private val mediaSubjectWords = setOf(
        "gaana", "gana", "gane", "song", "songs", "music", "track", "video",
        "ye", "yeh", "ise", "isko", "is", "wala",
    )

    // ---------------------------------------------------- device vocabulary

    private val onWords = listOf("on", "chalu", "chaalu", "jala", "jalao", "jala do", "enable", "start")
    private val offWords = listOf("off", "band", "bandh", "bujha", "bujhao", "bujha do", "disable", "close")
    private val upWords = listOf("badha", "badhao", "bada do", "increase", "up", "tez", "zyada", "jyada")
    private val downWords = listOf("kam", "ghata", "ghatao", "decrease", "down", "dheema", "halka")
    private val maxWords = listOf("full", "max", "maximum", "poora", "pura")
    private val minWords = listOf("min", "minimum", "sabse kam", "lowest")

    private val torchWords = listOf("torch", "tourch", "flashlight", "flash light", "flash", "batti")
    private val wifiWords = listOf("wifi", "wi-fi", "wi fi", "waifai", "vaifai")
    private val bluetoothWords = listOf("bluetooth", "blutooth", "bluetuth", "blue tooth")
    private val dataWords = listOf("mobile data", "internet", "data on", "data off", "data")
    private val airplaneWords = listOf("airplane", "aeroplane", "flight mode", "hawai jahaj")
    private val volumeWords = listOf("volume", "awaz", "aawaz", "aavaz", "sound")
    private val brightnessWords = listOf("brightness", "roshni", "chamak", "screen light")
    private val muteWords = listOf("mute", "chup")
    private val silentWords = listOf("silent")
    private val vibrateWords = listOf("vibrate", "vibration", "kampan")
    private val ringerWords = listOf("ringtone mode", "ring mode", "normal mode", "general mode")
    private val screenshotWords = listOf("screenshot", "screen shot", "screen capture")
    private val lockWords = listOf("screen lock", "lock screen", "phone lock", "lock kar", "lock karo")
    private val homeWords = listOf("home jao", "home button", "home screen", "home pe jao")
    private val backWords = listOf("back jao", "back karo", "peeche jao", "wapas jao")
    private val recentsWords = listOf("recent apps", "recent app", "recents")
    private val notificationWords = listOf("notification", "notifications")
    private val settingsWords = listOf("settings", "setting")

    /**
     * Entry point.
     *
     * Pehle dekhta hai ki ek hi vaakya me kai kaam to nahi bole gaye. Agar haan,
     * aur saare hisse samajh bhi aa gaye, to [EvCommand.Multi] banta hai.
     */
    fun parse(rawInput: String, installedApps: List<InstalledApp>): EvCommand {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return EvCommand.Unknown(raw)

        val normalized = normalize(raw)

        val parts = splitCommands(normalized)
        if (parts.size > 1) {
            val commands = parts.map { parseSingle(it, it, installedApps) }
            // Ek bhi hissa samajh na aaye to todna galat tha — poora vaakya
            // ek hi command hoga (jaise gaane ke naam me "aur" aa gaya ho).
            if (commands.none { it is EvCommand.Unknown }) {
                return EvCommand.Multi(commands)
            }
        }

        return parseSingle(normalized, raw, installedApps)
    }

    /**
     * "aur / phir / then" pe todo — par sirf tab jab risky na ho.
     *
     * Message aur reminder ke body me "aur" bahut aata hai ("bolo ki main aa
     * raha hoon aur khana laga do"), isliye "ki" wale vaakya kabhi nahi tootte.
     */
    private fun splitCommands(text: String): List<String> {
        if (kiRegex.containsMatchIn(text)) return listOf(text)
        if (!joinRegex.containsMatchIn(text)) return listOf(text)

        return joinRegex.split(text)
            .map { it.trim() }
            .filter { it.length >= 3 }
    }

    private fun parseSingle(
        normalized: String,
        raw: String,
        installedApps: List<InstalledApp>,
    ): EvCommand {
        if (normalized.isEmpty()) return EvCommand.Unknown(raw)

        // Device toggles pehle — "torch on karo" ko app-launcher parse na kar de.
        parseDevice(normalized)?.let { return it }

        // "photo lo", "30 second ka video banao"
        parseCamera(normalized)?.let { return it }

        // "instagram pe type karo hello bhai"
        parseType(normalized, installedApps)?.let { return it }

        // "ye gaana kaun sa hai", "next gaana", "gaana roko", "10 second aage"
        parseMedia(normalized)?.let { return it }

        // "kal subah 8 baje yaad dilana ki dawai leni hai"
        //
        // Timer/alarm se pehle, kyunki reminder me bhi "baje" hota hai.
        parseReminder(normalized)?.let { return it }

        // "5 minute ka timer", "subah 7 baje alarm"
        parseTimerOrAlarm(normalized)?.let { return it }

        // "rehan ko call lagao"
        parseCall(normalized)?.let { return it }

        // Messaging ki shape ("<naam> ko ... bhejo") bahut distinctive hai.
        parseMessage(normalized)?.let { return it }

        var text = normalized
        var target: AppTarget? = null

        // "<app> pe <rest>" -> pull the app out of the front of the sentence.
        targetSplitRegex.find(text)?.let { match ->
            val resolved = resolveApp(match.groupValues[1], installedApps)
            if (resolved != null) {
                target = resolved
                text = match.groupValues[3].trim()
            }
        }

        val verb = detectVerb(text)
        val query = cleanQuery(if (verb != null) stripVerb(text, verb) else text)
        val app = target

        return when (verb) {
            Verb.PLAY -> when {
                query.isEmpty() -> app?.let { EvCommand.OpenApp(it) } ?: EvCommand.Unknown(raw)
                app == null && resolveApp(query, installedApps) != null ->
                    EvCommand.OpenApp(resolveApp(query, installedApps)!!)
                else -> EvCommand.PlayMedia(query, app ?: youtube)
            }

            Verb.SEARCH -> when {
                query.isEmpty() -> app?.let { EvCommand.OpenApp(it) } ?: EvCommand.Unknown(raw)
                else -> EvCommand.SearchInApp(query, app ?: youtube)
            }

            Verb.OPEN -> (app ?: resolveApp(query, installedApps))
                ?.let { EvCommand.OpenApp(it) }
                ?: EvCommand.Unknown(raw)

            null -> (resolveApp(text, installedApps) ?: app)
                ?.let { EvCommand.OpenApp(it) }
                ?: EvCommand.Unknown(raw)
        }
    }

    // --------------------------------------------------------------- camera

    /**
     * "photo lo" / "selfie lo" / "30 second ka video banao"
     *
     * Video ka time bola ho to wahi, warna 15 second — itna kaafi hota hai aur
     * galti se ghanto ki recording nahi chalti.
     */
    private fun parseCamera(text: String): EvCommand? {
        val front = frontCameraWords.any { containsWord(text, it) }

        if (matchPhrase(text, videoVerbs) != null) {
            val seconds = TimeParser.durationSeconds(text) ?: DEFAULT_VIDEO_SECONDS
            return EvCommand.RecordVideo(front = front, seconds = seconds)
        }

        if (matchPhrase(text, photoVerbs) != null) {
            return EvCommand.TakePhoto(front = front)
        }

        return null
    }

    // ----------------------------------------------------------------- type

    /**
     * "instagram pe type karo hello bhai"
     *
     * App ka naam aage se nikalta hai, aur likhne wala text verb ke baad se.
     * Agar text verb ke pehle bola gaya ho ("hello bhai type karo") to wo bhi
     * chalta hai.
     */
    private fun parseType(text: String, installedApps: List<InstalledApp>): EvCommand? {
        val verb = matchPhrase(text, typeVerbs) ?: return null

        var work = text
        var target: AppTarget? = null

        targetSplitRegex.find(work)?.let { match ->
            val resolved = resolveApp(match.groupValues[1], installedApps)
            if (resolved != null) {
                target = resolved
                work = match.groupValues[3].trim()
            }
        }

        val match = phraseRegex(verb).find(work) ?: return null

        val after = work.substring(match.range.last + 1)
            .trim()
            .removePrefix("ki ")
            .removePrefix("that ")
            .trim()
        val before = work.substring(0, match.range.first).trim()

        val body = cleanQuery(if (after.isNotEmpty()) after else before)
        if (body.isBlank()) return null

        return EvCommand.TypeText(text = body, target = target)
    }

    // ---------------------------------------------------------------- media

    /**
     * Media commands.
     *
     * next/pause/resume tabhi maante hain jab command me aur kuch bacha na ho.
     * Warna "paisa gaana chalu karo" bhi resume ban jata, jabki wahan user ek
     * naya gaana chalwana chahta hai.
     */
    private fun parseMedia(text: String): EvCommand? {
        if (isSongIdentify(text)) return EvCommand.IdentifySong

        matchPhrase(text, forwardWords)?.let { return EvCommand.Media(MediaAction.FORWARD) }
        matchPhrase(text, rewindWords)?.let { return EvCommand.Media(MediaAction.REWIND) }

        matchPhrase(text, nextWords)?.let {
            if (onlySubjectLeft(text, it)) return EvCommand.Media(MediaAction.NEXT)
        }
        matchPhrase(text, prevWords)?.let {
            if (onlySubjectLeft(text, it)) return EvCommand.Media(MediaAction.PREVIOUS)
        }
        matchPhrase(text, pauseWords)?.let {
            if (onlySubjectLeft(text, it)) return EvCommand.Media(MediaAction.PAUSE)
        }
        matchPhrase(text, resumeWords)?.let {
            if (onlySubjectLeft(text, it)) return EvCommand.Media(MediaAction.PLAY)
        }

        return null
    }

    private fun isSongIdentify(text: String): Boolean {
        if (identifyWords.any { containsWord(text, it) }) return true

        val hasSubject = listOf("gaana", "gana", "song", "music", "track")
            .any { containsWord(text, it) }
        val asksWhich = whichWords.any { containsWord(text, it) }

        return hasSubject && asksWhich
    }

    private fun onlySubjectLeft(text: String, phrase: String): Boolean {
        val rest = text.replace(phraseRegex(phrase), " ")
        return normalize(rest)
            .split(" ")
            .filter { it.isNotBlank() }
            .all { it in mediaSubjectWords || it in fillerWords || it in connectors }
    }

    // ------------------------------------------------------------ reminder

    private fun parseReminder(text: String): EvCommand? {
        val verb = matchPhrase(text, reminderVerbs) ?: return null
        val at = TimeParser.reminderMillis(text) ?: return null

        val kiMatch = kiRegex.find(text)
        val afterKi = if (kiMatch != null) text.substring(kiMatch.range.last + 1).trim() else ""

        val body = if (afterKi.isNotEmpty()) {
            afterKi
        } else {
            text.replace(phraseRegex(verb), " ")
                .split(" ")
                .filter { token ->
                    token.isNotBlank() &&
                        token.toIntOrNull() == null &&
                        !token.contains(":") &&
                        token !in reminderNoiseWords &&
                        token !in connectors &&
                        token !in fillerWords
                }
                .joinToString(" ")
        }

        return EvCommand.Reminder(at = at, text = body.ifBlank { "Reminder" })
    }

    // ------------------------------------------------------- timer / alarm

    private fun parseTimerOrAlarm(text: String): EvCommand? {
        if (containsWord(text, "alarm")) {
            val time = TimeParser.clockTime(text) ?: return null
            return EvCommand.Alarm(time.first, time.second)
        }

        if (containsWord(text, "timer")) {
            val seconds = TimeParser.durationSeconds(text) ?: return null
            return EvCommand.Timer(seconds, null)
        }

        return null
    }

    // ----------------------------------------------------------------- call

    private fun parseCall(text: String): EvCommand? {
        val verb = matchPhrase(text, callVerbs) ?: return null

        // "call" aur "dial" akele bahut aam shabd hain: "call of duty kholo",
        // "dial pad kholo" — inhe call samajh ke dialer khol dena galat hai.
        val explicitPhrase = verb.contains(" ")
        val hasKo = koRegex.containsMatchIn(text)
        if (!explicitPhrase && !hasKo) return null

        val withoutVerb = text.replace(phraseRegex(verb), " ").replace(koRegex, " ")
        val name = normalize(withoutVerb)
            .split(" ")
            .filter {
                it.isNotBlank() && it !in callNoiseWords &&
                    it !in connectors && it !in fillerWords
            }
            .joinToString(" ")

        if (name.isEmpty()) return null
        return EvCommand.CallContact(name)
    }

    // -------------------------------------------------------------- message

    private fun parseMessage(text: String): EvCommand? {
        val mentionsWhatsApp = messageNoiseWords
            .filter { it.startsWith("w") }
            .any { containsWord(text, it) } || text.contains("whats app")
        val mentionsSms = containsWord(text, "sms") || text.contains("text message")
        val stripped = text.replace(whatsappPrefixRegex, "").trim()

        val koMatch = Regex("\\s+ko\\s+").find(stripped) ?: return null

        val namePart = cleanName(stripped.substring(0, koMatch.range.first))
        var rest = stripped.substring(koMatch.range.last + 1).trim()
        rest = rest.replace(whatsappPrefixRegex, "").trim()

        val verb = matchPhrase(rest, messageVerbs)

        if (verb == null && !mentionsWhatsApp && !mentionsSms) return null
        if (namePart.isEmpty()) return null

        val body = if (verb == null) {
            stripNoise(rest)
        } else {
            val match = phraseRegex(verb).find(rest)
            if (match == null) {
                stripNoise(rest)
            } else {
                val before = rest.substring(0, match.range.first).trim()
                val after = rest.substring(match.range.last + 1)
                    .trim()
                    .removePrefix("ki ")
                    .removePrefix("that ")
                    .trim()
                stripNoise(if (after.isNotEmpty()) after else before)
            }
        }

        if (body.isEmpty()) return null

        return if (mentionsSms && !mentionsWhatsApp) {
            EvCommand.SendSms(contactName = namePart, message = body)
        } else {
            EvCommand.SendWhatsApp(contactName = namePart, message = body)
        }
    }

    // --------------------------------------------------------------- device

    private fun parseDevice(text: String): EvCommand.Device? {
        fun has(words: List<String>) = words.any { word -> containsWord(text, word) }

        val wantsOn = has(onWords)
        val wantsOff = has(offWords)
        val wantsUp = has(upWords)
        val wantsDown = has(downWords)
        val wantsMax = has(maxWords)
        val wantsMin = has(minWords)
        val hasModifier = wantsOn || wantsOff || wantsUp || wantsDown || wantsMax || wantsMin

        val action = when {
            has(torchWords) -> when {
                wantsOff -> DeviceAction.TORCH_OFF
                wantsOn -> DeviceAction.TORCH_ON
                else -> DeviceAction.TORCH_TOGGLE
            }

            has(wifiWords) -> when {
                wantsOff -> DeviceAction.WIFI_OFF
                wantsOn -> DeviceAction.WIFI_ON
                else -> DeviceAction.WIFI_PANEL
            }

            has(bluetoothWords) -> DeviceAction.BLUETOOTH
            has(airplaneWords) -> DeviceAction.AIRPLANE_MODE
            has(screenshotWords) -> DeviceAction.SCREENSHOT
            has(lockWords) -> DeviceAction.LOCK_SCREEN
            has(recentsWords) -> DeviceAction.RECENTS
            has(homeWords) -> DeviceAction.HOME
            has(backWords) -> DeviceAction.BACK
            has(notificationWords) -> DeviceAction.NOTIFICATIONS
            has(vibrateWords) -> DeviceAction.RINGER_VIBRATE
            has(ringerWords) -> DeviceAction.RINGER_NORMAL

            has(volumeWords) && has(muteWords) -> DeviceAction.VOLUME_MUTE

            has(volumeWords) && hasModifier -> when {
                wantsMax -> DeviceAction.VOLUME_MAX
                wantsDown || wantsOff -> DeviceAction.VOLUME_DOWN
                else -> DeviceAction.VOLUME_UP
            }

            has(silentWords) -> DeviceAction.RINGER_SILENT

            has(brightnessWords) && hasModifier -> when {
                wantsMax -> DeviceAction.BRIGHTNESS_MAX
                wantsMin -> DeviceAction.BRIGHTNESS_MIN
                wantsDown || wantsOff -> DeviceAction.BRIGHTNESS_DOWN
                else -> DeviceAction.BRIGHTNESS_UP
            }

            has(dataWords) && hasModifier -> DeviceAction.MOBILE_DATA

            // "settings kholo" — sirf tab jab koi app ka naam saath me na ho.
            has(settingsWords) && matchPhrase(text, openVerbs) != null -> DeviceAction.SETTINGS

            else -> null
        }

        return action?.let { EvCommand.Device(it) }
    }

    // --------------------------------------------------------------- shared

    private fun cleanName(input: String): String = normalize(input)
        .split(" ")
        .filter { it.isNotBlank() && it !in messageNoiseWords && it !in connectors && it !in fillerWords }
        .joinToString(" ")

    private fun stripNoise(input: String): String {
        val tokens = normalize(input).split(" ").filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && (tokens.first() in messageNoiseWords || tokens.first() in fillerWords)) {
            tokens.removeAt(0)
        }
        while (tokens.isNotEmpty() && (tokens.last() in messageNoiseWords || tokens.last() in fillerWords)) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }

    /**
     * Matches a spoken name against the curated catalog first, then against
     * every app installed on the phone (exact -> no-space -> prefix -> contains).
     */
    fun resolveApp(name: String, installedApps: List<InstalledApp>): AppTarget? {
        val n = cleanQuery(normalize(name))
        if (n.isEmpty()) return null
        val compact = n.replace(" ", "")

        AppCatalog.shortcuts.firstOrNull { shortcut ->
            shortcut.id.equals(n, true) ||
                shortcut.label.equals(n, true) ||
                shortcut.label.replace(" ", "").equals(compact, true) ||
                shortcut.keywords.any { it.equals(n, true) }
        }?.let { return AppTarget(it.label, it.packageName, it.webFallbackUrl) }

        installedApps.firstOrNull { it.label.equals(n, true) }
            ?.let { return AppTarget(it.label, it.packageName) }

        installedApps.firstOrNull { normalize(it.label).replace(" ", "") == compact }
            ?.let { return AppTarget(it.label, it.packageName) }

        installedApps.firstOrNull { normalize(it.label).startsWith(n) }
            ?.let { return AppTarget(it.label, it.packageName) }

        if (n.length >= 3) {
            installedApps.firstOrNull { normalize(it.label).contains(n) }
                ?.let { return AppTarget(it.label, it.packageName) }
        }

        return null
    }

    private fun normalize(input: String): String = input
        .lowercase()
        .replace(Regex("[?!.,;:\"']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsWord(text: String, word: String): Boolean =
        phraseRegex(word).containsMatchIn(text)

    private fun detectVerb(text: String): Verb? = when {
        matchPhrase(text, playVerbs) != null -> Verb.PLAY
        matchPhrase(text, searchVerbs) != null -> Verb.SEARCH
        matchPhrase(text, openVerbs) != null -> Verb.OPEN
        else -> null
    }

    private fun matchPhrase(text: String, phrases: List<String>): String? = phrases
        .sortedByDescending { it.length }
        .firstOrNull { phrase -> phraseRegex(phrase).containsMatchIn(text) }

    private fun stripVerb(text: String, verb: Verb): String {
        val phrases = when (verb) {
            Verb.PLAY -> playVerbs
            Verb.OPEN -> openVerbs
            Verb.SEARCH -> searchVerbs
        }
        val phrase = matchPhrase(text, phrases) ?: return text
        return text.replace(phraseRegex(phrase), " ")
    }

    private fun phraseRegex(phrase: String): Regex =
        Regex("(^|\\s)" + Regex.escape(phrase) + "($|\\s)")

    /** Drops filler/connector words from the edges only. */
    private fun cleanQuery(input: String): String {
        val tokens = normalize(input).split(" ").filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && (tokens.first() in fillerWords || tokens.first() in connectors)) {
            tokens.removeAt(0)
        }
        while (tokens.isNotEmpty() && (tokens.last() in fillerWords || tokens.last() in connectors)) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }

    private const val DEFAULT_VIDEO_SECONDS = 15
}
