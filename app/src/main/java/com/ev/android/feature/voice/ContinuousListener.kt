package com.ev.android.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Hands-free sunne wala loop.
 *
 * Android ka [SpeechRecognizer] ek baar sun ke ruk jata hai, isliye har result
 * ya error ke baad hum use dobara start karte hain. Yahi "hamesha sun raha hai"
 * ka ehsaas deta hai.
 *
 * Do baatein jaanbujh ke aise hain:
 *
 * - **Wake word zaroori hai.** Bina iske har baat command ban jati. "Hey E.V"
 *   sunne ke baad hi aage ka text command mana jata hai.
 * - **Restart pe delay hai.** Bina delay ke, jab mic dusri app ke paas ho, ye
 *   loop CPU aur battery kha jata hai.
 *
 * Note: recognizer ke saare methods main thread pe hi chalte hain.
 */
class ContinuousListener(
    private val context: Context,
    private val onWake: () -> Unit,
    private val onCommand: (String) -> Unit,
    private val onFatal: (String) -> Unit,
) : RecognitionListener {

    private companion object {
        /** Wake word ke baad itni der tak agli baat command maani jayegi. */
        const val COMMAND_WINDOW_MS = 12_000L

        const val RESTART_DELAY_MS = 400L
        const val BUSY_RETRY_MS = 1_200L

        /**
         * Speech engine "E.V" ko kai tarah se sunta hai, isliye itne variants.
         * Sabse lambe pehle match karte hain taki "hey ev" "ev" se pehle lage.
         */
        val WAKE_WORDS = listOf(
            "hey ev", "hey e v", "hey evi", "hey evie", "hey ivy", "hey evy",
            "hello ev", "hi ev", "hai ev", "ok ev", "okay ev", "he ev", "hey app",
            "ev", "e v", "evie", "ivy",
        ).sortedByDescending { it.length }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private var running = false
    private var paused = false
    private var awaitingCommand = false
    private var awaitingSince = 0L

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFatal("Is phone me speech recognition nahi hai")
            return
        }
        running = true
        paused = false
        listen()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        release()
    }

    /** Jab tak E.V khud bol raha hai, mic band \u2014 warna khud ko sun leta hai. */
    fun pause() {
        paused = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
    }

    fun resume() {
        if (!running) return
        paused = false
        restart(RESTART_DELAY_MS)
    }

    // ------------------------------------------------------------- internals

    private fun listen() {
        if (!running || paused) return

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@ContinuousListener)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        runCatching { recognizer?.startListening(intent) }
            .onFailure { restart(BUSY_RETRY_MS) }
    }

    private fun restart(delayMs: Long) {
        if (!running || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun release() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun handleResults(texts: List<String>) {
        val heard = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (heard.isEmpty()) {
            restart(RESTART_DELAY_MS)
            return
        }

        val now = System.currentTimeMillis()

        // Wake word pehle ho chuka hai \u2014 ab jo bhi bola, wahi command hai.
        if (awaitingCommand && now - awaitingSince < COMMAND_WINDOW_MS) {
            awaitingCommand = false
            onCommand(heard.first())
            return
        }
        awaitingCommand = false

        for (text in heard) {
            val rest = afterWakeWord(text) ?: continue

            if (rest.isNotBlank()) {
                // "Hey E.V, torch on karo" \u2014 sab ek hi saans me.
                onCommand(rest)
            } else {
                // Sirf "Hey E.V" \u2014 ab agli baat ka intezaar.
                awaitingCommand = true
                awaitingSince = now
                onWake()
            }
            return
        }

        restart(RESTART_DELAY_MS)
    }

    /** Wake word mila to uske baad ka text, warna null. */
    private fun afterWakeWord(raw: String): String? {
        val text = raw.lowercase()
            .replace(Regex("[?!.,;:\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        for (wake in WAKE_WORDS) {
            if (text == wake) return ""
            if (text.startsWith(wake + " ")) {
                return text.substring(wake.length).trim()
            }
        }
        return null
    }

    // ------------------------------------------------------ RecognitionListener

    override fun onResults(results: Bundle?) {
        val texts = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        handleResults(texts)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                running = false
                release()
                onFatal("Mic ki permission nahi hai")
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            -> {
                // Engine atak gaya \u2014 naya banana padta hai.
                release()
                restart(BUSY_RETRY_MS)
            }

            else -> restart(RESTART_DELAY_MS)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
