package com.ev.android.feature.voice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Mic input via the system speech recognizer.
 *
 * Uses the RecognizerIntent activity (Google app handles the mic), so E.V does
 * not need the RECORD_AUDIO permission itself.
 *
 * Returns a lambda \u2014 call it to start listening.
 */
@Composable
fun rememberVoiceCommand(
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (spoken.isNullOrEmpty()) {
            currentOnError("Kuch sunai nahi diya, dobara try karo")
        } else {
            currentOnResult(spoken)
        }
    }

    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // en-IN hi Hinglish deta hai — Latin script me, jaisa parser chahta
            // hai ("youtube kholo", "torch on karo"). hi-IN Devanagari me
            // likhta hai ("टॉर्च ऑन करो") aur parser use pehchan nahi pata.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(
                RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                arrayListOf("en-IN", "hi-IN"),
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye\u2026 jaise: YouTube pe paisa song lagao")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            currentOnError("Is phone pe voice input available nahi hai")
        }
    }
}
