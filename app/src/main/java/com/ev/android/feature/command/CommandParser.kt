package com.ev.android.feature.command

import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.launcher.AppCatalog

private enum class Verb { PLAY, OPEN, SEARCH }

/**
 * Turns Hinglish speech/text into an [EvCommand].
 *
 * Examples:
 *  "youtube pe paisa song lagao"   -> PlayMedia("paisa song", YouTube)
 *  "paisa wala gaana baja do"      -> PlayMedia("paisa wala gaana", YouTube)
 *  "spotify pe tum hi ho lagao"    -> PlayMedia("tum hi ho", Spotify)
 *  "whatsapp kholo"                -> OpenApp(WhatsApp)
 *  "phonepe open karo"             -> OpenApp(PhonePe)
 */
object CommandParser {

    val youtube = AppTarget("YouTube", "com.google.android.youtube", "https://www.youtube.com")

    private val playVerbs = listOf(
        "laga do", "lagaa do", "lagado", "laga de", "lagade", "lagao", "lagaao", "laga",
        "baja do", "bajado", "baja de", "bajade", "bajao",
        "chala do", "chalado", "chala de", "chalao",
        "suna do", "sunado", "sunao",
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

    /** Only stripped from the START/END of a query so song names stay intact. */
    private val fillerWords = setOf(
        "mujhe", "muje", "please", "plz", "zara", "bhai", "abhi", "to", "na", "yaar",
    )

    private val connectors = setOf("pe", "par", "pr", "me", "mein", "main", "on", "in", "se", "ka", "ki", "ke")

    private val targetSplitRegex =
        Regex("^(.{2,40}?)\\s+(pe|par|pr|me|mein|main|on|in)\\s+(.+)$")

    fun parse(rawInput: String, installedApps: List<InstalledApp>): EvCommand {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return EvCommand.Unknown(raw)

        var text = normalize(raw)
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
