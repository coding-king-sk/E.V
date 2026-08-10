package com.ev.android.feature.ai

import android.content.Context
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.media.MediaAction
import com.ev.android.feature.settings.EvSettings
import org.json.JSONObject

sealed interface AiOutcome {
    data class Resolved(val command: EvCommand) : AiOutcome
    data class Failed(val reason: String) : AiOutcome

    /** AI off hai, key nahi hai, ya privacy ki wajah se bheja hi nahi. */
    data object Unavailable : AiOutcome
}

/**
 * Parser jo na samajh paye, wahi AI ke paas jata hai.
 *
 * Ye order jaan bujh ke hai:
 *  - roz ke 90% commands parser turant, offline, aur free me handle karta hai
 *  - sirf ajeeb/ghumavdar commands hi network pe jate hain
 *
 * Isse free limit kabhi khatam nahi hoti aur app dheema bhi nahi padta.
 */
object AiCommandResolver {

    private const val MAX_APPS_IN_PROMPT = 60

    suspend fun resolve(
        context: Context,
        rawInput: String,
        installedApps: List<InstalledApp>,
    ): AiOutcome {
        if (!EvSettings.aiEnabled(context)) return AiOutcome.Unavailable

        // Privacy guard: message/call wale commands me naam aur text hota hai.
        if (looksPersonal(rawInput) && !EvSettings.sendPersonalToAi(context)) {
            return AiOutcome.Unavailable
        }

        val appNames = installedApps
            .take(MAX_APPS_IN_PROMPT)
            .joinToString(", ") { it.label }

        val result = LlmClient.complete(
            apiKey = EvSettings.apiKey(context),
            systemPrompt = systemPrompt(),
            userPrompt = "Command: " + rawInput + "\nInstalled apps: " + appNames,
        )

        return when (result) {
            is LlmResult.Error -> AiOutcome.Failed(result.message)
            is LlmResult.Ok -> {
                val command = toCommand(result.content, rawInput, installedApps)
                if (command == null) {
                    AiOutcome.Failed("AI bhi nahi samajh paya: \"" + rawInput + "\"")
                } else {
                    AiOutcome.Resolved(command)
                }
            }
        }
    }

    private fun systemPrompt(): String {
        val devices = DeviceAction.entries.joinToString(" ") { it.name }
        val media = MediaAction.entries.joinToString(" ") { it.name }

        return buildString {
            append("You convert Hinglish/Hindi/English phone commands into JSON. ")
            append("Reply with ONLY a JSON object and no prose.\n")
            append("Fields: intent, app, query, contact, message, media_action, ")
            append("device_action, seconds, hour, minute.\n")
            append("intent must be one of: open_app play_media search send_whatsapp ")
            append("send_sms call media device timer alarm identify_song unknown.\n")
            append("media_action must be one of: ").append(media).append("\n")
            append("device_action must be one of: ").append(devices).append("\n")
            append("Rules: hour is 24-hour. seconds is the timer length in seconds. ")
            append("For play_media put the song or video name in query. ")
            append("Pick app only from the installed apps list. ")
            append("If you are not confident, use intent unknown.")
        }
    }

    private fun toCommand(
        json: String,
        rawInput: String,
        installedApps: List<InstalledApp>,
    ): EvCommand? {
        val obj = runCatching { JSONObject(extractJson(json)) }.getOrNull() ?: return null

        val intent = obj.optString("intent").lowercase().trim()
        val app = obj.optString("app").trim()
        val query = obj.optString("query").trim()
        val contact = obj.optString("contact").trim()
        val message = obj.optString("message").trim()

        val target = if (app.isNotEmpty()) {
            CommandParser.resolveApp(app, installedApps)
        } else {
            null
        }

        return when (intent) {
            "open_app" -> target?.let { EvCommand.OpenApp(it) }

            "play_media" -> if (query.isEmpty()) null
            else EvCommand.PlayMedia(query, target ?: CommandParser.youtube)

            "search" -> if (query.isEmpty()) null
            else EvCommand.SearchInApp(query, target ?: CommandParser.youtube)

            "send_whatsapp" -> if (contact.isEmpty() || message.isEmpty()) null
            else EvCommand.SendWhatsApp(contact, message)

            "send_sms" -> if (contact.isEmpty() || message.isEmpty()) null
            else EvCommand.SendSms(contact, message)

            "call" -> if (contact.isEmpty()) null else EvCommand.CallContact(contact)

            "media" -> enumOrNull<MediaAction>(obj.optString("media_action"))
                ?.let { EvCommand.Media(it) }

            "device" -> enumOrNull<DeviceAction>(obj.optString("device_action"))
                ?.let { EvCommand.Device(it) }

            "timer" -> obj.optInt("seconds", 0)
                .takeIf { it > 0 }
                ?.let { EvCommand.Timer(it, null) }

            "alarm" -> {
                val hour = obj.optInt("hour", -1)
                val minute = obj.optInt("minute", 0)
                if (hour in 0..23 && minute in 0..59) EvCommand.Alarm(hour, minute) else null
            }

            "identify_song" -> EvCommand.IdentifySong

            else -> null
        }
    }

    /** Kabhi kabhi model JSON ke aage peeche text likh deta hai. */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name.trim().uppercase()) }.getOrNull()

    private fun looksPersonal(text: String): Boolean {
        val t = text.lowercase()
        if (Regex("(^|\\s)ko($|\\s)").containsMatchIn(t)) return true
        return listOf("message", "msg", "sms", "bhej", "bolo", "keh do", "call", "phone")
            .any { t.contains(it) }
    }
}
