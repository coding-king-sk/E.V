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
 * **Wake word ka matching sabse nazuk hissa hai.** Pehle version me exact
 * string match tha aur wo fail ho raha tha, kyunki:
 *  - Hindi engine "Hey E.V" ko Devanagari me likh deta hai ("हे ईवी")
 *  - kabhi "hey ivy", "hey evie", "a v", "hey we" jaisa kuch sunta hai
 *
 * Isliye ab exact match ki jagah **token-based** matching hai: pehla shabd
 * greeting ho to chhod do, agla shabd "E.V" jaisa lage to jaag jao.
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
         * Kamre me koi bol hi nahi raha ho to bar bar 400ms me restart karna
         * battery kha jata hai. Har khali round pe delay itna badhta hai,
         * [MAX_IDLE_DELAY_MS] tak. Awaz sunte hi wapas 400ms pe aa jata hai.
         */
        const val IDLE_BACKOFF_STEP_MS = 400L
        const val MAX_IDLE_DELAY_MS = 3_000L

        /** "Hey", "ok", "suno" — inhe chhod ke aage dekhte hain. */
        val GREETINGS = setOf(
            "hey", "hay", "hai", "hi", "hello", "he", "ok", "okay", "yo", "arey", "are",
            "suno", "sun",
            "हे", "हेय", "हाय", "हैलो", "ओके", "अरे", "सुनो",
        )

        /** "E.V" ko engine jitne tarah se sun sakta hai. */
        val NAMES = setOf(
            "ev", "e", "evi", "evie", "evy", "eevee", "eve", "ivy", "ivi", "avi", "a",
            "ईवी", "ईव", "ई", "इवी", "इव", "एव", "एवी", "आईवी",
        )

        /**
         * Ye naam itne chhote hain ki roz ki baat me bhi aa jate hain ("a",
         * "e", "ई"). Inhe akela wake word maan lene se TV ki awaz tak E.V ko
         * jaga deti thi, isliye inke saath "v" wala doosra hissa zaroori hai.
         */
        val AMBIGUOUS_NAMES = setOf("e", "a", "ई")

        /** "e v" alag alag suna gaya ho to doosra token ye hoga. */
        val NAME_TAILS = setOf("v", "vee", "vi", "bee", "b", "वी", "व")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private var running = false
    private var paused = false
    private var awaitingCommand = false
    private var awaitingSince = 0L

    /** Lagataar kitni baar kuch sunai nahi diya — backoff ke liye. */
    private var idleRounds = 0

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFatal("Is phone me speech recognition nahi hai")
            return
        }
        running = true
        paused = false
        idleRounds = 0
        listen()
    }

    fun stop() {
        running = false
        awaitingCommand = false
        handler.removeCallbacksAndMessages(null)
        release()
    }

    /** Jab tak E.V khud bol raha hai, mic band — warna khud ko sun leta hai. */
    fun pause() {
        paused = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
    }

    fun resume() {
        if (!running) return
        paused = false
        idleRounds = 0
        restart(RESTART_DELAY_MS)
    }

    /**
     * Wake word bahar se aaya hai (offline KWS), yahan se nahi.
     *
     * Aisi soorat me naam dobara sunne ki zaroorat nahi — jo bhi agli baat
     * sunai degi, wahi command maani jayegi.
     */
    fun listenForCommand() {
        awaitingCommand = true
        awaitingSince = System.currentTimeMillis()
        if (running) resume() else start()
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
            // Hinglish ke liye dono — engine jo behtar samjhe.
            putExtra(
                RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                arrayListOf("hi-IN", "en-IN"),
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
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

    /** Jitni der se kuch sunai nahi diya, utna sust restart. */
    private fun idleRestart() {
        idleRounds++
        val delay = (RESTART_DELAY_MS + idleRounds * IDLE_BACKOFF_STEP_MS)
            .coerceAtMost(MAX_IDLE_DELAY_MS)
        restart(delay)
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
            idleRestart()
            return
        }

        // Kuch to sunai diya — backoff reset.
        idleRounds = 0

        val now = System.currentTimeMillis()

        // Wake word pehle ho chuka hai — ab jo bhi bola, wahi command hai.
        if (awaitingCommand && now - awaitingSince < COMMAND_WINDOW_MS) {
            awaitingCommand = false
            onCommand(heard.first())
            return
        }
        awaitingCommand = false

        // Engine kai guesses deta hai; kisi ek me bhi wake word mil jaye to kaafi.
        for (text in heard) {
            val rest = afterWakeWord(text) ?: continue

            if (rest.isNotBlank()) {
                // "Hey E.V, torch on karo" — sab ek hi saans me.
                onCommand(rest)
            } else {
                // Sirf "Hey E.V" — ab agli baat ka intezaar.
                awaitingCommand = true
                awaitingSince = now
                onWake()
            }
            return
        }

        restart(RESTART_DELAY_MS)
    }

    /**
     * Wake word mila to uske baad ka text, warna null.
     * Khali string ka matlab: sirf naam pukara gaya, command abhi baaki hai.
     */
    private fun afterWakeWord(raw: String): String? {
        val text = raw.lowercase()
            .replace(Regex("[?!.,;:\"'।]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isEmpty()) return null

        val tokens = text.split(" ")
        var i = 0

        if (tokens[i] in GREETINGS) {
            i++
            if (i >= tokens.size) return null
        }

        if (tokens[i] !in NAMES) return null

        // "a", "e", "ई" jaise chhote naam sirf tab wake karte hain jab unke
        // baad "v" wala hissa bhi suna ho. Warna har doosri baat wake ban jati.
        val ambiguous = tokens[i] in AMBIGUOUS_NAMES
        val tail = tokens.getOrNull(i + 1)
        if (ambiguous && (tail == null || tail !in NAME_TAILS)) return null

        i++

        // "e" + "v" alag alag aaye ho to doosra hissa bhi nigal lo.
        if (i < tokens.size && tokens[i] in NAME_TAILS) i++

        return tokens.drop(i).joinToString(" ").trim()
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
                // Engine atak gaya — naya banana padta hai.
                release()
                restart(BUSY_RETRY_MS)
            }

            // Kuch bola hi nahi gaya — sabse aam case. Yahan sust restart se
            // battery bachti hai.
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> idleRestart()

            else -> restart(RESTART_DELAY_MS)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() {
        // Awaz aa rahi hai — backoff hata do, ab tez response chahiye.
        idleRounds = 0
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
