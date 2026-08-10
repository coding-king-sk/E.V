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
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.apps.InstalledAppsRepository
import com.ev.android.feature.command.CommandExecutor
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.contacts.ContactsRepository
import com.ev.android.feature.voice.rememberVoiceCommand
import com.ev.android.ui.theme.EVTheme
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
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
                if (quickShortcuts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Quick actions")
                    }
                    items(quickShortcuts, key = { "quick_${it.id}" }) { shortcut ->
                        TileCard(
                            label = shortcut.label,
                            onClick = {
                                val message = when (val result = AppLauncher.launch(context, shortcut)) {
                                    is LaunchResult.Opened -> "${result.label} khul raha hai"
                                    is LaunchResult.Fallback -> "${result.label}: ${result.reason}"
                                    is LaunchResult.Failed -> "${result.label} open nahi ho paya"
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
                        when {
                            loadingApps -> "Phone ke apps load ho rahe hain\u2026"
                            else -> "Phone ke apps (${matchingApps.size})"
                        }
                    )
                }

                items(matchingApps, key = { "app_${it.packageName}" }) { app ->
                    TileCard(
                        label = app.label,
                        onClick = {
                            val opened = AppLauncher.launchPackage(context, app.packageName)
                            notify(
                                if (opened) "${app.label} khul raha hai"
                                else "${app.label} open nahi ho paya"
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
