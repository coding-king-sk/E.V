@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ev.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * API key yahin paste hoti hai aur phone me hi rehti hai.
 * Isi wajah se key repo me daalne ki zaroorat nahi padti.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current

    var key by remember { mutableStateOf(EvSettings.apiKey(context)) }
    var aiOn by remember { mutableStateOf(EvSettings.aiEnabled(context)) }
    var personalOn by remember { mutableStateOf(EvSettings.sendPersonalToAi(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI settings") },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    EvSettings.setApiKey(context, key)
                    EvSettings.setAiEnabled(context, aiOn)
                    EvSettings.setSendPersonalToAi(context, personalOn)
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
