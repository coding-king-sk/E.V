package com.ev.android.feature.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.ev.android.feature.tts.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hands-free mode.
 *
 * Ye foreground service hai (notification ke saath) \u2014 Android background me
 * mic use karne hi nahi deta, aur notification se user ko hamesha pata rehta
 * hai ki mic on hai. Chhupa ke sunne wala kaam E.V nahi karta.
 *
 * Flow: sunna -> "Hey E.V" -> ya to command chalta hai, ya sawaal Groq ko
 * jaata hai -> jawab bol ke sunaya jaata hai -> phir se sunna.
 */
class EvListeningService : Service() {

    companion object {
        private const val CHANNEL_ID = "ev_listening"
        private const val NOTIFICATION_ID = 42

        const val ACTION_STOP = "com.ev.android.action.STOP_LISTENING"

        /** TTS callback kisi wajah se na aaye to bhi mic wapas chalu ho jaye. */
        private const val SAFETY_RESUME_MS = 20_000L

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

    private var listener: ContinuousListener? = null
    private var apps: List<InstalledApp> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createChannel()
        startForegroundCompat()
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

        listener?.start()

        // Chhoti si awaz \u2014 isse turant pata chal jata hai ki service zinda hai.
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
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        safety.removeCallbacksAndMessages(null)
        listener?.stop()
        listener = null
        Speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- commands

    /**
     * Teen seedhiyan: offline parser -> AI se command -> AI se seedha jawab.
     * Har seedhi tabhi chalti hai jab pichli haar jaye.
     */
    private fun runCommand(text: String) {
        listener?.pause()

        scope.launch {
            var command: EvCommand = CommandParser.parse(text, apps)

            if (command is EvCommand.Unknown) {
                val outcome = AiCommandResolver.resolve(this@EvListeningService, text, apps)
                if (outcome is AiOutcome.Resolved) command = outcome.command
            }

            if (command is EvCommand.Unknown) {
                // Command nahi hai \u2014 shayad sawaal hai. Groq se jawab le lo.
                val reply = Conversation.answer(this@EvListeningService, text)
                say(
                    reply ?: "Samajh nahi aaya. Settings me Groq key daal do to " +
                        "main sawaalon ke jawab bhi de sakta hoon."
                )
                return@launch
            }

            val result = CommandExecutor.execute(this@EvListeningService, command)
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
        listener?.resume()
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

    private fun startForegroundCompat() {
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

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}
