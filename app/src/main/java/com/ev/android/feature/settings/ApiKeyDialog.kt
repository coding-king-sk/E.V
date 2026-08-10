package com.ev.android.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ev.android.feature.hud.EvDialog
import com.ev.android.feature.hud.EvDialogHint
import com.ev.android.feature.hud.evFieldColors

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

    EvDialog(
        title = "Groq API key",
        onDismiss = onDismiss,
        onConfirm = {
            EvSettings.setApiKey(context, key)
            onSaved(if (key.isBlank()) "Key hata di" else "Groq key save ho gayi")
            onDismiss()
        },
    ) {
        EvDialogHint(
            text = "Ye key AI ke liye hai \u2014 jo command E.V khud na samajh paye " +
                "aur aapke sawaalon ke jawab. Iske bina baaki sab pehle jaisa " +
                "chalta rahega.",
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
            colors = evFieldColors(),
        )

        EvDialogHint(
            text = "console.groq.com/keys se free milti hai. Key sirf is phone me " +
                "save hoti hai, kahin bheji nahi jaati.",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
