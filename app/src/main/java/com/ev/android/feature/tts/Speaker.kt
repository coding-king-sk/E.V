package com.ev.android.feature.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * TTS = Text To Speech. Pipeline ka teesra hissa.
 *
 * Do cheezein khaas hain:
 *
 * 1. **Refcount** \u2014 UI aur listening service dono isi engine ko use karte hain.
 *    Pehle screen band hote hi shutdown ho jata tha aur service ki awaz mar
 *    jati thi. Ab jab tak ek bhi user baaki hai, engine zinda rehta hai.
 *
 * 2. **onDone callback** \u2014 hands-free mode me jab tak E.V bol raha hai, mic
 *    band rakhna padta hai. Warna wo apni hi awaz sun ke command samajh leta.
 */
object Speaker {

    private var engine: TextToSpeech? = null
    private var users = 0

    @Volatile
    private var ready = false

    private var pending: Pair<String, (() -> Unit)?>? = null

    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    fun init(context: Context) {
        users++
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

            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finish(utteranceId)

                @Deprecated("Older API", ReplaceWith("onError(utteranceId, errorCode)"))
                override fun onError(utteranceId: String?) = finish(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) =
                    finish(utteranceId)
            })

            ready = true
            pending?.let { (text, onDone) ->
                pending = null
                speak(text, onDone)
            }
        }
    }

    /**
     * @param onDone bolna khatam hone pe main thread pe chalta hai. Ye hamesha
     *   chalta hai \u2014 error ya stop pe bhi \u2014 taki caller kabhi atke na.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.let { main.post(it) }
            return
        }

        val tts = engine
        if (tts == null || !ready) {
            pending = text to onDone
            return
        }

        val id = "ev-" + System.currentTimeMillis()
        if (onDone != null) callbacks[id] = onDone

        val status = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (status != TextToSpeech.SUCCESS) finish(id)
    }

    fun stop() {
        engine?.stop()
    }

    @Synchronized
    fun shutdown() {
        users--
        if (users > 0) return

        users = 0
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pending = null
        callbacks.clear()
    }

    private fun finish(utteranceId: String?) {
        val callback = utteranceId?.let { callbacks.remove(it) } ?: return
        main.post(callback)
    }
}
