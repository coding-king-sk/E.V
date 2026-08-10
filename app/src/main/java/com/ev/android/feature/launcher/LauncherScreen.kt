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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.ai.AiCommandResolver
import com.ev.android.feature.ai.AiOutcome
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.apps.InstalledAppsRepository
import com.ev.android.feature.command.CommandExecutor
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.history.CommandHistory
import com.ev.android.feature.history.HistoryEntry
import com.ev.android.feature.hud.EvOrb
import com.ev.android.feature.hud.HudActionBar
import com.ev.android.feature.hud.HudHeader
import com.ev.android.feature.hud.HudSectionLabel
import com.ev.android.feature.hud.HudStatusRow
import com.ev.android.feature.hud.HudTabs
import com.ev.android.feature.hud.HudTile
import com.ev.android.feature.media.MediaAction
import com.ev.android.feature.settings.EvSettings
import com.ev.android.feature.settings.SettingsDialog
import com.ev.android.feature.tts.Speaker
import com.ev.android.feature.tts.VoiceSetup
import com.ev.android.feature.voice.EvListeningService
import com.ev.android.feature.voice.rememberVoiceCommand
import com.ev.android.ui.theme.EVTheme
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DeviceTile(val label: String, val emoji: String, val action: DeviceAction)

/** action == null ka matlab "ye gaana kaun sa hai". */
private data class MediaTile(val label: String, val emoji: String, val action: MediaAction?)

private const val TAB_COMMAND = "COMMAND"
private const val TAB_APPS = "APPS"
private const val TAB_TOOLS = "TOOLS"
private const val TAB_HISTORY = "HISTORY"

private val tabs = listOf(TAB_COMMAND, TAB_APPS, TAB_TOOLS, TAB_HISTORY)

private val deviceTiles = listOf(
    DeviceTile("Torch", "\uD83D\uDD26", DeviceAction.TORCH_TOGGLE),
    DeviceTile("WiFi", "\uD83D\uDCF6", DeviceAction.WIFI_PANEL),
    DeviceTile("Bluetooth", "\uD83D\uDD35", DeviceAction.BLUETOOTH),
    DeviceTile("Volume +", "\uD83D\uDD0A", DeviceAction.VOLUME_UP),
    DeviceTile("Volume \u2212", "\uD83D\uDD09", DeviceAction.VOLUME_DOWN),
    DeviceTile("Silent", "\uD83D\uDD15", DeviceAction.RINGER_SILENT),
    DeviceTile("Brightness", "\u2600", DeviceAction.BRIGHTNESS_MAX),
    DeviceTile("Screenshot", "\uD83D\uDDBC", DeviceAction.SCREENSHOT),
    DeviceTile("Lock", "\uD83D\uDD12", DeviceAction.LOCK_SCREEN),
    DeviceTile("Settings", "\u2699", DeviceAction.SETTINGS),
)

private val mediaTiles = listOf(
    MediaTile("Pause", "\u23F8", MediaAction.PAUSE),
    MediaTile("Play", "\u25B6", MediaAction.PLAY),
    MediaTile("Next", "\u23ED", MediaAction.NEXT),
    MediaTile("Previous", "\u23EE", MediaAction.PREVIOUS),
    MediaTile("Aage", "\u23E9", MediaAction.FORWARD),
    MediaTile("Peeche", "\u23EA", MediaAction.REWIND),
    MediaTile("Kaun sa gaana?", "\uD83C\uDFA7", null),
)

/**
 * E.V ki main screen — HUD look.
 *
 * Design jaan-boojh ke "ek cheez pe dhyan" wala hai: beech me bas orb, jo
 * batata hai E.V so raha hai, sun raha hai ya soch raha hai. Apps, tools aur
 * history tabs ke peeche hain taaki roz ka istemal shaant lage.
 */
