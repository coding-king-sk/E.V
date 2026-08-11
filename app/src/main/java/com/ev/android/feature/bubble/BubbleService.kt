package com.ev.android.feature.bubble

import android.animation.ValueAnimator
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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ev.android.MainActivity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bubble ko bahar se chalane wale chhote helpers.
 *
 * Overlay ki permission aam permission jaisi nahi hoti - runtime dialog se
 * nahi milti, user ko Settings ki ek alag screen pe jaana padta hai. Isliye
 * yahan uska intent bhi rakha hai.
 */
object Bubble {

    /** Bubble se app khuli aur seedha sunna shuru karna hai. */
    const val EXTRA_LISTEN = "ev_bubble_listen"

    /** Bubble se app khuli aur ye command chalani hai. */
    const val EXTRA_COMMAND = "ev_bubble_command"

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
 * Single tap  -> neeche chhota sa action bar khulta hai (peeche wali app
 *                dikhti rehti hai)
 * Double tap  -> seedha mic - bolo aur kaam ho jaye
 * Drag        -> chhodte hi kinare pe chipak jata hai, taaki beech ka hissa
 *                khaali rahe
 * Neeche X pe chhodo -> bubble abhi ke liye band
 * Lamba dabao -> bubble band
 * Kuch der haath na lage -> halka transparent, taaki peeche wali screen dikhe
 */
class BubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var orb: OrbView? = null
    private var params: WindowManager.LayoutParams? = null

    private var trash: View? = null
    private var panel: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable {
        orb?.animate()?.alpha(IDLE_ALPHA)?.setDuration(400)?.start()
    }
    private var pendingTap: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        showOrb()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hidePanel()
        hideTrash()
        orb?.let { view ->
            // Service maut ke waqt view hataana zaroori hai, warna orb screen pe
            // atka reh jata hai aur phone restart tak nahi jata.
            runCatching { windowManager?.removeView(view) }
        }
        orb = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ overlay

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showOrb() {
        if (!Bubble.canShow(this)) {
            stopSelf()
            return
        }

        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val sizePx = dp(SIZE_DP)

        val saved = prefs()
        val layout = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            // NOT_FOCUSABLE zaroori hai - warna neeche wali app ka keyboard
            // aur buttons kaam karna band kar dete hain.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved.getInt(KEY_X, 0)
            y = saved.getInt(KEY_Y, 320)
        }
        params = layout

        val view = OrbView(this)
        orb = view
        attachTouch(view, layout, manager)

        runCatching { manager.addView(view, layout) }
            .onFailure { stopSelf() }

        scheduleFade()
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
        var lastTapAt = 0L

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
                    wakeUp()
                    view.setPressedLook(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true

                    if (moved) {
                        if (trash == null) showTrash()
                        layout.x = startX + dx.toInt()
                        layout.y = startY + dy.toInt()
                        runCatching { manager.updateViewLayout(view, layout) }
                        highlightTrash(inTrashZone(event.rawX, event.rawY))
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.setPressedLook(false)
                    val heldMs = System.currentTimeMillis() - downAt

                    when {
                        moved && inTrashZone(event.rawX, event.rawY) -> {
                            hideTrash()
                            stopSelf()
                        }

                        moved -> {
                            hideTrash()
                            snapToEdge(view, layout, manager)
                            scheduleFade()
                        }

                        heldMs >= LONG_PRESS_MS -> stopSelf()

                        else -> {
                            val now = System.currentTimeMillis()
                            if (now - lastTapAt <= DOUBLE_TAP_MS) {
                                // Doosra tap - pehle wala kaam raddi me.
                                pendingTap?.let { handler.removeCallbacks(it) }
                                pendingTap = null
                                lastTapAt = 0L
                                hidePanel()
                                launchMain(listen = true)
                            } else {
                                lastTapAt = now
                                val task = Runnable {
                                    pendingTap = null
                                    togglePanel()
                                }
                                pendingTap = task
                                handler.postDelayed(task, DOUBLE_TAP_MS)
                            }
                            scheduleFade()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.setPressedLook(false)
                    hideTrash()
                    scheduleFade()
                    true
                }

                else -> false
            }
        }
    }

    // -------------------------------------------------------- snap to edge

