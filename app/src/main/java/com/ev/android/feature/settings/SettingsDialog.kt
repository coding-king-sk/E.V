@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ev.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import kotlinx.coroutines.launch

/**
 * API key yahin paste hoti hai aur phone me hi rehti hai.
 * Isi wajah se key repo me daalne ki zaroorat nahi padti.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(EvSettings.apiKey(context)) }
    var aiOn by remember { mutableStateOf(EvSettings.aiEnabled(context)) }
    var personalOn by remember { mutableStateOf(EvSettings.sendPersonalToAi(context)) }

    var offlineWake by remember { mutableStateOf(EvSettings.offlineWakeWord(context)) }
    var modelUrl by remember { mutableStateOf(EvSettings.wakeWordModelUrl(context)) }
    var keywords by remember { mutableStateOf(EvSettings.wakeWordKeywords(context)) }
    var modelReady by remember { mutableStateOf(WakeWordModel.isInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val libraryOk = remember { SherpaWakeWord.isLibraryAvailable() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Jo command E.V khud na samajh paye, wahi AI ko bheja jayega. " +
                        "Baaki sab pehle jaisa offline hi chalega.",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    label = { Text("Groq API key") },
                    placeholder = { Text("gsk_...") },
                )

                Text(
                    text = "console.groq.com/keys se free milti hai. Key sirf is phone " +
                        "me save hoti hai.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                SettingRow(
                    label = "AI fallback on",
                    checked = aiOn,
                    onChange = { aiOn = it },
                )

                SettingRow(
                    label = "Message/call ka text bhi AI ko bhejo",
                    checked = personalOn,
                    onChange = { personalOn = it },
                )

                Text(
                    text = "Ye off rakhna behtar hai. In commands me contact ka naam aur " +
                        "message ka text hota hai, aur free tier pe providers prompts ko " +
                        "training ke liye use kar sakte hain.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = "Offline wake word",
                    style = MaterialTheme.typography.titleSmall,
                )

                Text(
                    text = "Wake word phone ke andar hi pakda jayega, internet ke bina " +
                        "aur bina awaaz kahin bheje. Iske liye ek ~4 MB ka model " +
                        "download karna padta hai.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (!libraryOk) {
                    Text(
                        text = "\u26A0 Native library is phone pe nahi mili. Offline wake " +
                            "word kaam nahi karega — app Google recognizer pe chalti " +
                            "rahegi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                SettingRow(
                    label = "Offline wake word use karo",
                    checked = offlineWake,
                    onChange = { offlineWake = it },
                )

                Text(
                    text = when {
                        downloading -> status.ifBlank { "Download ho raha hai\u2026" }
                        status.isNotBlank() -> status
                        modelReady -> "Model ready hai"
                        else -> "Model abhi download nahi hua"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(
                        enabled = !downloading,
                        onClick = {
                            downloading = true
                            status = "Download shuru\u2026"
                            val url = modelUrl.ifBlank { WakeWordModel.DEFAULT_URL }
                            scope.launch {
                                val result = WakeWordModel.download(
                                    context = context,
                                    url = url,
                                    onProgress = { percent ->
                                        status = if (percent >= 0) {
                                            "Download $percent%"
                                        } else {
                                            "Download ho raha hai\u2026"
                                        }
                                    },
                                )
                                downloading = false
                                modelReady = WakeWordModel.isInstalled(context)
                                status = result.fold(
                                    onSuccess = { "Model ready hai" },
                                    onFailure = { "Nahi hua: ${it.message}" },
                                )
                            }
                        },
                    ) {
                        Text(if (modelReady) "Dobara download" else "Model download karo")
                    }

                    if (modelReady) {
                        TextButton(
                            enabled = !downloading,
                            onClick = {
                                WakeWordModel.delete(context)
                                modelReady = false
                                status = "Model hata diya"
                            },
                        ) {
                            Text("Hatao")
                        }
                    }
                }

                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    label = { Text("Keyword (model ke tokens me)") },
                    placeholder = { Text("khaali = model ki apni list") },
                )

                Text(
                    text = "Yahan seedha \"E.V\" likhne se kaam nahi chalega — keyword " +
                        "model ke tokens me likhna padta hai. Khaali chhod do to model " +
                        "ki apni keywords.txt chalegi.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = { modelUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    label = { Text("Model URL (optional)") },
                    placeholder = { Text("khaali = default") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    EvSettings.setApiKey(context, key)
                    EvSettings.setAiEnabled(context, aiOn)
                    EvSettings.setSendPersonalToAi(context, personalOn)
                    EvSettings.setOfflineWakeWord(context, offlineWake)
                    EvSettings.setWakeWordModelUrl(context, modelUrl)
                    EvSettings.setWakeWordKeywords(context, keywords)
                    onSaved(
                        if (EvSettings.aiEnabled(context)) "AI fallback chalu hai"
                        else "AI fallback band hai"
                    )
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
