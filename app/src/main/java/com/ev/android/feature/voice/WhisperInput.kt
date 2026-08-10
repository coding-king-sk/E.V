package com.ev.android.feature.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ev.android.feature.ai.GroqAudio
import com.ev.android.feature.settings.EvSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Mic se record karo, phir Groq Whisper se text banwao.
 *
 * Recording khud band ho jaati hai jab user chup ho jaye — warna har command
 * ke baad "ab bas" wala button dabana padta, jo hands-free ke ulta hai.
 */
object WhisperInput {

    private const val SAMPLE_RATE = 16_000
    private const val MAX_MS = 12_000
    private const val MIN_MS = 700

    /** Itni der chup rehne pe recording band. */
    private const val SILENCE_MS = 1_300

    /** Isse neeche wali awaaz ko "chup" maana jata hai (0–32767 scale). */
    private const val SILENCE_LEVEL = 800

    suspend fun listen(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val key = EvSettings.apiKey(context)
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Pehle Groq key daalo"))
        }

        val file = File(context.cacheDir, "ev-stt.wav")

        val recorded = runCatching { record(file) }
        if (recorded.isFailure) {
            file.delete()
            return@withContext Result.failure(
                IllegalStateException(
                    "Mic nahi mila \u2014 ho sakta hai hands-free abhi mic use kar raha ho"
                )
            )
        }

        val result = GroqAudio.transcribe(key, file)
        file.delete()
        result
    }

    /** PCM 16-bit mono record karke WAV file me likh do. */
    private fun record(file: File) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw IllegalStateException("Mic support nahi karta")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Mic init nahi hua")
        }

        val pcm = ByteArrayOutputStream()
        val chunk = ShortArray(1600) // 100 ms
        var elapsedMs = 0
        var quietMs = 0
        var heardSomething = false

        try {
            recorder.startRecording()

            while (elapsedMs < MAX_MS) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) break

                var sum = 0L
                for (i in 0 until read) sum += abs(chunk[i].toInt())
                val level = (sum / read).toInt()

                val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until read) bytes.putShort(chunk[i])
                pcm.write(bytes.array())

                val chunkMs = read * 1000 / SAMPLE_RATE
                elapsedMs += chunkMs

                if (level > SILENCE_LEVEL) {
                    heardSomething = true
                    quietMs = 0
                } else {
                    quietMs += chunkMs
                }

                // Bolna shuru hone ke baad hi chuppi maayne rakhti hai, warna
                // mic on karte hi recording khatam ho jaati.
                if (heardSomething && quietMs >= SILENCE_MS && elapsedMs >= MIN_MS) break
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        writeWav(file, pcm.toByteArray())
    }

    /** 44-byte WAV header — Whisper ko raw PCM samajh nahi aata. */
    private fun writeWav(file: File, pcm: ByteArray) {
        val totalDataLen = 36 + pcm.size
        val byteRate = SAMPLE_RATE * 2

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)  // PCM
        header.putShort(1)  // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2)  // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray())
        header.putInt(pcm.size)

        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(pcm)
        }
    }
}