    /**
     * Chhodte hi bubble kinare pe chala jata hai.
     *
     * Beech me latka hua bubble sabse zyada disturb karta hai - jo padh rahe
     * ho wahi dhak leta hai. Jis taraf zyada paas hai, usi taraf sarak jata
     * hai, halke se.
     */
    private fun snapToEdge(
        view: View,
        layout: WindowManager.LayoutParams,
        manager: WindowManager,
    ) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val sizePx = dp(SIZE_DP)
        val center = layout.x + sizePx / 2
        val target = if (center < screenWidth / 2) 0 else screenWidth - sizePx

        // Upar-neeche bhi thoda sambhal lete hain, warna bubble status bar ke
        // peeche ya navigation bar ke neeche gum ho jata hai.
        val lowest = (screenHeight - sizePx - dp(24)).coerceAtLeast(dp(24))
        layout.y = layout.y.coerceIn(dp(24), lowest)

        ValueAnimator.ofInt(layout.x, target).apply {
            duration = SNAP_MS
            addUpdateListener { anim ->
                layout.x = anim.animatedValue as Int
                runCatching { manager.updateViewLayout(view, layout) }
            }
            start()
        }

        savePosition(target, layout.y)
    }

    // ------------------------------------------------------------ fade

    /** Haath lagte hi poora saaf. */
    private fun wakeUp() {
        handler.removeCallbacks(fadeRunnable)
        orb?.animate()?.alpha(1f)?.setDuration(120)?.start()
    }

    /** Kuch der kuch na ho to dheere se halka pad jata hai. */
    private fun scheduleFade() {
        handler.removeCallbacks(fadeRunnable)
        handler.postDelayed(fadeRunnable, IDLE_MS)
    }

    // ------------------------------------------------------------ trash

    /** Neeche ka "X" - drag karke yahan chhodo to bubble abhi ke liye band. */
    private fun showTrash() {
        val manager = windowManager ?: return

        val icon = TextView(this).apply {
            text = "\u2715"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1A1A1A.toInt())
                setStroke(dp(1), 0xFFFF5252.toInt())
            }
            alpha = 0f
        }

        val size = dp(TRASH_DP)
        val layout = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TRASH_MARGIN_DP)
        }

        runCatching {
            manager.addView(icon, layout)
            trash = icon
            icon.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun hideTrash() {
        val view = trash ?: return
        trash = null
        runCatching { windowManager?.removeView(view) }
    }

    /** Paas aate hi X thoda bada ho jata hai - pata chalta hai ki chhod sakte ho. */
    private fun highlightTrash(near: Boolean) {
        val view = trash ?: return
        val scale = if (near) 1.3f else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    private fun inTrashZone(rawX: Float, rawY: Float): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val zoneTop = screenHeight - dp(TRASH_MARGIN_DP + TRASH_DP + 40)
        val centerX = screenWidth / 2f
        return rawY >= zoneTop && abs(rawX - centerX) <= dp(90)
    }

    // ------------------------------------------------------------ panel

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    /**
     * Single tap wala chhota bar.
     *
     * Poori app kholne ki zaroorat nahi - jo kaam sabse zyada hote hain wo
     * neeche ek patli si patti me aa jaate hain, aur peeche wali app dikhti
     * rehti hai.
     */
    private fun showPanel() {
        val manager = windowManager ?: return

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        row.addView(roundButton("\uD83D\uDCF7") { launchMain(command = "photo lo") })
        row.addView(gap())
        row.addView(roundButton("\u2191") { launchMain() })
        row.addView(gap())
        row.addView(pill())
        row.addView(gap())
        row.addView(roundButton("\uD83C\uDFA4") { launchMain(listen = true) })
        row.addView(gap())
        row.addView(roundButton("\u2715") { hidePanel() })

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(18)
        }

        runCatching {
            manager.addView(row, layout)
            panel = row
            row.alpha = 0f
            row.animate().alpha(1f).setDuration(160).start()
        }
    }

    private fun hidePanel() {
        val view = panel ?: return
        panel = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun roundButton(glyph: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = glyph
            setTextColor(0xFFF2F5F2.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF20B0D0B.toInt())
                setStroke(dp(1), 0xFF1E241F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            setOnClickListener { onClick() }
        }

    private fun pill(): View =
        TextView(this).apply {
            text = "E.V se poochho\u2026"
            setTextColor(0xFF7C857D.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(26).toFloat()
                setColor(0xF20B0D0B.toInt())
                setStroke(dp(1), 0xFF1E241F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            setOnClickListener { launchMain() }
        }

    private fun gap(): View =
        View(this).apply {
            layoutParams = ViewGroup.LayoutParams(dp(8), dp(1))
        }

    // ------------------------------------------------------------ helpers

    private fun launchMain(command: String? = null, listen: Boolean = false) {
        hidePanel()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (listen) intent.putExtra(Bubble.EXTRA_LISTEN, true)
        if (command != null) intent.putExtra(Bubble.EXTRA_COMMAND, command)

        runCatching { startActivity(intent) }
    }

    private fun savePosition(x: Int, y: Int) {
        prefs().edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
            .setContentText("Tap - bar, double tap - mic, lamba dabao - band")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    /**
     * Ghoomta hua taar wala gola.
     *
     * App wala bada orb Compose se banta hai; use overlay me daalne ke liye
     * poora lifecycle setup chahiye hota hai. Isliye yahan wahi shakl seedha
     * Canvas pe banayi hai - kaali gend ke upar hari lakeeren, jo dheere
     * dheere ghoomti hain. Lakeeren kinare pe paas paas aur beech me door
     * dikhti hain, isi se gol hone ka ehsaas hota hai.
     */
    private class OrbView(context: Context) : View(context) {

        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GREEN
            strokeCap = Paint.Cap.ROUND
        }
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GREEN
        }
        private val oval = RectF()

        private var phase = 0f
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
            val outer = min(w, h) / 2f
            val radius = outer * (if (pressed) 0.74f else 0.8f)

            glow.shader = RadialGradient(
                cx,
                cy,
                outer,
                intArrayOf(GREEN_SOFT, GREEN_FADE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, outer, glow)

            canvas.drawCircle(cx, cy, radius, core)

            // Kinare ki chamakti hui dhaar.
            rim.strokeWidth = outer * 0.035f
            rim.alpha = 220
            canvas.drawCircle(cx, cy, radius, rim)

            line.strokeWidth = outer * 0.028f

            // Khade taar (meridian) - ghoomte hue patle-chaude hote hain.
            for (i in 0 until MERIDIANS) {
                val angle = phase + i * (Math.PI.toFloat() / MERIDIANS)
                val halfWidth = abs(radius * cos(angle))
                oval.set(cx - halfWidth, cy - radius, cx + halfWidth, cy + radius)
                line.alpha = (70f + 170f * abs(sin(angle))).toInt().coerceIn(40, 255)
                canvas.drawOval(oval, line)
            }

            // Lete hue taar (latitude) - inse gend bharti hui lagti hai.
            for (i in 1 until LATITUDES) {
                val t = i.toFloat() / LATITUDES
                val y = -radius + 2f * radius * t
                val ratio = (1f - (y / radius) * (y / radius)).coerceAtLeast(0f)
                val halfWidth = radius * sqrt(ratio)
                val halfHeight = radius * 0.1f
                oval.set(cx - halfWidth, cy + y - halfHeight, cx + halfWidth, cy + y + halfHeight)
                line.alpha = 110
                canvas.drawOval(oval, line)
            }

            phase += SPIN_STEP
            postInvalidateOnAnimation()
        }

        private companion object {
            const val GREEN = 0xFF00E676.toInt()
            const val GREEN_SOFT = 0x5500E676
            const val GREEN_FADE = 0x2200E676
            const val MERIDIANS = 5
            const val LATITUDES = 4
            const val SPIN_STEP = 0.012f
        }
    }

    private companion object {
        const val SIZE_DP = 72
        const val TRASH_DP = 62
        const val TRASH_MARGIN_DP = 70
        const val LONG_PRESS_MS = 600L
        const val DOUBLE_TAP_MS = 280L
        const val SNAP_MS = 180L
        const val IDLE_MS = 4000L
        const val IDLE_ALPHA = 0.45f
        const val NOTIFICATION_ID = 4201
        const val CHANNEL_ID = "ev_bubble"
        const val PREFS = "ev_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
