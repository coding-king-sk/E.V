package com.ev.android.feature.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.ev.android.MainActivity
import kotlin.math.abs
import kotlin.math.min

/**
 * Bubble ko bahar se chalane wale chhote helpers.
 *
 * Overlay ki permission aam permission jaisi nahi hoti - runtime dialog se
 * nahi milti, user ko Settings ki ek alag screen pe jaana padta hai. Isliye
 * yahan uska intent bhi rakha hai.
 */
object Bubble {

    /** Kya "Display over other apps" mil chuki hai? */
    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Wo screen jahan se user ye permission de sakta hai. */
    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(context: Context) {
        if (!canShow(context)) return

        val intent = Intent(context, BubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, BubbleService::class.java))
    }
}

/**
 * Har app ke upar tairta hua E.V ka orb.
 *
 * Ye foreground service hai kyunki Android background me overlay dikhane wali
 * service ko chup-chaap maar deta hai. Notification ki importance sabse kam
 * rakhi hai taaki wo chup rahe.
 *
 * Tap  -> E.V khul jata hai
 * Drag -> bubble jahan chhodo wahin ruk jata hai (jagah yaad rehti hai)
 * Lamba dabao -> bubble band
 */
class BubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var orb: OrbView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        showOrb()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        orb?.let { view ->
            // Service maut ke waqt view hataana zaroori hai, warna orb screen pe
            // atka reh jata hai aur phone restart tak nahi jata.
            runCatching { windowManager?.removeView(view) }
        }
        orb = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ overlay

    private fun showOrb() {
        if (!Bubble.canShow(this)) {
            stopSelf()
            return
        }

        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val sizePx = (SIZE_DP * resources.displayMetrics.density).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val saved = prefs()
        val layout = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            // NOT_FOCUSABLE zaroori hai - warna neeche wali app ka keyboard
            // aur buttons kaam karna band kar dete hain.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved.getInt(KEY_X, 24)
            y = saved.getInt(KEY_Y, 320)
        }
        params = layout

        val view = OrbView(this)
        orb = view
        attachTouch(view, layout, manager)

        runCatching { manager.addView(view, layout) }
            .onFailure { stopSelf() }
    }

    /**
     * Ungli ka kaam samajhna.
     *
     * Thoda sa hilna bhi drag nahi hota - haath kabhi bilkul sthir nahi hota.
     * Isliye ek chhoti si limit rakhi hai; usse kam hile aur jaldi chhoda to
     * wo tap hai.
     */
    private fun attachTouch(
        view: OrbView,
        layout: WindowManager.LayoutParams,
        manager: WindowManager,
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downAt = 0L
        var moved = false

        val slop = 12f * resources.displayMetrics.density

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downAt = System.currentTimeMillis()
                    moved = false
                    view.setPressedLook(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true

                    if (moved) {
                        layout.x = startX + dx.toInt()
                        layout.y = startY + dy.toInt()
                        runCatching { manager.updateViewLayout(view, layout) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.setPressedLook(false)
                    val heldMs = System.currentTimeMillis() - downAt

                    when {
                        moved -> savePosition(layout.x, layout.y)
                        heldMs >= LONG_PRESS_MS -> stopSelf()
                        else -> openApp()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.setPressedLook(false)
                    true
                }

                else -> false
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    private fun savePosition(x: Int, y: Int) {
        prefs().edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------- notification

    private fun startInForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "E.V bubble",
                // IMPORTANCE_MIN = na awaaz, na status bar me shor.
                NotificationManager.IMPORTANCE_MIN,
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("E.V bubble chalu hai")
            .setContentText("Bubble pe tap karo, lamba dabao to band")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    /**
     * Chhota orb.
     *
     * App wala bada orb Compose se banta hai; use overlay me daalne ke liye
     * poora lifecycle setup chahiye hota hai. Yahan sirf ek gol chamakta hua
     * daayra chahiye, isliye seedha Canvas pe bana diya - halka aur bharosemand.
     */
    private class OrbView(context: Context) : View(context) {

        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GREEN
        }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN }

        private var pressed = false

        fun setPressedLook(value: Boolean) {
            pressed = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) / 2f

            glow.shader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(GREEN_SOFT, GREEN_FADE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, glow)

            val body = radius * if (pressed) 0.56f else 0.62f
            canvas.drawCircle(cx, cy, body, core)

            ring.strokeWidth = radius * 0.06f
            canvas.drawCircle(cx, cy, body, ring)

            // Beech me ek hi bindu - app wale orb ki tarah.
            canvas.drawCircle(cx, cy, radius * 0.1f, dot)
        }

        private companion object {
            const val GREEN = 0xFF00E676.toInt()
            const val GREEN_SOFT = 0x5500E676
            const val GREEN_FADE = 0x2200E676
        }
    }

    private companion object {
        const val SIZE_DP = 72
        const val LONG_PRESS_MS = 600L
        const val NOTIFICATION_ID = 4201
        const val CHANNEL_ID = "ev_bubble"
        const val PREFS = "ev_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
