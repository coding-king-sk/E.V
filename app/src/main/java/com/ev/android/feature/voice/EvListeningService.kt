package com.ev.android.feature.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ev.android.feature.ai.AiCommandResolver
import com.ev.android.feature.ai.AiOutcome
import com.ev.android.feature.ai.Conversation
import com.ev.android.feature.apps.InstalledApp
import com.ev.android.feature.apps.InstalledAppsRepository
import com.ev.android.feature.command.CommandExecutor
import com.ev.android.feature.command.CommandParser
import com.ev.android.feature.command.EvCommand
import com.ev.android.feature.history.CommandHistory
import com.ev.android.feature.settings.EvSettings
import com.ev.android.feature.tts.Speaker
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hands-free mode.
 *
 * Ye foreground service hai (notification ke saath) — Android background me
 * mic use karne hi nahi deta, aur notification se user ko hamesha pata rehta
 * hai ki mic on hai. Chhupa ke sunne wala kaam E.V nahi karta.
 *
 * Sunne ke do tareeke hain:
 *  - **Google recognizer** (default): baar baar session restart karke sunta hai
 *  - **Offline KWS** (Settings me on karne pe): sherpa-onnx phone ke andar hi
 *    wake word pakadta hai, audio kahin nahi jata
 *
 * Dono ek saath mic nahi le sakte, isliye offline mode me mic barabar haath
 * badalta hai: KWS sunta hai → wake → mic recognizer ko → command → wapas KWS.
 */
class EvListeningService : Service() {

