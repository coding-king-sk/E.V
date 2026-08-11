package com.ev.android.feature.command

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.accessibility.EvAccessibilityService
import com.ev.android.feature.calling.Caller
import com.ev.android.feature.camera.CameraCapture
import com.ev.android.feature.contacts.ContactsRepository
import com.ev.android.feature.contacts.PhoneNumbers
import com.ev.android.feature.daily.DailyTasks
import com.ev.android.feature.device.DeviceControls
import com.ev.android.feature.launcher.AppLauncher
import com.ev.android.feature.media.MediaAction
import com.ev.android.feature.media.MediaControls
import com.ev.android.feature.media.SongIdentifier
import com.ev.android.feature.media.YouTubeResolver
import com.ev.android.feature.messaging.SmsSender
import com.ev.android.feature.reminders.Reminders
import kotlinx.coroutines.delay
import java.net.URLEncoder

sealed interface CommandResult {
    val message: String

    data class Success(override val message: String) : CommandResult
    data class Failure(override val message: String) : CommandResult
}

object CommandExecutor {

    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /** Do kaam ke beech itna gap \u2014 warna doosra pehle ke khulne se pehle chal jata hai. */
    private const val MULTI_GAP_MS = 1500L

    suspend fun execute(context: Context, command: EvCommand): CommandResult = when (command) {
        is EvCommand.OpenApp -> openApp(context, command.target)
        is EvCommand.PlayMedia -> playMedia(context, command.query, command.target)
        is EvCommand.SearchInApp -> searchInApp(context, command.query, command.target)
        is EvCommand.SendWhatsApp -> sendWhatsApp(context, command.contactName, command.message)
        is EvCommand.SendSms -> sendSms(context, command.contactName, command.message)
        is EvCommand.CallContact -> callContact(context, command.contactName)

        is EvCommand.Media ->
            if (MediaControls.perform(context, command.action)) {
                CommandResult.Success(mediaMessage(command.action))
            } else {
                CommandResult.Failure("Abhi koi app media play nahi kar rahi")
            }

        is EvCommand.IdentifySong -> CommandResult.Success(SongIdentifier.identify(context))

        is EvCommand.Timer ->
            if (DailyTasks.setTimer(context, command.seconds, command.label)) {
                CommandResult.Success(
                    DailyTasks.formatDuration(command.seconds) + " ka timer laga diya",
                )
            } else {
                CommandResult.Failure("Timer set nahi ho paya \u2014 koi Clock app nahi mili")
            }

        is EvCommand.Alarm ->
            if (DailyTasks.setAlarm(context, command.hour, command.minute, null)) {
                CommandResult.Success(
                    DailyTasks.formatTime(command.hour, command.minute) + " ka alarm laga diya",
                )
            } else {
                CommandResult.Failure("Alarm set nahi ho paya \u2014 koi Clock app nahi mili")
            }

        is EvCommand.Reminder ->
            if (Reminders.schedule(context, command.at, command.text)) {
                CommandResult.Success(Reminders.confirmation(command.at, command.text))
            } else {
                CommandResult.Failure(
                    "Reminder set nahi ho paya \u2014 phone ki Settings me E.V ko " +
                        "'Alarms & reminders' ki permission de do",
                )
            }

        is EvCommand.TakePhoto ->
            if (CameraCapture.takePhoto(context, command.front)) {
                CommandResult.Success(
                    if (command.front) "Selfie le raha hoon\u2026" else "Photo le raha hoon\u2026"
                )
            } else {
                CommandResult.Failure("Camera khul nahi paya")
            }

        is EvCommand.RecordVideo ->
            if (CameraCapture.recordVideo(context, command.front, command.seconds)) {
                CommandResult.Success(
                    command.seconds.toString() + " second ka video bana raha hoon\u2026"
                )
            } else {
                CommandResult.Failure("Camera khul nahi paya")
            }

        is EvCommand.TypeText -> typeText(context, command)

        is EvCommand.Multi -> runMulti(context, command.commands)

        is EvCommand.Device -> {
            val result = DeviceControls.run(context, command.action, command.level)
            if (result.ok) CommandResult.Success(result.message)
            else CommandResult.Failure(result.message)
        }

        is EvCommand.Unknown -> CommandResult.Failure(
            "Samajh nahi aaya: \"${command.raw}\". Try: \"YouTube pe paisa song lagao\""
        )
    }

