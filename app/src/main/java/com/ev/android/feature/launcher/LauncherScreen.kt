@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.ev.android.feature.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ev.android.feature.ai.AiCommandResolver
import com.ev.android.feature.ai.AiOutcome
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.apps.InstalledAppsRepository
import com.ev.android.feature.command.CommandExecutor
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.gallery.EvGallery
import com.ev.android.feature.history.CommandHistory
import com.ev.android.feature.history.HistoryEntry
import com.ev.android.feature.hud.EvOrb
import com.ev.android.feature.hud.HudActionBar
import com.ev.android.feature.hud.HudHeader
import com.ev.android.feature.hud.HudSectionLabel
import com.ev.android.feature.hud.HudStatusRow
import com.ev.android.feature.hud.HudTabs
import com.ev.android.feature.hud.OrbEditorPanel
import com.ev.android.feature.hud.OrbStyle
import com.ev.android.feature.hud.OrbStyleStore
import com.ev.android.feature.notes.Note
import com.ev.android.feature.notes.Notes
import com.ev.android.feature.reminders.Reminder
import com.ev.android.feature.reminders.Reminders
import com.ev.android.feature.settings.ApiKeyDialog
import com.ev.android.feature.settings.EvSettings
import com.ev.android.feature.settings.SettingsPanel
import com.ev.android.feature.tts.Speaker
import com.ev.android.feature.tts.VoiceSetup
import com.ev.android.feature.voice.EvListeningService
import com.ev.android.feature.voice.WhisperInput
import com.ev.android.feature.voice.rememberVoiceCommand
import com.ev.android.ui.theme.EVTheme
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_COMMAND = "COMMAND"
private const val TAB_REMINDER = "REMINDER"
private const val TAB_ALIASES = "ALIASES"
private const val TAB_NOTES = "NOTES"
private const val TAB_GALLERY = "GALLERY"
private const val TAB_HISTORY = "HISTORY"

private val tabs = listOf(
    TAB_COMMAND,
    TAB_REMINDER,
    TAB_ALIASES,
    TAB_NOTES,
    TAB_GALLERY,
    TAB_HISTORY,
)

/**
 * E.V ki main screen \u2014 HUD look.
 *
 * Beech me sirf orb rehta hai, jo batata hai E.V so raha hai, sun raha hai ya
 * soch raha hai. Settings aur orb editor poore page hain \u2014 khulte hi home
 * screen (header, tabs, command box) chhup jaati hai, taaki lamba content
 * chhoti khidki me na thusa lage. Back arrow se wapas.
 *
 * @param autoListen bubble se aaye ho aur seedha bolna hai
 * @param autoCommand bubble se aayi hui command, jo khud chal jaye
 */
