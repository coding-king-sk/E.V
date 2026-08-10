@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.ev.android.feature.launcher

import android.Manifest
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
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.apps.InstalledAppsRepository
import com.ev.android.feature.command.CommandExecutor
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.contacts.ContactsRepository
import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.voice.rememberVoiceCommand
import com.ev.android.ui.theme.EVTheme
import kotlinx.coroutines.launch

private data class DeviceTile(
    val label: String,
    val emoji: String,
    val action: DeviceAction,
)

private val deviceTiles = listOf(
    DeviceTile("Torch", "\uD83D\uDD26", DeviceAction.TORCH_TOGGLE),
    DeviceTile("WiFi", "\uD83D\uDCF6", DeviceAction.WIFI_PANEL),
    DeviceTile("Bluetooth", "\uD83D\uDD35", DeviceAction.BLUETOOTH),
    DeviceTile("Volume +", "\uD83D\uDD0A", DeviceAction.VOLUME_UP),
    DeviceTile("Volume \u2212", "\uD83D\uDD09", DeviceAction.VOLUME_DOWN),
    DeviceTile("Silent", "\uD83D\uDD15", DeviceAction.RINGER_SILENT),
    DeviceTile("Brightness", "\u2600\uFE0F", DeviceAction.BRIGHTNESS_MAX),
    DeviceTile("Screenshot", "\uD83D\uDDBC\uFE0F", DeviceAction.SCREENSHOT),
    DeviceTile("Lock", "\uD83D\uDD12", DeviceAction.LOCK_SCREEN),
    DeviceTile("Settings", "\u2699\uFE0F", DeviceAction.SETTINGS),
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

    LaunchedEffect(Unit) {
        accessibilityOn = AccessibilityHelper.isEnabled(context)
        installedApps = InstalledAppsRepository.load(context)
        loadingApps = false
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
            snackbarHostState.showSnackbar(result.message)
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val queued = pendingCommand
        pendingCommand = null
        when {
            queued == null -> Unit
            granted -> dispatch(queued)
            else -> notify("Contacts permission ke bina naam se message nahi bhej sakta")
        }
    }

    fun runCommand(text: String) {
        if (text.isBlank()) return
        keyboard?.hide()

        val command = CommandParser.parse(text, installedApps)

        val needsContacts = command is EvCommand.SendWhatsApp &&
            !command.contactName.isNullOrBlank() &&
            !ContactsRepository.hasPermission(context)

        if (needsContacts) {
            pendingCommand = command
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }

        dispatch(command)
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
    val matchingApps = remember(input, installedApps) {
        val q = input.trim()
        if (q.isEmpty()) installedApps else installedApps.filter { it.label.contains(q, true) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("E.V") }) },
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

                if (visibleDeviceTiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Device controls")
                    }
                    items(visibleDeviceTiles, key = { "device_${it.action.name}" }) { tile ->
                        TileCard(
                            label = tile.label,
                            onClick = { dispatch(EvCommand.Device(tile.action)) },
                        ) {
                            