    /**
     * Ek ke baad ek kaam.
     *
     * Beech me thoda rukte hain kyunki Android app ko khulne me waqt leta hai;
     * bina gap ke doosri app pehli ko dhakel deti hai.
     */
    private suspend fun runMulti(context: Context, commands: List<EvCommand>): CommandResult {
        if (commands.isEmpty()) return CommandResult.Failure("Kuch karne ko mila hi nahi")

        val messages = mutableListOf<String>()
        var allOk = true

        commands.forEachIndexed { index, command ->
            if (index > 0) delay(MULTI_GAP_MS)

            val result = execute(context, command)
            if (result is CommandResult.Failure) allOk = false
            messages.add(result.message)
        }

        val joined = messages.joinToString(", phir ")
        return if (allOk) CommandResult.Success(joined) else CommandResult.Failure(joined)
    }

    /**
     * App ke text box me likhna.
     *
     * Intent se kisi doosri app ke text box me likhna Android allow nahi karta,
     * isliye ye kaam Accessibility service karti hai: pehle usme text "arm"
     * karte hain, phir app kholte hain \u2014 app khulte hi wo focus wale box me
     * text bhar deti hai.
     */
    private fun typeText(context: Context, command: EvCommand.TypeText): CommandResult {
        if (!AccessibilityHelper.isEnabled(context)) {
            return CommandResult.Failure(
                "Type karne ke liye ek baar Accessibility me 'E.V auto-send' on karna padega"
            )
        }

        EvAccessibilityService.armTyping(command.text)

        val target = command.target
        val packageName = target?.packageName

        if (target != null && packageName != null) {
            if (!AppLauncher.launchPackage(context, packageName)) {
                EvAccessibilityService.cancelTyping()
                return CommandResult.Failure(target.label + " open nahi ho payi")
            }
            return CommandResult.Success(
                target.label + " me \"" + command.text + "\" type kar raha hoon"
            )
        }

        return CommandResult.Success(
            "\"" + command.text + "\" type kar raha hoon \u2014 jis box me chahiye us pe tap karo"
        )
    }

    /**
     * Kuch "app" asal me app nahi, Android ki screen hoti hai \u2014 camera,
     * settings, wifi, bluetooth. Inka koi package nahi hota, isliye pehle ye
     * seedha "open nahi ho paya" bol dete the. Ab inke liye system ka apna
     * intent chalta hai.
     */
    private fun systemIntentFor(label: String): Intent? = when (label.lowercase()) {
        "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        "settings", "setting" -> Intent(Settings.ACTION_SETTINGS)
        "wifi", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        else -> null
    }

    private fun openApp(context: Context, target: AppTarget): CommandResult {
        val pkg = target.packageName

        if (pkg != null && AppLauncher.launchPackage(context, pkg)) {
            return CommandResult.Success("${target.label} khul raha hai")
        }

        if (pkg == null) {
            systemIntentFor(target.label)?.let { intent ->
                if (AppLauncher.startIntent(context, intent)) {
                    return CommandResult.Success("${target.label} khol diya")
                }
            }
        }

        target.webFallbackUrl?.let { url ->
            if (AppLauncher.startIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return CommandResult.Success("${target.label} installed nahi hai \u2014 web pe khol diya")
            }
        }

        if (pkg != null && openPlayStore(context, pkg)) {
            return CommandResult.Success("${target.label} installed nahi hai \u2014 Play Store khol diya")
        }

        return CommandResult.Failure("${target.label} open nahi ho paya")
    }

    private suspend fun playMedia(
        context: Context,
        query: String,
        target: AppTarget,
    ): CommandResult {
        val pkg = target.packageName

        if (pkg == null || pkg == YOUTUBE_PACKAGE) {
            val videoId = YouTubeResolver.firstVideoId(query)
            if (videoId != null && openYouTubeVideo(context, videoId)) {
                return CommandResult.Success("YouTube pe \"$query\" chal raha hai")
            }
        }

        if (pkg != null && startPlayFromSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" play ho raha hai")
        }