@Composable
fun LauncherScreen(
    autoListen: Boolean = false,
    autoCommand: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var input by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(TAB_COMMAND) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<EvCommand?>(null) }
    var pendingAsk by remember { mutableStateOf<PendingAsk?>(null) }
    var apiKeySet by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showOrbEditor by remember { mutableStateOf(false) }
    var orbStyle by remember { mutableStateOf(OrbStyleStore.DEFAULT) }
    var showApiKey by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(EvListeningService.isRunning) }
    var handsFreePaused by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("READY \u2014 BOLO YA LIKHO") }
    var gallery by remember { mutableStateOf<List<EvGallery.Item>>(emptyList()) }
    var galleryLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CommandHistory.load(context)
        Notes.load(context)
        apiKeySet = EvSettings.hasApiKey(context)
        handsFree = EvListeningService.isRunning
        orbStyle = OrbStyleStore.load(context)
        installedApps = InstalledAppsRepository.load(context)
    }

    // Gallery har baar tab khulne pe refresh \u2014 beech me nayi photo li ho to
    // wapas aate hi dikh jaye.
    LaunchedEffect(tab) {
        if (tab != TAB_GALLERY) return@LaunchedEffect
        galleryLoading = true
        gallery = EvGallery.load(context)
        galleryLoading = false
    }

    /** Chhoti si baat batani ho to status line hi kaafi hai. */
    fun notify(message: String) {
        status = message.uppercase()
    }

    /**
     * TTS engine app ke saath zinda rehta hai.
     *
     * Agar acchi Hindi awaz phone me hai hi nahi, to bina kuch poochhe seedha
     * Google ki voice-data install screen khul jaati hai \u2014 user ko bas
     * "Install" dabana hai.
     */
    DisposableEffect(Unit) {
        Speaker.init(context) {
            notify("Acchi awaz ke liye Google voice data install kar lo")
            VoiceSetup.openVoiceDataInstall(context)
        }
        onDispose { Speaker.shutdown() }
    }

    fun dispatch(command: EvCommand, spoken: String? = null) {
        scope.launch {
            busy = true
            status = "SOCH RAHA HOON\u2026"

            val result = CommandExecutor.execute(context, command)

            busy = false
            status = result.message.uppercase()

            CommandHistory.add(
                context = context,
                spoken = spoken ?: ("\uD83D\uDC46 " + CommandHistory.describe(command)),
                understood = CommandHistory.describe(command),
                reply = result.message,
            )

            if (speakReplies) Speaker.speak(result.message)
        }
    }

    /** Sirf wahi permissions jo abhi tak mili nahi hain. */
    fun missingPermissions(command: EvCommand): List<String> {
        val needed = when (command) {
            is EvCommand.SendWhatsApp ->
                if (command.contactName.isNullOrBlank()) emptyList()
                else listOf(Manifest.permission.READ_CONTACTS)

            is EvCommand.SendSms -> listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
            )

            is EvCommand.CallContact -> listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
            )

            is EvCommand.TakePhoto -> listOf(Manifest.permission.CAMERA)

            is EvCommand.RecordVideo -> listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )

            // Ek hi vaakya me kai kaam \u2014 sabki permissions ek saath maang lo,
            // warna beech me ruk ke user ko do-teen dialog dekhne padte hain.
            is EvCommand.Multi -> command.commands.flatMap { inner ->
                when (inner) {
                    is EvCommand.SendSms -> listOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.SEND_SMS,
                    )

                    is EvCommand.CallContact -> listOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                    )

                    is EvCommand.SendWhatsApp -> listOf(Manifest.permission.READ_CONTACTS)
                    is EvCommand.TakePhoto -> listOf(Manifest.permission.CAMERA)
                    is EvCommand.RecordVideo -> listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                    )

                    else -> emptyList()
                }
            }.distinct()

            else -> emptyList()
        }

        return needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val queued = pendingCommand
        pendingCommand = null
        // Permission mile ya na mile, command chala dete hain \u2014 executor khud
        // fallback karta hai (SMS draft, dialer, ya saaf error message).
        if (queued != null) dispatch(queued)
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            EvListeningService.start(context)
            handsFree = true
        } else {
            handsFree = false
            notify("Mic ki permission ke bina hands-free nahi chalega")
        }
    }

    fun micPermissionsMissing(): List<String> {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Hands-free ka switch \u2014 sirf haath se.
     *
     * Pehle ye app khulte hi apne aap chalu ho jata tha. Natija: har baar app
     * kholte hi mic zinda, notification zinda, aur battery kharch \u2014 chahe
     * user ko sirf history dekhni ho. Ab jab tak khud MIC na dabao, E.V
     * sunta nahi.
     */
    fun toggleHandsFree() {
        if (EvListeningService.isRunning) {
            EvListeningService.stop(context)
            handsFree = false
            notify("Hands-free band")
            return
        }

        val missing = micPermissionsMissing()
        if (missing.isNotEmpty()) {
            micLauncher.launch(missing.toTypedArray())
            return
        }

        EvListeningService.start(context)
        handsFree = true
        notify("Ab bas \"Hey " + EvSettings.wakeName(context) + "\" bolo")
    }

    /** Mic button ke liye hands-free ko thoda der ke liye rok ke wapas chalu. */
    fun resumeHandsFree() {
        if (!handsFreePaused) return
        handsFreePaused = false
        EvListeningService.start(context)
        handsFree = true
    }

    /** Permission maango (agar chahiye) phir command chalao. */
    fun handle(command: EvCommand, spoken: String? = null) {
        val missing = missingPermissions(command)
        if (missing.isNotEmpty()) {
            pendingCommand = command
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        dispatch(command, spoken)
    }

    /**
     * Teen seedhiyan: offline parser -> AI se command -> AI se seedha jawab.
     *
     * Beech me ek chauthi cheez bhi hai: agar baat **adhoori** lagti hai
     * ("message bhejo" par kisko nahi bataya), to E.V "samajh nahi aaya"
     * bolne ki jagah **sawaal** puchta hai aur agla jawab usi command me jod
     * deta hai.
     *
     * @param askedBefore pichla sawaal, taaki wahi sawaal bar bar na puche.
     */
    fun runCommand(text: String, askedBefore: String? = null) {
        if (text.isBlank()) return
        keyboard?.hide()

        // Pehle se koi sawaal poocha hua hai? To ye uska jawab hai.
        val waiting = pendingAsk
        if (waiting != null) {
            pendingAsk = null

            if (isCancelAnswer(text)) {
                status = "THEEK HAI, RAHNE DETE HAIN"
                return
            }

            runCommand(waiting.rebuild(text.trim()), waiting.question)
            return
        }

        val parsed = CommandParser.parse(text, installedApps)
        if (parsed !is EvCommand.Unknown) {
            handle(parsed, text)
            return
        }

        // Adhoori baat \u2014 poochh lete hain. Wahi sawaal dobara nahi puchte,
        // warna do log aamne saamne sawaal karte reh jayenge.
        val ask = askFor(text)
        if (ask != null && ask.question != askedBefore) {
            pendingAsk = ask
            status = ask.question.uppercase()
            if (speakReplies) Speaker.speak(ask.question)
            return
        }

        scope.launch {
            busy = true
            status = "SOCH RAHA HOON\u2026"

            val outcome = AiCommandResolver.resolve(context, text, installedApps)

            if (outcome is AiOutcome.Resolved) {
                busy = false
                handle(outcome.command, text)
                return@launch
            }

            // Command nahi bana \u2014 shayad ye sawaal hai. Groq se jawab lo.
            val reply = com.ev.android.feature.ai.Conversation.answer(context, text)
            busy = false

            val message = reply
                ?: (outcome as? AiOutcome.Failed)?.reason
                ?: "Samajh nahi aaya. API KEY pe tap karke Groq key daal do to main sawaalon ke jawab bhi de sakta hoon."

            status = message.uppercase()

            CommandHistory.add(
                context = context,
                spoken = text,
                understood = if (reply != null) "Sawaal (AI)" else "Samajh nahi aaya",
                reply = message,
            )

            if (speakReplies) Speaker.speak(message)
        }
    }

    val startVoice = rememberVoiceCommand(
        onResult = { spoken ->
            listening = false
            resumeHandsFree()
            input = spoken
            runCommand(spoken)
        },
        onError = {
            listening = false
            resumeHandsFree()
            notify(it)
        },
    )

    /**
     * Mic button.
     *
     * Whisper tabhi chalta hai jab user ne Settings me khud on kiya ho aur key
     * ho. Baaki har haal me Google recognizer \u2014 wo offline bhi kaam kar jata
     * hai, isliye default wahi hai.
     */
    fun startListening() {
        val wasHandsFree = EvListeningService.isRunning
        if (wasHandsFree) EvListeningService.stop(context)
        handsFreePaused = wasHandsFree

        // Bolte waqt orb dikhna chahiye \u2014 koi page khula ho to band kar dete hain.
        showSettings = false
        showOrbEditor = false

        if (!EvSettings.whisperStt(context)) {
            listening = true
            status = "SUN RAHA HOON\u2026"
            startVoice()
            return
        }

        listening = true
        status = "SUN RAHA HOON\u2026 (WHISPER)"

        scope.launch {
            val result = WhisperInput.listen(context)
            listening = false
            resumeHandsFree()

            result.fold(
                onSuccess = { spoken ->
                    input = spoken
                    runCommand(spoken)
                },
                onFailure = { error ->
                    notify(error.message ?: "Whisper se nahi ho paya")
                },
            )
        }
    }

    /**
     * Sawaal poochte hi mic khud khul jata hai \u2014 jawab dene ke liye button
     * dabana pade to poora faayda hi chala jata hai.
     *
     * Thoda ruk ke, taaki E.V apna hi sawaal na sun le.
     */
    LaunchedEffect(pendingAsk) {
        val ask = pendingAsk ?: return@LaunchedEffect
        delay(900L + ask.question.length * 55L)
        if (pendingAsk != null) startListening()
    }

    /** Bubble se aaye ho \u2014 seedha mic, ya bheji hui command. */
    LaunchedEffect(autoListen, autoCommand) {
        if (!autoCommand.isNullOrBlank()) {
            input = autoCommand
            runCommand(autoCommand)
            return@LaunchedEffect
        }
        if (autoListen) {
            delay(250L)
            startListening()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EvBlack,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                // Poore page \u2014 inke khulte hi home screen chhup jaati hai.
                showSettings -> SettingsPanel(
                    onClose = {
                        showSettings = false
                        apiKeySet = EvSettings.hasApiKey(context)
                    },
                    onSaved = {
                        apiKeySet = EvSettings.hasApiKey(context)
                        notify(it)
                    },
                )

                showOrbEditor -> OrbEditorPanel(
                    style = orbStyle,
                    onChange = { updated ->
                        orbStyle = updated
                        OrbStyleStore.save(context, updated)
                    },
                    onClose = { showOrbEditor = false },
                )

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    HudHeader(
                        networkOnline = true,
                        onSettings = {
                            showOrbEditor = false
                            showSettings = true
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    HudTabs(
                        tabs = tabs,
                        selected = tab,
                        onSelect = { tab = it },
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    HudStatusRow(
                        micOn = handsFree,
                        coreActive = true,
                        apiKeySet = apiKeySet,
                        onMicClick = { toggleHandsFree() },
                        onApiKeyClick = { showApiKey = true },
                    )

                    if (busy) {
                        LinearProgressIndicator(
                            color = EvGreen,
                            trackColor = EvSurfaceHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (tab) {
                            TAB_COMMAND -> CommandPane(
                                listening = listening,
                                busy = busy,
                                status = status,
                                orbStyle = orbStyle,
                                onOrbTap = { showOrbEditor = true },
                            )

                            TAB_REMINDER -> RemindersPane(onNotify = { notify(it) })

                            TAB_ALIASES -> AliasesPane(onNotify = { notify(it) })

                            TAB_NOTES -> NotesPane(
                                notes = Notes.items,
                                onAdd = { text ->
                                    Notes.add(context, text)
                                    notify("Note save ho gaya")
                                },
                                onDelete = { note -> Notes.remove(context, note) },
                            )

                            TAB_GALLERY -> GalleryPane(
                                items = gallery,
                                loading = galleryLoading,
                                onOpen = { item -> EvGallery.open(context, item) },
                            )

                            else -> HistoryPane(
                                entries = CommandHistory.entries,
                                onRepeat = { entry -> runCommand(entry.spoken) },
                                onClear = {
                                    CommandHistory.clear(context)
                                    notify("History saaf kar di")
                                },
                            )
                        }
                    }

                    HudActionBar(
                        busy = busy,
                        listening = listening,
                        onCamera = { handle(EvCommand.TakePhoto(front = false)) },
                        onStop = {
                            Speaker.stop()
                            listening = false
                            pendingAsk = null
                            resumeHandsFree()
                            status = "READY \u2014 BOLO YA LIKHO"
                        },
                        onMic = { startListening() },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = {
                            Text("type a command\u2026", color = EvTextMuted, fontSize = 15.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EvTextPrimary,
                            unfocusedTextColor = EvTextPrimary,
                            focusedBorderColor = EvGreen,
                            unfocusedBorderColor = EvOutline,
                            cursorColor = EvGreen,
                            focusedContainerColor = EvSurfaceHigh,
                            unfocusedContainerColor = EvSurfaceHigh,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { runCommand(input) }),
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = input.isNotBlank() && !busy) {
                                        runCommand(input)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "\u2192",
                                    color = if (input.isNotBlank()) EvGreen else EvTextMuted,
                                    fontSize = 22.sp,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showApiKey) {
        ApiKeyDialog(
            onDismiss = {
                showApiKey = false
                apiKeySet = EvSettings.hasApiKey(context)
            },
            onSaved = {
                apiKeySet = EvSettings.hasApiKey(context)
                notify(it)
            },
        )
    }
}

// --------------------------------------------------------------- sawaal-jawab

/**
 * Ek adhoori command aur uska sawaal.
 *
 * [rebuild] jawab ko wapas ek poore vaakya me jod deta hai, jise phir wahi
 * purana parser padhta hai. Isse koi naya command type banane ki zaroorat
 * nahi padi \u2014 jitna kam naya code, utni kam nayi galtiyan.
 */
private data class PendingAsk(
    val question: String,
    val rebuild: (String) -> String,
)

private fun askNormalize(input: String): String = input
    .lowercase()
    .replace(Regex("[?!.,;:\"']"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun askHasWord(text: String, word: String): Boolean =
    Regex("(^|\\s)" + Regex.escape(word) + "($|\\s)").containsMatchIn(text)

private val cancelAnswers = setOf(
    "cancel", "rehne do", "rahne do", "chhodo", "chodo", "chhod do",
    "kuch nahi", "koi nahi", "nahi", "no", "band karo", "stop",
)

private fun isCancelAnswer(text: String): Boolean = askNormalize(text) in cancelAnswers

/**
 * Baat adhoori lagi to uska sawaal, warna null.
 *
 * Yahan sirf wahi soorat pakadte hain jahan **saaf** pata ho ki kya cheez
 * kam hai. Shak hone par kuch nahi puchte \u2014 galat sawaal "samajh nahi aaya"
 * se bhi zyada chidhata hai.
 */
private fun askFor(raw: String): PendingAsk? {
    val text = askNormalize(raw)
    if (text.isEmpty()) return null

    val words = text.split(" ")
    val koIndex = words.indexOf("ko")
    val name = if (koIndex > 0) words.take(koIndex).joinToString(" ") else ""

    // "note karo" \u2014 par likhna kya hai?
    if (askHasWord(text, "note") && words.size <= 4) {
        return PendingAsk("Kya note karun?") { answer -> "note karo " + answer }
    }

    // "call lagao" \u2014 kisko?
    if ((askHasWord(text, "call") || askHasWord(text, "dial")) && name.isEmpty()) {
        return PendingAsk("Kisko call karun?") { answer -> answer + " ko call karo" }
    }

    val messageish = askHasWord(text, "message") || askHasWord(text, "msg") ||
        askHasWord(text, "sms") || askHasWord(text, "whatsapp") ||
        askHasWord(text, "bhejo") || askHasWord(text, "bhej")

    if (messageish) {
        // Naam mil chuka hai par matter nahi \u2014 ab wo poochte hain.
        if (name.isNotEmpty()) {
            return PendingAsk("Kya message bhejun?") { answer -> name + " ko bolo ki " + answer }
        }
        return PendingAsk("Kisko bhejna hai?") { answer -> answer + " ko message bhejo" }
    }

    val hasNumber = text.any { it.isDigit() }

    if (askHasWord(text, "alarm") && !hasNumber) {
        return PendingAsk("Kitne baje ka alarm?") { answer ->
            val clean = askNormalize(answer)
            if (askHasWord(clean, "baje")) clean + " alarm lagao" else clean + " baje alarm lagao"
        }
    }

    if (askHasWord(text, "timer") && !hasNumber) {
        return PendingAsk("Kitne minute ka timer?") { answer ->
            val clean = askNormalize(answer)
            if (askHasWord(clean, "timer")) clean else clean + " ka timer lagao"
        }
    }

    // "gaana lagao" \u2014 kaunsa?
    val songish = askHasWord(text, "gaana") || askHasWord(text, "gana") ||
        askHasWord(text, "song") || askHasWord(text, "music")
    if (songish && words.size <= 4) {
        return PendingAsk("Kaunsa gaana lagau?") { answer -> "youtube pe " + answer + " lagao" }
    }

    // Sirf "kholo" \u2014 kaunsi app?
    if (words.size <= 2 && (askHasWord(text, "kholo") || askHasWord(text, "khol") ||
            askHasWord(text, "open"))
    ) {
        return PendingAsk("Kaunsi app kholun?") { answer -> answer + " kholo" }
    }

    return null
}

/**
 * Beech wala shaant sa screen \u2014 sirf orb aur ek line status.
 *
 * Orb pe **double tap** karne se uska design page khulta hai (rang, shakl,
 * size, lehar waghairah). Single tap jaan boojh ke kuch nahi karta \u2014 orb bada
 * hai, chalte-phirte galti se chhoo jaana aam baat hai.
 */
@Composable
private fun CommandPane(
    listening: Boolean,
    busy: Boolean,
    status: String,
    orbStyle: OrbStyle,
    onOrbTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(orbStyle.sizeDp.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onOrbTap() })
                },
            contentAlignment = Alignment.Center,
        ) {
            EvOrb(
                listening = listening,
                busy = busy,
                dimmed = false,
                style = orbStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = status,
            color = EvGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 22.dp, start = 12.dp, end = 12.dp),
        )

        Text(
            text = "ORB PE DOUBLE TAP \u2192 DESIGN BADLO",
            color = EvTextMuted,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Jo yaad dilane ko kaha tha.
 *
 * Pehle ye Settings me chhupa tha, jo galat jagah thi \u2014 setting ek baar
 * karte ho, reminder roz dekhte ho. Ab ye apna tab hai.
 */
@Composable
private fun RemindersPane(
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    var refresh by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<Reminder>>(emptyList()) }

    LaunchedEffect(refresh) { items = Reminders.upcoming(context) }

    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "KOI REMINDER NAHI\n\n\"2 MINUTE BAAD YAAD DILANA PAANI PEENA HAI\" BOLO",
                color = EvTextMuted,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item { HudSectionLabel("// aane wale (" + items.size + ")") }

        items(items, key = { it.id }) { reminder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvSurfaceHigh)
                    .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = reminder.text, color = EvTextPrimary, fontSize = 14.sp)
                    Text(
                        text = formatter.format(Date(reminder.at)),
                        color = EvGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "\u2715",
                    color = EvTextMuted,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            Reminders.cancel(context, reminder.id)
                            refresh++
                            onNotify("Reminder hata diya")
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Galat suni gayi naam ki list.
 *
 * Recognizer "Kais" ko "case" likh deta hai. Har phone aur har contact pe ye
 * alag hota hai, isliye list user ki apni hai.
 */
@Composable
private fun AliasesPane(
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(EvSettings.aliasesRaw(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HudSectionLabel("// naam ki list")

        Text(
            text = "Ek line me ek: pehle jo suna jata hai, phir asli naam.\n\n" +
                "case = Kais\na man = Armaan",
            color = EvTextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            singleLine = false,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("case = Kais", color = EvTextMuted, fontSize = 14.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = EvTextPrimary,
                unfocusedTextColor = EvTextPrimary,
                focusedBorderColor = EvGreen,
                unfocusedBorderColor = EvOutline,
                cursorColor = EvGreen,
                focusedContainerColor = EvSurfaceHigh,
                unfocusedContainerColor = EvSurfaceHigh,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EvGreen.copy(alpha = 0.10f))
                .border(BorderStroke(1.5.dp, EvGreen), RoundedCornerShape(14.dp))
                .clickable {
                    EvSettings.setAliasesRaw(context, text)
                    onNotify("Naam ki list save ho gayi")
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SAVE",
                color = EvGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}

/**
 * Chhote notes.
 *
 * Bolke bhi likhwa sakte ho \u2014 mic dabao, bolo, phir text yahan paste karke
 * save kar do. Sab kuch phone me hi rehta hai.
 */
@Composable
private fun NotesPane(
    notes: List<Note>,
    onAdd: (String) -> Unit,
    onDelete: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("naya note\u2026", color = EvTextMuted, fontSize = 14.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = EvTextPrimary,
                unfocusedTextColor = EvTextPrimary,
                focusedBorderColor = EvGreen,
                unfocusedBorderColor = EvOutline,
                cursorColor = EvGreen,
                focusedContainerColor = EvSurfaceHigh,
                unfocusedContainerColor = EvSurfaceHigh,
            ),
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = draft.isNotBlank()) {
                            onAdd(draft)
                            draft = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = if (draft.isNotBlank()) EvGreen else EvTextMuted,
                        fontSize = 24.sp,
                    )
                }
            },
        )

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "ABHI KOI NOTE NAHI",
                    color = EvTextMuted,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { HudSectionLabel("// notes (" + notes.size + ")") }

            items(notes, key = { it.at.toString() + it.text }) { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EvSurfaceHigh)
                        .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = note.text, color = EvTextPrimary, fontSize = 14.sp)
                        Text(
                            text = formatter.format(Date(note.at)),
                            color = EvTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "\u2715",
                        color = EvTextMuted,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDelete(note) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** E.V se li hui photo aur video. Tap karo to phone ki gallery me khulti hain. */
@Composable
private fun GalleryPane(
    items: List<EvGallery.Item>,
    loading: Boolean,
    onOpen: (EvGallery.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (loading) "LOAD HO RAHA HAI\u2026"
                else "ABHI KOI PHOTO NAHI \u2014 \"PHOTO LO\" BOLO",
                color = EvTextMuted,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            GalleryTile(item = item, onOpen = { onOpen(item) })
        }
    }
}

@Composable
private fun GalleryTile(item: EvGallery.Item, onOpen: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(item.uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.uri) {
        thumb = EvGallery.thumbnail(context, item.uri)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(EvSurfaceHigh)
            .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (item.isVideo) "\uD83C\uDFA5" else "\uD83D\uDCF7",
                fontSize = 28.sp,
            )
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EvBlack)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = "VIDEO", color = EvGreen, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/**
 * Pichle commands.
 *
 * Sabse bada faida debugging hai: "kya suna" aur "kya samjha" alag alag dikhta
 * hai, to galti kahan hui ye turant pata chal jata hai.
 */
@Composable
private fun HistoryPane(
    entries: List<HistoryEntry>,
    onRepeat: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "ABHI KOI COMMAND NAHI",
                color = EvTextMuted,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudSectionLabel("// history", modifier = Modifier.weight(1f))
                Text(
                    text = "SAAF KARO",
                    color = EvTextMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        items(entries.take(30), key = { it.at.toString() + it.spoken }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EvSurfaceHigh)
                    .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(14.dp))
                    .clickable { onRepeat(entry) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.spoken,
                        color = EvTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatter.format(Date(entry.at)),
                        color = EvTextMuted,
                        fontSize = 11.sp,
                    )
                }

                Text(
                    text = "\u2192 " + entry.understood,
                    color = EvGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )

                HorizontalDivider(
                    color = EvOutline,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                Text(
                    text = entry.reply,
                    color = EvTextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LauncherScreenPreview() {
    EVTheme {
        LauncherScreen()
    }
}
