package com.ev.android.feature.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.ev.android.feature.settings.EvSettings
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Offline wake word — poori tarah phone ke andar, internet ke bina.
 *
 * Google wale recognizer se farq: ye har waqt mic se sun sakta hai bina baar
 * baar session restart kiye, aur audio kahin bhejta nahi. Badle me ise ek 4 MB
 * ka model chahiye ([WakeWordModel]) aur native library chahiye
 * (`app/src/main/jniLibs/`).
 *
 * Dono me se kuch bhi na ho to ye chupchaap `false` lauta deta hai — app tab
 * Google recognizer pe hi chalti rehti hai.
 */
class SherpaWakeWord(
    private val context: Context,
    private val onWake: () -> Unit,
    private val onError: (String) -> Unit = {},
) {

    @Volatile
    private var running = false

    private var worker: Thread? = null

    /** Chal raha hai ya nahi. */
    fun isRunning(): Boolean = running

    /**
     * Sunna shuru karo.
     *
     * @return false agar permission, library ya model me se kuch bhi missing ho.
     *         Aisi soorat me [onError] pe wajah bhi aa jati hai.
     */
    fun start(): Boolean {
        if (running) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError("Mic permission nahi hai")
            return false
        }

        if (!isLibraryAvailable()) {
            onError("Sherpa native library missing hai")
            return false
        }

        val model = WakeWordModel.installed(context)
        if (model == null) {
            onError("Wake word model download nahi hua")
            return false
        }

        running = true
        worker = Thread { loop(model) }.also {
            it.isDaemon = true
            it.start()
        }
        return true
    }

    fun stop() {
        running = false
        worker?.join(1_500)
        worker = null
    }

    private fun loop(model: WakeWordModel.Installed) {
        var spotter: KeywordSpotter? = null
        var stream: OnlineStream? = null
        var recorder: AudioRecord? = null

        try {
            spotter = KeywordSpotter(
                assetManager = null,
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = model.encoder.absolutePath,
                            decoder = model.decoder.absolutePath,
                            joiner = model.joiner.absolutePath,
                        ),
                        tokens = model.tokens.absolutePath,
                        modelType = "zipformer2",
                        numThreads = 1,
                        provider = "cpu",
                    ),
                    keywordsFile = model.keywords.absolutePath,
                    keywordsScore = 1.5f,
                    keywordsThreshold = EvSettings.wakeWordThreshold(context),
                    numTrailingBlanks = 2,
                ),
            )

            // Custom keyword khaali ho to model ki apni keywords.txt chalti hai.
            stream = spotter.createStream(EvSettings.wakeWordKeywords(context))

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                onError("Mic buffer nahi mila")
                return
            }

            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK * 4),
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                onError("Mic shuru nahi ho paya")
                return
            }

            recorder.startRecording()

            val shorts = ShortArray(CHUNK)
            val floats = FloatArray(CHUNK)

            while (running) {
                val count = recorder.read(shorts, 0, shorts.size)
                if (count <= 0) continue

                for (i in 0 until count) {
                    floats[i] = shorts[i] / 32768.0f
                }

                stream.acceptWaveform(floats.copyOf(count), SAMPLE_RATE)

                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                }

                val keyword = spotter.getResult(stream).keyword
                if (keyword.isNotBlank()) {
                    // Reset zaroori hai, warna wahi keyword baar baar milta rahega.
                    spotter.reset(stream)
                    onWake()
                }
            }
        } catch (t: Throwable) {
            // UnsatisfiedLinkError bhi yahin pakda jata hai — .so missing hone pe.
            onError(t.message ?: t.javaClass.simpleName)
        } finally {
            running = false
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { stream?.release() }
            runCatching { spotter?.release() }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK = 1600 // 100 ms

        @Volatile
        private var libraryChecked = false

        @Volatile
        private var libraryOk = false

        /**
         * Native library phone me hai ya nahi.
         *
         * `jniLibs` me `.so` na daali ho, ya phone ka architecture alag ho, to
         * ye false rahega — aur app bina crash hue Google recognizer pe chalti
         * rahegi.
         */
        fun isLibraryAvailable(): Boolean {
            if (libraryChecked) return libraryOk
            libraryChecked = true
            libraryOk = runCatching { System.loadLibrary("sherpa-onnx-jni") }.isSuccess
            return libraryOk
        }
    }
}
