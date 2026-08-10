package com.ev.android.feature.command

import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.launcher.AppCatalog

private enum class Verb { PLAY, OPEN, SEARCH }

/**
 * Turns Hinglish speech/text into an [EvCommand].
 *
 * Examples:
 *  "youtube pe paisa song lagao"          -> PlayMedia("paisa song", YouTube)
 *  "paisa wala gaana baja do"             -> PlayMedia("paisa wala gaana", YouTube)
 *  "spotify pe tum hi ho lagao"           -> PlayMedia("tum hi ho", Spotify)
 *  "whatsapp kholo"                       -> OpenApp(WhatsApp)
 *  "rehan ko bolo ki main aa raha hoon"   -> SendWhatsApp("rehan", "main aa raha hoon")
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
        "text kar do", "text karo",
        "bhej do", "bhejo", "bhejna", "bhej",
        "likh do", "likho", "likh",
        "keh do", "kehna", "kaho",
        "bol do", "bolo", "bol",
    )

    /** Only stripped from the START/END of a query so song names stay intact. */
    private val fillerWords = setOf(
        "mujhe", "muje", "please", "plz", "zara", "bhai", "abhi", "to", "na", "yaar",
    )

    private val connectors =
        setOf("pe", "par", "pr", "me", "mein", "main", "on", "in", "se", "ka", "ki", "ke")

    private val messageNoiseWords = setOf("message", "msg", "text", "whatsapp", "wa")

    private val targetSplitRegex =
        Regex("^(.{2,40}?)\\s+(pe|par|pr|me|mein|main|on|in)\\s+(.+)$")

    private val whatsappPrefixRegex =
        Regex("^(whatsapp|whats app|wa)\\s+(pe|par|pr|me|mein|main|on)\\s+")

    fun parse(rawInput: String, installedApps: List<InstalledApp>): EvCommand {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return EvCommand.Unknown(raw)

        val normalized = normalize(raw)

        // WhatsApp messaging is checked first \u2014 it has a very distinctive
        // "<naam> ko ... <bhejo/bolo>" shape that the app-launch parser would
        // otherwise mangle.
        parseWhatsAppMessage(normalized)?.let { return it }

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

        return when (verb) {
            Verb.PLAY -> when {
                query.isEmpty() -> target?.let { EvCommand.OpenApp(it) } ?: EvCommand.Unknown(raw)
                // "spotify chalao" is an open, not a song named "spotify"
                target == null && resolveApp(query, installedApps) != null ->
                    EvCommand.OpenApp(resolveApp(query, installedApps)!!)
                else -> EvCommand.PlayMedia(query, target ?: youtube)
            }

            Verb.SEARCH -> when {
                query.isEmpty() -> target?.let { EvCommand.OpenApp(it) } ?: EvCommand.Unknown(raw)
                else -> EvCommand.SearchInApp(query, target ?: youtube)
            }

            Verb.OPEN -> (target ?: resolveApp(query, installedApps))
                ?.let { EvCommand.OpenApp(it) }
                ?: EvCommand.Unknown(raw)

            // No verb at all: treat the whole thing as an app name.
            null -> (resolveApp(text, installedApps) ?: target)
                ?.let { EvCommand.OpenApp(it) }
                ?: EvCommand.Unknown(raw)
        }
    }

    /**
     * Handles both Hinglish word orders:
     *   "rehan ko whatsapp pe bolo ki main aa raha hoon"  (body AFTER the verb)
     *   "whatsapp pe rehan ko good morning bhej do"       (body BEFORE the verb)
     */
    private fun parseWhatsAppMessage(text: String): EvCommand? {
        val mentionsWhatsApp = text.contains("whatsapp") || text.contains("whats app")
        val stripped = text.replace(whatsappPrefixRegex, "").trim()

        val koMatch = Regex("\\s+ko\\s+").find(stripped) ?: run {
            // "whatsapp kholo" and friends are NOT messages.
            return null
        }

        val namePart = cleanName(stripped.substring(0, koMatch.range.first))
        var rest = stripped.substring(koMatch.range.last + 1).trim()
        rest = rest.replace(whatsappPrefixRegex, "").trim()

        val verb = matchPhrase(rest, messageVerbs)

        // Without an explicit whatsapp mention we need a messaging verb,
        // otherwise "usko lagao" would be misread as a message.
        if (verb == null && !mentionsWhatsApp) return null
        if (namePart.isEmpty()) return null

        val body = if (verb == null) {
            stripNoise(rest)
        } else {
            val regex = phraseRegex(verb)
            val match = regex.find(rest)
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
        return EvCommand.SendWhatsApp(contactName = namePart, message = body)
    }

    private fun cleanName(input: String): String {
        val tokens = normalize(input).split(" ")
            .filter { it.isNotBlank() && it !in messageNoiseWords && it !in connectors && it !in fillerWords }
        return tokens.joinToString(" ")
    }

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
}