    companion object {
        private const val CHANNEL_ID = "ev_listening"
        private const val NOTIFICATION_ID = 42

        const val ACTION_STOP = "com.ev.android.action.STOP_LISTENING"

        /** TTS callback kisi wajah se na aaye to bhi mic wapas chalu ho jaye. */
        private const val SAFETY_RESUME_MS = 20_000L

        /** Wake ke baad itni der command na aaye to wapas KWS pe chale jao. */
        private const val COMMAND_WINDOW_MS = 14_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, EvListeningService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EvListeningService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val safety = Handler(Looper.getMainLooper())
    private val main = Handler(Looper.getMainLooper())
    private val wakeTimeout = Handler(Looper.getMainLooper())

    private var listener: ContinuousListener? = null
    private var apps: List<InstalledApp> = emptyList()

    private var wakeWord: SherpaWakeWord? = null
    private var offlineWake = false
    private var awaitingCommandAfterWake = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createChannel()

        // Mic permission ke bina Android 14+ pe "microphone" type ka foreground
        // service start karna SecurityException deta hai. Ye tab hota hai jab
        // user permission hata de aur system START_STICKY ki wajah se service
        // dobara chala de — pehle yahan app crash ho jati thi.
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        startForegroundCompat(micGranted)

        if (!micGranted) {
            isRunning = false
            stopSelf()
            return
        }

        CommandHistory.load(this)
        Speaker.init(this)

        listener = ContinuousListener(
            context = this,
            onWake = { say("Haan, boliye") },
            onCommand = { text -> runCommand(text) },
            onFatal = { reason ->
                Speaker.speak(reason)
                stopSelf()
            },
        )

        // Teeno cheezein honi chahiye: user ne on kiya ho, native library ho,
        // aur model download ho. Kisi ek ki bhi kami me Google recognizer.
        offlineWake = EvSettings.offlineWakeWord(this) &&
            SherpaWakeWord.isLibraryAvailable() &&
            WakeWordModel.isInstalled(this)

        if (offlineWake) startWakeWord() else listener?.start()

        // Chhoti si awaz — isse turant pata chal jata hai ki service zinda hai.
        say("E.V taiyaar hai")

        // App list background me load hoti rehti hai; tab tak bhi commands chalte hain.
        scope.launch {
            apps = InstalledAppsRepository.load(this@EvListeningService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Mic permission na hone ki wajah se listener bana hi nahi — aise me
        // system ko baar baar service restart karne ka koi fayda nahi.
        if (listener == null) return START_NOT_STICKY

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        safety.removeCallbacksAndMessages(null)
        main.removeCallbacksAndMessages(null)
        wakeTimeout.removeCallbacksAndMessages(null)

        val hadListener = listener != null
        listener?.stop()
        listener = null

        runCatching { wakeWord?.stop() }
        wakeWord = null

        if (hadListener) Speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    // ----------------------------------------------------- offline wake word

    private fun startWakeWord() {
        if (!offlineWake) return

        // Recognizer ko mic chhodna padega — do cheezein ek saath mic nahi
        // le sakti.
        listener?.stop()

        val spotter = wakeWord ?: SherpaWakeWord(
            context = this,
            onWake = { main.post { onOfflineWake() } },
            onError = { reason -> main.post { fallbackToRecognizer(reason) } },
        ).also { wakeWord = it }

        if (!spotter.start()) {
            fallbackToRecognizer("Offline wake word shuru nahi hua")
        }
    }

    private fun onOfflineWake() {
        if (!offlineWake) return

        // Mic ab recognizer ko chahiye. stop() worker thread ka intezaar karta
        // hai, par wo ek chunk (100ms) se zyada nahi lagta.
        runCatching { wakeWord?.stop() }

        awaitingCommandAfterWake = true
        say("Haan, boliye")
    }

    /**
     * Offline KWS kaam nahi kar raha — chupchaap Google recognizer pe wapas.
     *
     * User ko error sunane ka koi fayda nahi; use bas ye chahiye ki E.V sunta
     * rahe. Wajah Settings me dikh hi jayegi.
     */
    private fun fallbackToRecognizer(reason: String) {
        if (!offlineWake) return

        offlineWake = false
        awaitingCommandAfterWake = false
        wakeTimeout.removeCallbacksAndMessages(null)

        runCatching { wakeWord?.stop() }
        wakeWord = null

        listener?.start()
    }

    private fun backToWakeWord() {
        wakeTimeout.removeCallbacksAndMessages(null)
        awaitingCommandAfterWake = false
        startWakeWord()
    }

    // ------------------------------------------------------------- commands

    /**
     * Teen seedhiyan: offline parser -> AI se command -> AI se seedha jawab.
     * Har seedhi tabhi chalti hai jab pichli haar jaye.
     */
    private fun runCommand(text: String) {
        awaitingCommandAfterWake = false
        wakeTimeout.removeCallbacksAndMessages(null)
        listener?.pause()

        scope.launch {
            var command: EvCommand = CommandParser.parse(text, apps)

            if (command is EvCommand.Unknown) {
                val outcome = AiCommandResolver.resolve(this@EvListeningService, text, apps)
                if (outcome is AiOutcome.Resolved) command = outcome.command
            }

            if (command is EvCommand.Unknown) {
                // Command nahi hai — shayad sawaal hai. Groq se jawab le lo.
                val reply = Conversation.answer(this@EvListeningService, text)
                val message = reply
                    ?: "Samajh nahi aaya. Settings me Groq key daal do to " +
                    "main sawaalon ke jawab bhi de sakta hoon."

                CommandHistory.add(
                    context = this@EvListeningService,
                    spoken = text,
                    understood = if (reply != null) "Sawaal (AI)" else "Samajh nahi aaya",
                    reply = message,
                )

                say(message)
                return@launch
            }

            val result = CommandExecutor.execute(this@EvListeningService, command)

            CommandHistory.add(
                context = this@EvListeningService,
                spoken = text,
                understood = CommandHistory.describe(command),
                reply = result.message,
            )

            say(result.message)
        }
    }

    /** Bolte waqt mic band, bolne ke baad wapas chalu. */
    private fun say(text: String) {
        listener?.pause()
        safety.removeCallbacksAndMessages(null)

        Speaker.speak(text) { resumeListening() }
        safety.postDelayed({ resumeListening() }, SAFETY_RESUME_MS)
    }

    private fun resumeListening() {
        safety.removeCallbacksAndMessages(null)

        if (!offlineWake) {
            listener?.resume()
            return
        }

        if (!awaitingCommandAfterWake) {
            backToWakeWord()
            return
        }

        // Wake ho chuka hai — ab naam dobara bolne ki zaroorat nahi, jo bhi
        // bolo wahi command hai.
        listener?.listenForCommand()

        wakeTimeout.removeCallbacksAndMessages(null)
        wakeTimeout.postDelayed({ backToWakeWord() }, COMMAND_WINDOW_MS)
    }

    // -------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "E.V hands-free",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Jab E.V \"Hey E.V\" ka intezaar kar raha ho"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * @param withMicType sirf tab true jab RECORD_AUDIO mila ho. Bina permission
     *   ke microphone type dena Android 14+ pe crash karata hai.
     */
    private fun startForegroundCompat(withMicType: Boolean) {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, EvListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("E.V sun raha hai")
            .setContentText("\"Hey E.V\" bolo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Band karo", stopIntent)
            .build()

        val type = if (withMicType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }
    }
}
