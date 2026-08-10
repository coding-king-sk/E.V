package com.ev.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Sirf Groq key ke liye chhota dialog.
 *
 * API KEY tile pehle poora Settings khol deti thi, jahan key doosri cheezon ke
 * beech dabi rehti thi. Ab tile seedha yahi kholti hai.
 */
@Composable
fun ApiKeyDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(EvSettings.apiKey(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Groq API key") },
        text = {
            Column {
                Text(
                    text = "Ye key AI ke liye hai \u2014 jo command E.V khud na samajh paye " +
                        "aur aapke sawaalon ke jawab. Iske bina baaki sab pehle jaisa " +
                        "chalta rahega.",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    label = { Text("Key") },
                    placeholder = { Text("gsk_...") },
                )

                Text(
                    text = "console.groq.com/keys se free milti hai. Key sirf is phone me " +
                        "save hoti hai, kahin bheji nahi jaati.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    EvSettings.setApiKey(context, key)
                    onSaved(
                        if (key.isBlank()) "Key hata di" else "Groq key save ho gayi"
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