        if (pkg != null && startInAppSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" search kiya")
        }

        if (startPlayFromSearch(context, query, packageName = null)) {
            return CommandResult.Success("\"$query\" play ho raha hai")
        }

        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    private fun searchInApp(context: Context, query: String, target: AppTarget): CommandResult {
        val pkg = target.packageName
        if (pkg != null && startInAppSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" search kiya")
        }
        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    private suspend fun sendWhatsApp(
        context: Context,
        contactName: String?,
        message: String,
    ): CommandResult {
        if (contactName.isNullOrBlank()) {
            return openApp(context, CommandParser.whatsapp)
        }

        val number = resolveNumber(context, contactName)
            ?: return contactFailure(context, contactName)

        val autoSend = AccessibilityHelper.isEnabled(context)
        if (autoSend) {
            EvAccessibilityService.armWhatsAppAutoSend()
        }

        val successMessage = if (autoSend) {
            "$contactName ko message bheja jaa raha hai\u2026"
        } else {
            "$contactName ka chat khul gaya \u2014 send dabao (auto-send ke liye Accessibility on karo)"
        }

        val encoded = URLEncoder.encode(message, "UTF-8").replace("+", "%20")

        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$number&text=$encoded"),
        ).setPackage("com.whatsapp")
        if (AppLauncher.startIntent(context, deepLink)) {
            return CommandResult.Success(successMessage)
        }

        val waMe = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$encoded"))
        if (AppLauncher.startIntent(context, waMe)) {
            return CommandResult.Success(successMessage)
        }

        EvAccessibilityService.cancelAutoSend()
        return CommandResult.Failure("WhatsApp open nahi ho paya")
    }

    private suspend fun sendSms(
        context: Context,
        contactName: String,
        message: String,
    ): CommandResult {
        val number = resolveNumber(context, contactName)
            ?: return contactFailure(context, contactName)

        if (SmsSender.send(context, number, message)) {
            return CommandResult.Success("$contactName ko SMS bhej diya")
        }

        return if (SmsSender.openDraft(context, number, message)) {
            CommandResult.Success("$contactName ka SMS type kar diya \u2014 send dabao")
        } else {
            CommandResult.Failure("SMS bhej nahi paya")
        }
    }

    private suspend fun callContact(context: Context, contactName: String): CommandResult {
        val number = resolveNumber(context, contactName)
            ?: return contactFailure(context, contactName)

        if (!Caller.call(context, number)) {
            return CommandResult.Failure("Call nahi lag payi")
        }

        return if (Caller.dialedDirectly(context)) {
            CommandResult.Success("$contactName ko call lag rahi hai")
        } else {
            CommandResult.Success("$contactName ka number dialer me hai \u2014 call button dabao")
        }
    }

    /** Naam ya seedha number \u2014 dono se phone number nikalta hai. */
    private suspend fun resolveNumber(context: Context, contactName: String): String? = when {
        PhoneNumbers.looksLikeNumber(contactName) -> PhoneNumbers.normalize(contactName)
        else -> ContactsRepository.findByName(context, contactName)?.phone
    }

    private fun contactFailure(context: Context, contactName: String): CommandResult.Failure =
        if (!ContactsRepository.hasPermission(context)) {
            CommandResult.Failure("Contacts ki permission chahiye naam se contact dhoondne ke liye")
        } else {
            CommandResult.Failure("\"$contactName\" naam ka contact nahi mila")
        }

    private fun mediaMessage(action: MediaAction): String = when (action) {
        MediaAction.NEXT -> "Agla gaana"
        MediaAction.PREVIOUS -> "Pichla gaana"
        MediaAction.PLAY_PAUSE -> "Play/pause kiya"
        MediaAction.PAUSE -> "Rok diya"
        MediaAction.PLAY -> "Phir se chalu"
        MediaAction.FORWARD -> "Aage badha diya"
        MediaAction.REWIND -> "Peeche kar diya"
        MediaAction.STOP -> "Band kar diya"
    }

    private fun openYouTubeVideo(context: Context, videoId: String): Boolean {
        val inApp = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId"),
        ).setPackage(YOUTUBE_PACKAGE)
        if (AppLauncher.startIntent(context, inApp)) return true

        val scheme = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        if (AppLauncher.startIntent(context, scheme)) return true

        val browser = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId"),
        )
        return AppLauncher.startIntent(context, browser)
    }

    private fun startPlayFromSearch(context: Context, query: String, packageName: String?): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (packageName != null) setPackage(packageName)
        }
        return AppLauncher.startIntent(context, intent)
    }

    private fun startInAppSearch(context: Context, query: String, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(packageName)
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
        }
        return AppLauncher.startIntent(context, intent)
    }

    private fun openYouTubeResults(
        context: Context,
        query: String,
        preferPackage: String?,
    ): CommandResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")

        if (preferPackage != null) {
            val inApp = Intent(Intent.ACTION_VIEW, uri).setPackage(preferPackage)
            if (AppLauncher.startIntent(context, inApp)) {
                return CommandResult.Success("\"$query\" ke results khol diye")
            }
        }

        if (AppLauncher.startIntent(context, Intent(Intent.ACTION_VIEW, uri))) {
            return CommandResult.Success("\"$query\" browser me khol diya")
        }

        return CommandResult.Failure("\"$query\" play nahi ho paya")
    }

    private fun openPlayStore(context: Context, packageName: String): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        if (AppLauncher.startIntent(context, market)) return true
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        return AppLauncher.startIntent(context, web)
    }
}
