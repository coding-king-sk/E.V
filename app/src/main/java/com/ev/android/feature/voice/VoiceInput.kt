package com.ev.android.feature.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Mic input — app ke andar hi.
 *
 * Pehle ye RecognizerIntent chalata tha, jisme Google ki poori-screen
 * "Boliye…" wali khidki aa jaati thi aur E.V ka apna screen dhak jaata tha.
 * Ab SpeechRecognizer seedha use hota hai, isliye sunna chupchap background me
 * hota hai aur orb hi batata hai ki mic chalu hai.
 *
 * Iski keemat ye hai ki ab RECORD_AUDIO permission app ko khud chahiye —
 * pehle Google app apni permission se kaam chala leta tha. Hands-free ke liye
 * wo permission waise bhi maangi jaati hai, isliye practically farak nahi.
 *
 * Awaaz ka level `MicLevel` me jata hai, jise orb padh kar lehrata hai.
 *
 * Returns a lambda — call it to start listening.
 */
@Composable
fun rememberVoiceCommand(
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    // Ek hi recognizer baar baar use karne se kuch phones pe wo atak jata hai,
    // isliye har baar naya banate hain aur kaam khatam hote hi chhod dete hain.
    val holder = remember { RecognizerHolder() }

    DisposableEffect(Unit) {
        onDispose { holder.release() }
    }

    return start@{
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            currentOnError("Is phone pe voice input available nahi hai")
            return@start
        }

        val micAllowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (!micAllowed) {
            currentOnError("Mic ki permission chahiye \u2014 settings me se de do")
            return@start
        }

        holder.release()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        holder.current = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = MicLevel.reset()
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit

            /** Yahi wo number hai jisse orb lehrata hai. */
            override fun onRmsChanged(rmsdB: Float) = MicLevel.update(rmsdB)

            override fun onEndOfSpeech() = MicLevel.reset()

            override fun onResults(results: Bundle?) {
                holder.release()

                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (spoken.isNullOrEmpty()) {
                    currentOnError("Kuch sunai nahi diya, dobara try karo")
                } else {
                    currentOnResult(spoken)
                }
            }

            override fun onError(error: Int) {
                holder.release()
                currentOnError(describe(error))
            }
        })

        recognizer.startListening(listenIntent(context))
    }
}

/** Recognizer ko ek jagah rakhta hai taaki saaf-safai bhulni na pade. */
private class RecognizerHolder {
    var current: SpeechRecognizer? = null

    fun release() {
        MicLevel.reset()
        val recognizer = current ?: return
        current = null
        runCatching {
            recognizer.cancel()
            recognizer.destroy()
        }
    }
}

private fun listenIntent(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        // en-IN hi Hinglish deta hai — Latin script me, jaisa parser chahta
        // hai ("youtube kholo", "torch on karo"). hi-IN Devanagari me likhta
        // hai ("\u091F\u0949\u0930\u094D\u091A \u0911\u0928 \u0915\u0930\u094B") aur parser use pehchan nahi pata.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
        putExtra(
            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
            arrayListOf("en-IN", "hi-IN"),
        )
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

private fun describe(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Mic se awaaz nahi aa payi"
    SpeechRecognizer.ERROR_CLIENT -> "Mic band ho gaya, dobara try karo"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic ki permission nahi hai"
    SpeechRecognizer.ERROR_NETWORK -> "Network nahi mila"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network slow hai, dobara try karo"
    SpeechRecognizer.ERROR_NO_MATCH -> "Samajh nahi aaya, dobara boliye"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic abhi busy hai, ek second ruko"
    SpeechRecognizer.ERROR_SERVER -> "Google ka server jawab nahi de raha"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya"
    else -> "Sunne me dikkat aayi, dobara try karo"
}
