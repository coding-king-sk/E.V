package com.ev.android.feature.wakeword

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline wake word ka model — app ke andar nahi, chalte waqt download hota hai.
 *
 * Model ~4 MB ka hai. Use APK me daalne ka matlab hota har user ko wo weight
 * uthana, chahe wo offline wake word use kare ya na kare. Isliye jab user
 * Settings me maange, tabhi download hota hai, phone ke private folder me.
 */
object WakeWordModel {

    /**
     * Default model — English keyword spotting (zipformer, 3.3M).
     *
     * Ye URL Settings me badla ja sakta hai. Wajah: model release ke naam kabhi
     * kabhi badal jaate hain, aur us waqt user ko nayi APK ka intezaar na karna
     * pade — wo naya link khud paste kar sake.
     */
    const val DEFAULT_URL =
        "https://github.com/pkufool/keyword-spotting-models/releases/download/v0.1/" +
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"

    private const val DIR_NAME = "kws-model"
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** Model ki wo files jo KeywordSpotter ko chahiye. */
    data class Installed(
        val encoder: File,
        val decoder: File,
        val joiner: File,
        val tokens: File,
        val keywords: File,
    )

    fun dir(context: Context): File = File(context.applicationContext.filesDir, DIR_NAME)

    fun isInstalled(context: Context): Boolean = installed(context) != null

    /**
     * Extract ho chuke folder me se kaam ki files dhoondo.
     *
     * File ke naam hardcode jaan bujh ke nahi kiye — har model release me epoch
     * number alag hota hai (`encoder-epoch-12-avg-2-...onnx`). Isliye pattern se
     * dhoondte hain, taki doosra KWS model bhi bina code badle chal jaye.
     */
    fun installed(context: Context): Installed? {
        val root = dir(context)
        if (!root.isDirectory) return null

        val files = root.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return null

        fun onnx(part: String): File? = files
            .filter { it.name.endsWith(".onnx") && it.name.contains(part, ignoreCase = true) }
            // int8 wala chhota aur tez hota hai, isliye pehle wahi.
            .minByOrNull { if (it.name.contains("int8")) 0 else 1 }

        val encoder = onnx("encoder") ?: return null
        val decoder = onnx("decoder") ?: return null
        val joiner = onnx("joiner") ?: return null
        val tokens = files.firstOrNull { it.name.equals("tokens.txt", ignoreCase = true) }
            ?: return null
        val keywords = files.firstOrNull { it.name.equals("keywords.txt", ignoreCase = true) }
            ?: return null

        return Installed(encoder, decoder, joiner, tokens, keywords)
    }

    fun delete(context: Context) {
        dir(context).deleteRecursively()
    }

    /**
     * Model download karke extract karo.
     *
     * @param onProgress 0..100. Server size na bataye to -1 aata hai.
     * @return kaamyaabi pe [Installed], warna insaan ke padhne layak error.
     */
    suspend fun download(
        context: Context,
        url: String = DEFAULT_URL,
        onProgress: (Int) -> Unit = {},
    ): Result<Installed> = withContext(Dispatchers.IO) {
        val target = dir(context)

        runCatching {
            // Aadha-adhoora purana model pada ho to pehle safai.
            target.deleteRecursively()
            target.mkdirs()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "*/*")
            }

            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    error("Server ne $code bheja. Link galat ya purana ho sakta hai.")
                }

                val total = connection.contentLengthLong
                var read = 0L
                var lastReported = -1

                val counting = object : java.io.FilterInputStream(
                    BufferedInputStream(connection.inputStream)
                ) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) {
                            read += n
                            if (read > MAX_BYTES) error("File bahut badi hai, download roka.")
                            val percent = if (total > 0) ((read * 100) / total).toInt() else -1
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent)
                            }
                        }
                        return n
                    }
                }

                extract(counting, target)
            } finally {
                connection.disconnect()
            }

            installed(context) ?: error(
                "Download to ho gaya par isme KWS model ki files nahi mili " +
                    "(encoder/decoder/joiner .onnx, tokens.txt, keywords.txt). " +
                    "Shayad ye link kisi doosre tarah ke model ka hai."
            )
        }.onFailure {
            target.deleteRecursively()
        }
    }

    /**
     * tar.bz2 kholo aur saari files seedha [target] me daal do.
     *
     * Folder structure jaan bujh ke flatten kiya hai — humein files pattern se
     * dhoondni hi hain, to andar ka folder naam bekaar hai.
     */
    private fun extract(input: java.io.InputStream, target: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(input, true)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue

                // Zip Slip se bachao: sirf file ka naam lo, path nahi.
                val name = File(entry.name).name
                if (name.isBlank() || name.startsWith(".")) continue

                File(target, name).outputStream().use { out -> tar.copyTo(out) }
            }
        }
    }
}
