package com.ev.android.feature.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS = Text To Speech. Pipeline ka teesra hissa.
 *
 * STT (aawaz -> text) pehle se hai, parser (text -> matlab) beech me hai,
 * aur ab E.V jawab bol ke bhi deta hai.
 *
 * Engine init hone me thoda time lagta hai, isliye jo lines us beech aa jayen
 * unhe [pending] me rakh ke ready hote hi bol dete hain.
 */
object Speaker {

    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var pending: String? = null

    fun init(context: Context) {
        if (engine != null) return

        val appContext = context.applicationContext
        engine = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech

            // Hinglish Hindi voice me sabse natural lagta hai; na mile to English.
            val hindi = Locale("hi", "IN")
            val result = engine?.setLanguage(hindi)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine?.setLanguage(Locale.US)
            }

            ready = true
            pending?.let { queued ->
                pending = null
                speak(queued)
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        val tts = engine
        if (tts == null || !ready) {
            pending = text
            return
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ev-" + System.currentTimeMillis())
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pending = null
    }
}
