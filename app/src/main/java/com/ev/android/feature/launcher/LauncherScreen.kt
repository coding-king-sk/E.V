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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.ev.android.feature.media.MediaAction
import com.ev.android.feature.settings.SettingsDialog
import com.ev.android.feature.tts.Speaker
import com.ev.android.feature.voice.EvListeningService
import com.ev.android.feature.voice.rememberVoiceCommand
import com.ev.android.ui.theme.EVTheme
import kotlinx.coroutines.launch

private data class DeviceTile(val label: String, val emoji: String, val action: DeviceAction)

/** action == null ka matlab "ye gaana kaun sa hai". */
private data class MediaTile(val label: String, val emoji: String, val action: MediaAction?)

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

@Composable
fun LauncherScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    var input by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<EvCommand?>(null) }
    var accessibilityOn by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(EvListeningService.isRunning) }

    LaunchedEffect(Unit) {
        accessibilityOn = AccessibilityHelper.isEnabled(context)
        handsFree = EvListeningService.isRunning
        installedApps = InstalledAppsRepository.load(context)
        loadingApps = false
    }

    // TTS engine app ke saath hi zinda rehta hai, screen band hote hi chhod dete hain.
    DisposableEffect(Unit) {
        Speaker.init(context)
        onDispose { Speaker.shutdown() }
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun dispatch(command: EvCommand) {
        scope.launch {
            busy = true
            val result = CommandExecutor.execute(context, command)
            busy = false
            accessibilityOn = AccessibilityHelper.isEnabled(context)
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
            notify("Ab bas \"Hey E.V\" bolo")
        } else {
            handsFree = false
            notify("Mic ki permission ke bina hands-free nahi chalega")
        }
    }

    fun toggleHandsFree() {
        if (EvListeningService.isRunning) {
            EvListeningService.stop(context)
            handsFree = false
            notify("Hands-free band")
            return
        }

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            micLauncher.launch(missing.toTypedArray())
            return
        }

        EvListeningService.start(context)
        handsFree = true
        notify("Ab bas \"Hey E.V\" bolo")
    }

    /** Permission maango (agar chahiye) phir command chalao. */
    fun handle(command: EvCommand) {
        val missing = missingPermissions(command)
        if (missing.isNotEmpty()) {
            pendingCommand = command
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        dispatch(command)
    }

    /**
     * Pehle offline parser, aur wo haar jaye tabhi AI.
     *
     * Isse roz ke commands turant aur free me chalte hain, aur network sirf
     * ghumavdar sentences ke liye use hota hai.
     */
    fun runCommand(text: String) {
        if (text.isBlank()) return
        keyboard?.hide()

        val command = CommandParser.parse(text, installedApps)
        if (command !is EvCommand.Unknown) {
            handle(command)
            return
        }

        scope.launch {
            busy = true
            val outcome = AiCommandResolver.resolve(context, text, installedApps)
            busy = false

            when (outcome) {
                is AiOutcome.Resolved -> handle(outcome.command)

                is AiOutcome.Failed -> {
                    if (speakReplies) Speaker.speak(outcome.reason)
                    snackbarHostState.showSnackbar(outcome.reason)
                }

                AiOutcome.Unavailable -> handle(command)
            }
        }
    }

    val startVoice = rememberVoiceCommand(
        onResult = { spoken ->
            input = spoken
            runCommand(spoken)
        },
        onError = { notify(it) },
    )

    val quickShortcuts = remember(input) { AppCatalog.shortcuts.filter { it.matches(input) } }
    val visibleDeviceTiles = remember(input) {
        val q = input.trim()
        if (q.isEmpty()) deviceTiles else deviceTiles.filter { it.label.contains(q, true) }
    }
    val visibleMediaTiles = remember(input) {
        val q = input.trim()
        if (q.isEmpty()) mediaTiles else mediaTiles.filter { it.label.contains(q, true) }
    }
    val matchingApps = remember(input, installedApps) {
        val q = input.trim()
        if (q.isEmpty()) installedApps else installedApps.filter { it.label.contains(q, true) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("E.V") },
                actions = {
                    IconButton(onClick = { toggleHandsFree() }) {
                        Text(
                            text = if (handsFree) "\uD83D\uDC42" else "\uD83D\uDCA4",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    IconButton(
                        onClick = {
                            speakReplies = !speakReplies
                            if (!speakReplies) Speaker.stop()
                            notify(
                                if (speakReplies) "Ab E.V bol ke jawab dega"
                                else "Awaz band \u2014 sirf screen pe dikhega"
                            )
                        },
                    ) {
                        Text(
                            text = if (speakReplies) "\uD83D\uDD0A" else "\uD83D\uDD07",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    IconButton(onClick = { showSettings = true }) {
                        Text("\u2699", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Bolo ya likho") },
                placeholder = { Text("YouTube pe paisa song lagao") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { runCommand(input) }),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { startVoice() }) {
                            Text("\uD83C\uDFA4", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(
                            onClick = { runCommand(input) },
                            enabled = input.isNotBlank() && !busy,
                        ) {
                            Text("\u25B6", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
            )

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!accessibilityOn) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AccessibilityBanner(
                            onEnable = {
                                AccessibilityHelper.openSettings(context)
                                notify("List me 'E.V auto-send' dhoond ke on kar do")
                            },
                        )
                    }
                }

                if (visibleMediaTiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Jo abhi chal raha hai")
                    }
                    items(visibleMediaTiles, key = { "media_" + it.label }) { tile ->
                        TileCard(
                            label = tile.label,
                            onClick = {
                                val action = tile.action
                                dispatch(
                                    if (action == null) EvCommand.IdentifySong
                                    else EvCommand.Media(action)
                                )
                            },
                        ) {
                            Text(tile.emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                if (visibleDeviceTiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Device controls")
                    }
                    items(visibleDeviceTiles, key = { "device_" + it.action.name }) { tile ->
                        TileCard(
                            label = tile.label,
                            onClick = { dispatch(EvCommand.Device(tile.action)) },
                        ) {
                            Text(tile.emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                if (quickShortcuts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Quick actions")
                    }
                    items(quickShortcuts, key = { "quick_" + it.id }) { shortcut ->
                        TileCard(
                            label = shortcut.label,
                            onClick = {
                                val message = when (val result = AppLauncher.launch(context, shortcut)) {
                                    is LaunchResult.Opened -> result.label + " khul raha hai"
                                    is LaunchResult.Fallback -> result.label + ": " + result.reason
                                    is LaunchResult.Failed -> result.label + " open nahi ho paya"
                                }
                                notify(message)
                            },
                        ) {
                            Text(shortcut.emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        if (loadingApps) "Phone ke apps load ho rahe hain\u2026"
                        else "Phone ke apps (" + matchingApps.size + ")"
                    )
                }

                items(matchingApps, key = { "app_" + it.packageName }) { app ->
                    TileCard(
                        label = app.label,
                        onClick = {
                            val opened = AppLauncher.launchPackage(context, app.packageName)
                            notify(
                                if (opened) app.label + " khul raha hai"
                                else app.label + " open nahi ho paya"
                            )
                        },
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Text("\uD83D\uDCF1", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                if (!loadingApps && matchingApps.isEmpty() && quickShortcuts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Kuch nahi mila. Mic dabake bol ke bhi try kar sakte ho.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onSaved = { notify(it) },
        )
    }
}

@Composable
private fun AccessibilityBanner(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "\u267F Auto-send band hai",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "WhatsApp ka Send button E.V khud daba sake, iske liye ek baar " +
                    "Accessibility me 'E.V auto-send' on kar do. Device ke screenshot, " +
                    "lock aur back/home buttons bhi isi se chalte hain.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onEnable, modifier = Modifier.padding(top = 4.dp)) {
                Text("Enable karo")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TileCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
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