@Composable
fun LauncherScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    var input by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(TAB_COMMAND) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<EvCommand?>(null) }
    var accessibilityOn by remember { mutableStateOf(false) }
    var apiKeySet by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(EvListeningService.isRunning) }
    var status by remember { mutableStateOf("READY \u2014 BOLO YA LIKHO") }

    LaunchedEffect(Unit) {
        CommandHistory.load(context)
        accessibilityOn = AccessibilityHelper.isEnabled(context)
        apiKeySet = EvSettings.hasApiKey(context)
        handsFree = EvListeningService.isRunning
        installedApps = InstalledAppsRepository.load(context)
        loadingApps = false
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    /**
     * TTS engine app ke saath zinda rehta hai.
     *
     * Agar acchi Hindi awaz phone me hai hi nahi, to bina kuch poochhe seedha
     * Google ki voice-data install screen khul jaati hai — user ko bas
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
            accessibilityOn = AccessibilityHelper.isEnabled(context)

            CommandHistory.add(
                context = context,
                spoken = spoken ?: ("\uD83D\uDC46 " + CommandHistory.describe(command)),
                understood = CommandHistory.describe(command),
                reply = result.message,
            )

            if (speakReplies) Speaker.speak(result.message)
            snackbarHostState.showSnackbar(result.message)
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

            // Ek hi vaakya me kai kaam — sabki permissions ek saath maang lo,
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
        // Permission mile ya na mile, command chala dete hain — executor khud
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
        notify("Ab bas \"Hey E.V\" bolo")
    }

    /**
     * App khulte hi hands-free apne aap on.
     *
     * Toggle sirf isliye bacha hai ki kabhi khud band karna ho — roz roz on
     * karne ki zaroorat nahi.
     */
    LaunchedEffect(Unit) {
        if (EvListeningService.isRunning) return@LaunchedEffect

        val missing = micPermissionsMissing()
        if (missing.isEmpty()) {
            EvListeningService.start(context)
            handsFree = true
        } else {
            micLauncher.launch(missing.toTypedArray())
        }
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
     * Roz ke command turant aur free me chalte hain; network sirf tab lagta
     * hai jab baat ghumavdar ho ya sawaal ho.
     */
    fun runCommand(text: String) {
        if (text.isBlank()) return
        keyboard?.hide()

        val parsed = CommandParser.parse(text, installedApps)
        if (parsed !is EvCommand.Unknown) {
            handle(parsed, text)
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

            // Command nahi bana — shayad ye sawaal hai. Groq se jawab lo.
            val reply = com.ev.android.feature.ai.Conversation.answer(context, text)
            busy = false

            val message = reply
                ?: (outcome as? AiOutcome.Failed)?.reason
                ?: "Samajh nahi aaya. Settings me Groq key daal do to main sawaalon ke jawab bhi de sakta hoon."

            status = message.uppercase()

            CommandHistory.add(
                context = context,
                spoken = text,
                understood = if (reply != null) "Sawaal (AI)" else "Samajh nahi aaya",
                reply = message,
            )

            if (speakReplies) Speaker.speak(message)
            snackbarHostState.showSnackbar(message)
        }
    }

    val startVoice = rememberVoiceCommand(
        onResult = { spoken ->
            listening = false
            input = spoken
            runCommand(spoken)
        },
        onError = {
            listening = false
            notify(it)
        },
    )

    val quickShortcuts = remember(input) { AppCatalog.shortcuts.filter { it.matches(input) } }
    val matchingApps = remember(input, installedApps) {
        val q = input.trim()
        if (q.isEmpty()) installedApps else installedApps.filter { it.label.contains(q, true) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EvBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            HudHeader(
                networkOnline = true,
                onSettings = { showSettings = true },
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            HudTabs(tabs = tabs, selected = tab, onSelect = { tab = it })

            Spacer(modifier = Modifier.height(12.dp))

            HudStatusRow(
                micOn = handsFree,
                coreActive = true,
                apiKeySet = apiKeySet,
                onMicClick = { toggleHandsFree() },
                onApiKeyClick = { showSettings = true },
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
                        accessibilityOn = accessibilityOn,
                        onEnableAccessibility = {
                            AccessibilityHelper.openSettings(context)
                            notify("List me 'E.V auto-send' dhoond ke on kar do")
                        },
                    )

                    TAB_APPS -> AppsPane(
                        loadingApps = loadingApps,
                        quickShortcuts = quickShortcuts,
                        matchingApps = matchingApps,
                        onShortcut = { shortcut ->
                            val message = when (val result = AppLauncher.launch(context, shortcut)) {
                                is LaunchResult.Opened -> result.label + " khul raha hai"
                                is LaunchResult.Fallback -> result.label + ": " + result.reason
                                is LaunchResult.Failed -> result.label + " open nahi ho paya"
                            }
                            notify(message)
                        },
                        onApp = { app ->
                            val opened = AppLauncher.launchPackage(context, app.packageName)
                            notify(
                                if (opened) app.label + " khul raha hai"
                                else app.label + " open nahi ho paya"
                            )
                        },
                    )

                    TAB_TOOLS -> ToolsPane(
                        onDevice = { action -> dispatch(EvCommand.Device(action)) },
                        onMedia = { action ->
                            dispatch(
                                if (action == null) EvCommand.IdentifySong
                                else EvCommand.Media(action)
                            )
                        },
                        onCamera = { front -> handle(EvCommand.TakePhoto(front = front)) },
                        onVideo = { handle(EvCommand.RecordVideo(front = false, seconds = 15)) },
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
                    status = "READY \u2014 BOLO YA LIKHO"
                },
                onMic = {
                    listening = true
                    status = "SUN RAHA HOON\u2026"
                    startVoice()
                },
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

    if (showSettings) {
        SettingsDialog(
            onDismiss = {
                showSettings = false
                apiKeySet = EvSettings.hasApiKey(context)
            },
            onSaved = {
                apiKeySet = EvSettings.hasApiKey(context)
                notify(it)
            },
        )
    }
}

/** Beech wala shaant sa screen — sirf orb aur ek line status. */
@Composable
private fun CommandPane(
    listening: Boolean,
    busy: Boolean,
    status: String,
    accessibilityOn: Boolean,
    onEnableAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EvOrb(
            listening = listening,
            busy = busy,
            dimmed = !listening && !busy,
            modifier = Modifier.size(230.dp),
        )

        Text(
            text = status,
            color = EvGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 26.dp, start = 12.dp, end = 12.dp),
        )

        Text(
            text = "ENGINE LOCAL",
            color = EvTextMuted,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (!accessibilityOn) {
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(14.dp))
                    .background(EvSurfaceHigh)
                    .clickable(onClick = onEnableAccessibility)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "AUTO-SEND / TYPING OFF \u2014 ON KARO",
                    color = EvTextMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun AppsPane(
    loadingApps: Boolean,
    quickShortcuts: List<AppShortcut>,
    matchingApps: List<InstalledApp>,
    onShortcut: (AppShortcut) -> Unit,
    onApp: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (quickShortcuts.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HudSectionLabel("// quick")
            }
            items(quickShortcuts, key = { "quick_" + it.id }) { shortcut ->
                HudTile(
                    label = shortcut.label,
                    onClick = { onShortcut(shortcut) },
                    modifier = Modifier.height(98.dp),
                    icon = { Text(shortcut.emoji, fontSize = 26.sp) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            HudSectionLabel(
                if (loadingApps) "// apps load ho rahe hain"
                else "// apps (" + matchingApps.size + ")"
            )
        }

        items(matchingApps, key = { "app_" + it.packageName }) { app ->
            HudTile(
                label = app.label,
                onClick = { onApp(app) },
                modifier = Modifier.height(98.dp),
                icon = {
                    val icon = app.icon
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        Text("\uD83D\uDCF1", fontSize = 26.sp)
                    }
                },
            )
        }
    }
}

@Composable
private fun ToolsPane(
    onDevice: (DeviceAction) -> Unit,
    onMedia: (MediaAction?) -> Unit,
    onCamera: (Boolean) -> Unit,
    onVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { HudSectionLabel("// camera") }

        item {
            HudTile(
                label = "Photo",
                onClick = { onCamera(false) },
                modifier = Modifier.height(98.dp),
                icon = { Text("\uD83D\uDCF7", fontSize = 26.sp) },
            )
        }
        item {
            HudTile(
                label = "Selfie",
                onClick = { onCamera(true) },
                modifier = Modifier.height(98.dp),
                icon = { Text("\uD83E\uDD33", fontSize = 26.sp) },
            )
        }
        item {
            HudTile(
                label = "Video",
                onClick = onVideo,
                modifier = Modifier.height(98.dp),
                icon = { Text("\uD83C\uDFA5", fontSize = 26.sp) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) { HudSectionLabel("// jo abhi chal raha hai") }

        items(mediaTiles, key = { "media_" + it.label }) { tile ->
            HudTile(
                label = tile.label,
                onClick = { onMedia(tile.action) },
                modifier = Modifier.height(98.dp),
                icon = { Text(tile.emoji, fontSize = 26.sp) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) { HudSectionLabel("// device") }

        items(deviceTiles, key = { "device_" + it.action.name }) { tile ->
            HudTile(
                label = tile.label,
                onClick = { onDevice(tile.action) },
                modifier = Modifier.height(98.dp),
                icon = { Text(tile.emoji, fontSize = 26.sp) },
            )
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
