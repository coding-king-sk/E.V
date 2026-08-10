package com.ev.android.feature.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * TTS = Text To Speech. Pipeline ka teesra hissa.
 *
 * Teen cheezein khaas hain:
 *
 * 1. **Google engine force** — phone ka default engine (Samsung/Xiaomi wala)
 *    robot jaisa bolta hai. Hum jaan bujh ke `com.google.android.tts` pakadte
 *    hain. Wo na mile tabhi default pe girte hain.
 *
 * 2. **Sabse acchi awaz** — engine ke saari voices me se highest quality wali
 *    Hindi voice chunte hain. Network voices sabse natural hoti hain.
 *
 * 3. **Refcount + onDone** — UI aur listening service dono ek hi engine share
 *    karte hain, aur hands-free mode ko pata chalta rehta hai ki bolna kab
 *    khatam hua (tab tak mic band rehta hai).
 */
object Speaker {

    private var engine: TextToSpeech? = null
    private var appContext: Context? = null
    private var users = 0
    private var triedGoogle = false

    @Volatile
    private var ready = false

    /** True jab high-quality Hindi awaz phone me hai hi nahi. */
    @Volatile
    var needsVoiceData = false
        private set

    private var pending: Pair<String, (() -> Unit)?>? = null
    private var onVoiceDataMissing: (() -> Unit)? = null

    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /**
     * @param onVoiceDataMissing tab chalta hai jab acchi Hindi awaz phone me
     *   nahi hai — UI isse download screen khol deti hai.
     */
    @Synchronized
    fun init(context: Context, onVoiceDataMissing: (() -> Unit)? = null) {
        if (onVoiceDataMissing != null) this.onVoiceDataMissing = onVoiceDataMissing

        users++
        if (engine != null) {
            // Engine pehle se ready hai par awaz missing thi — UI ko abhi bata do.
            if (needsVoiceData) main.post { this.onVoiceDataMissing?.invoke() }
            return
        }

        val app = context.applicationContext
        appContext = app
        triedGoogle = VoiceSetup.isGoogleTtsInstalled(app)

        engine = if (triedGoogle) {
            TextToSpeech(app, { status -> configure(status) }, VoiceSetup.GOOGLE_TTS_PACKAGE)
        } else {
            TextToSpeech(app) { status -> configure(status) }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.let { main.post(it) }
            return
        }

        val tts = engine
        if (tts == null || !ready) {
            // Engine abhi taiyaar nahi. Purani pending line ka callback yahin
            // chala do, warna wo hamesha ke liye kho jata hai aur hands-free
            // mode ka mic safety timeout tak band pada rehta hai.
            pending?.second?.let { previous -> main.post(previous) }
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

    // ------------------------------------------------------------- internals

    private fun configure(status: Int) {
        val tts = engine

        if (status != TextToSpeech.SUCCESS || tts == null) {
            // Google engine se init fail hua — default engine pe wapas.
            ready = false
            if (triedGoogle) {
                triedGoogle = false
                val app = appContext ?: return
                runCatching { tts?.shutdown() }
                engine = TextToSpeech(app) { retryStatus -> configure(retryStatus) }
            }
            return
        }

        val hindi = Locale("hi", "IN")

        when (tts.isLanguageAvailable(hindi)) {
            TextToSpeech.LANG_MISSING_DATA -> {
                flagMissingVoice()
                tts.setLanguage(Locale.US)
            }

            TextToSpeech.LANG_NOT_SUPPORTED -> tts.setLanguage(Locale.US)

            else -> tts.setLanguage(hindi)
        }

        applyBestVoice(tts)

        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Purana API", ReplaceWith("onError(utteranceId, errorCode)"))
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })

        ready = true
        pending?.let { (text, onDone) ->
            pending = null
            speak(text, onDone)
        }
    }

    /**
     * Engine ki saari voices me se sabse acchi Hindi voice.
     *
     * Ranking: pehle quality, phir network voice (wo sabse natural hoti hai).
     * Hindi na mile to Indian English, taki Hinglish theek sunai de.
     */
    private fun applyBestVoice(tts: TextToSpeech) {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            .filterNotNull()
            .filterNot {
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            }

        if (voices.isEmpty()) return

        val rank = compareBy<Voice>(
            { it.quality },
            { if (it.isNetworkConnectionRequired) 1 else 0 },
        )

        val best = voices.filter { it.locale.language == "hi" }.maxWithOrNull(rank)
            ?: voices
                .filter { it.locale.language == "en" && it.locale.country == "IN" }
                .maxWithOrNull(rank)

        if (best == null) {
            flagMissingVoice()
            return
        }

        runCatching { tts.voice = best }

        // NORMAL se neeche matlab wahi bheeni robot wali awaz.
        if (best.quality < Voice.QUALITY_HIGH) flagMissingVoice()
    }

    private fun flagMissingVoice() {
        needsVoiceData = true
        main.post { onVoiceDataMissing?.invoke() }
    }

    private fun finish(utteranceId: String?) {
        val callback = utteranceId?.let { callbacks.remove(it) } ?: return
        main.post(callback)
    }
}
