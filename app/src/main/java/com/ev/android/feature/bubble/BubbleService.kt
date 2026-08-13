package com.ev.android.feature.bubble

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.ev.android.MainActivity
import com.ev.android.feature.voice.EvListeningService
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
 * Single tap  -> neeche action bar khulta hai AUR command wala mic turant
 *                sunne lagta hai. Wake word bolne ki zaroorat nahi.
 * Double tap  -> poori app khul jati hai, wo bhi sunte hue
 * Drag        -> chhodte hi kinare pe chipak jata hai
 * Neeche X pe chhodo -> bubble abhi ke liye band
 * Lamba dabao -> bubble band
 * Kuch der haath na lage -> halka transparent, aur ghoomna bhi ruk jata hai
 */
class BubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var orb: OrbView? = null

    private var trash: View? = null
    private var panel: View? = null
    private var field: EditText? = null

    private var recognizer: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable {
        val view = orb ?: return@Runnable
        view.animate()
            .alpha(IDLE_ALPHA)
            .setDuration(400)
            // Jab tak koi dekh hi nahi raha, ghoomte rehne ka koi fayda nahi -
            // isse phone ka CPU khali baithta hai aur doosri apps tez chalti
            // hain.
            .withEndAction { view.setSpinning(false) }
            .start()
    }

    /** Bar khuli reh gayi aur user kuch na kare to khud band ho jaye. */
    private val panelTimeout = Runnable { hidePanel() }

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
        stopBarMic()
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

    /** Haath lagte hi poora saaf, aur ghoomna dobara chalu. */
    private fun wakeUp() {
        handler.removeCallbacks(fadeRunnable)
        val view = orb ?: return
        view.setSpinning(true)
        view.animate().alpha(1f).setDuration(120).start()
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
     * Single tap wala action bar.
     *
     * Design app ki HUD jaisa hai - kaala card, hari dhaar, aur andar wahi
     * gol buttons jo home screen pe hain.
     *
     * Bar khulte hi command wala mic chalu ho jata hai. Bar apne aap band ho
     * jati hai agar user kuch na kare, ya kahin bhi bahar tap kar de - screen
     * pe atki hui patti se zyada chidhane wali cheez koi nahi.
     */
    private fun showPanel() {
        val manager = windowManager ?: return

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(SURFACE)
                setStroke(dp(1), OUTLINE)
            }
        }

        val input = EditText(this).apply {
            hint = "E.V SE POOCHHO\u2026"
            setHintTextColor(TEXT_MUTED)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(SURFACE_HIGH)
                setStroke(dp(1), OUTLINE)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnEditorActionListener { _, actionId, _ ->
                val done = actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO
                if (done) {
                    val typed = text?.toString().orEmpty().trim()
                    if (typed.isNotEmpty()) launchMain(command = typed)
                }
                done
            }
            setOnClickListener { keepPanelAlive() }
        }
        field = input

        row.addView(iconButton(IconView.CAMERA) { launchMain(command = "photo lo") })
        row.addView(gap())
        row.addView(input)
        row.addView(gap())
        row.addView(iconButton(IconView.MIC) { startBarMic() })
        row.addView(gap())
        row.addView(iconButton(IconView.CLOSE) { hidePanel() })

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusableInTouchMode = true
            addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnKeyListener { _, keyCode, event ->
                // Focusable overlay back button kha jata hai, isliye khud hi
                // band kar dete hain.
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hidePanel()
                    true
                } else {
                    false
                }
            }
            setOnTouchListener { _, event ->
                // Bar ke bahar kahin bhi tap - band.
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hidePanel()
                    true
                } else {
                    false
                }
            }
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // NOT_FOCUSABLE nahi hai - warna text box me keyboard hi nahi
            // khulta. NOT_TOUCH_MODAL se bahar ke tap neeche wali app tak
            // pahunchte hain, aur WATCH_OUTSIDE_TOUCH se humein bhi khabar
            // mil jati hai ki bahar tap hua.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(18)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        runCatching {
            manager.addView(container, layout)
            panel = container
            container.alpha = 0f
            container.animate().alpha(1f).setDuration(160).start()
        }.onSuccess {
            keepPanelAlive()
            startBarMic()
        }
    }

    /** Jab bhi user kuch kare, band hone ki ginti dobara shuru. */
    private fun keepPanelAlive() {
        handler.removeCallbacks(panelTimeout)
        handler.postDelayed(panelTimeout, PANEL_IDLE_MS)
    }

    private fun hidePanel() {
        handler.removeCallbacks(panelTimeout)
        stopBarMic()
        val view = panel ?: return
        panel = null
        field = null
        runCatching {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        runCatching { windowManager?.removeView(view) }
    }

    private fun iconButton(kind: Int, onClick: () -> Unit): View =
        IconView(this, kind).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SURFACE_HIGH)
                setStroke(dp(1), OUTLINE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener {
                keepPanelAlive()
                onClick()
            }
        }

    private fun gap(): View =
        View(this).apply {
            layoutParams = ViewGroup.LayoutParams(dp(8), dp(1))
        }

    // ------------------------------------------------------------ bar mic

    /**
     * Bar ke andar hi sunna - wahi command wala mic jo home screen pe hai.
     *
     * Hands-free wala listener aur ye dono ek saath mic nahi le sakte; Android
     * doosre ko seedha ERROR_RECOGNIZER_BUSY de deta hai. Isliye bar ka mic
     * chalte waqt hands-free ko band kar dete hain - user ne tap karke bola
     * hai, ab wake word ka intezaar karne ka koi matlab nahi.
     */
    private fun startBarMic() {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !SpeechRecognizer.isRecognitionAvailable(this)) {
            launchMain(listen = true)
            return
        }

        stopBarMic()

        if (EvListeningService.isRunning) {
            EvListeningService.stop(this)
            setBarHint("MIC LE RAHA HOON\u2026")
            // Mic chhootne me ek pal lagta hai.
            handler.postDelayed({ if (panel != null) beginRecognition() }, MIC_HANDOVER_MS)
            return
        }

        beginRecognition()
    }

    private fun beginRecognition() {
        val speech = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = speech

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                keepPanelAlive()
                setBarHint("SUN RAHA HOON\u2026")
            }

            override fun onBeginningOfSpeech() {
                keepPanelAlive()
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                setBarHint("SOCH RAHA HOON\u2026")
            }

            override fun onError(error: Int) {
                releaseRecognizer()
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    -> launchMain(listen = true)

                    else -> setBarHint("PHIR SE BOLO\u2026")
                }
            }

            override fun onResults(results: Bundle?) {
                releaseRecognizer()
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (heard.isEmpty()) {
                    setBarHint("KUCH SUNAI NAHI DIYA")
                } else {
                    launchMain(command = heard)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (heard.isNotBlank()) {
                    keepPanelAlive()
                    field?.setText(heard)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // en-IN Hinglish ko Latin me likhta hai - Devanagari se parser ka
            // kaam mushkil ho jata hai.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        runCatching { speech.startListening(intent) }
            .onFailure {
                releaseRecognizer()
                launchMain(listen = true)
            }
    }

    private fun releaseRecognizer() {
        val speech = recognizer ?: return
        recognizer = null
        runCatching { speech.destroy() }
    }

    private fun stopBarMic() {
        val speech = recognizer ?: return
        recognizer = null
        runCatching {
            speech.cancel()
            speech.destroy()
        }
    }

    private fun setBarHint(text: String) {
        field?.hint = text
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
            // Mic wala type sirf tab lagta hai jab RECORD_AUDIO mil chuki ho.
            // Bina permission ke ye type dete hi Android app ko gira deta hai.
            val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val type = if (granted) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }

            runCatching { startForeground(NOTIFICATION_ID, notification, type) }
                .onFailure {
                    runCatching {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                        )
                    }
                }
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
            .setContentText("Tap - bar aur mic, double tap - app, lamba dabao - band")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    /**
     * Bar ke gol buttons ke icon.
     *
     * App ke HUD wale buttons bhi Canvas pe bante hain, isliye yahan bhi wahi
     * tarika hai - emoji dalne se har phone pe alag shakl aati aur design
     * app se match nahi karta.
     */
    private class IconView(context: Context, private val kind: Int) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = GREEN
        }
        private val box = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val size = min(w, h)
            val cx = w / 2f
            val cy = h / 2f
            paint.strokeWidth = size * 0.07f

            when (kind) {
                CAMERA -> {
                    val bw = size * 0.54f
                    val bh = size * 0.40f
                    box.set(cx - bw / 2f, cy - bh / 2f + size * 0.04f, cx + bw / 2f, cy + bh / 2f + size * 0.04f)
                    canvas.drawRoundRect(box, size * 0.09f, size * 0.09f, paint)
                    canvas.drawCircle(cx, cy + size * 0.04f, size * 0.11f, paint)
                    canvas.drawLine(
                        cx - size * 0.08f,
                        cy - bh / 2f + size * 0.04f,
                        cx + size * 0.02f,
                        cy - size * 0.26f,
                        paint,
                    )
                }

                MIC -> {
                    val mw = size * 0.20f
                    val mh = size * 0.36f
                    box.set(cx - mw / 2f, cy - mh / 2f - size * 0.06f, cx + mw / 2f, cy + mh / 2f - size * 0.06f)
                    canvas.drawRoundRect(box, mw / 2f, mw / 2f, paint)

                    val arc = size * 0.30f
                    box.set(cx - arc, cy - arc + size * 0.02f, cx + arc, cy + arc + size * 0.02f)
                    canvas.drawArc(box, 20f, 140f, false, paint)

                    canvas.drawLine(cx, cy + size * 0.24f, cx, cy + size * 0.34f, paint)
                }

                else -> {
                    val arm = size * 0.20f
                    canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, paint)
                    canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, paint)
                }
            }
        }

        companion object {
            const val CAMERA = 0
            const val MIC = 1
            const val CLOSE = 2
            private const val GREEN = 0xFF00E676.toInt()
        }
    }

    /**
     * Ghoomta hua taar wala gola.
     *
     * Har lakeer ek jhuka hua chakkar hai jisme lehar dali gayi hai. Do
     * cheezein alag alag chalti hain: poori gend apni raftaar se ghoomti hai,
     * aur har lakeer ki lehar bhi apni raftaar se sarakti hai - isi se aisa
     * lagta hai ki taar khud bhi bal kha rahe hain, sirf gola nahi ghoom raha.
     *
     * Chamak blur se nahi banti - blur ke liye poora view software pe draw
     * karna padta hai, jo phone ko dheema kar deta hai. Uski jagah har lakeer
     * ke neeche ek chaudi halki lakeer hai, jisse wahi neon wala ehsaas aa
     * jata hai aur GPU ka kaam bhi kam rehta hai.
     */
    private class OrbView(context: Context) : View(context) {

        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val strand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = GREEN
        }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = GREEN
        }

        private var phase = 0f
        private var pressed = false
        private var spinning = true

        fun setPressedLook(value: Boolean) {
            pressed = value
            invalidate()
        }

        /** Idle hone pe ghoomna band - CPU doosri apps ko mil jata hai. */
        fun setSpinning(value: Boolean) {
            if (spinning == value) return
            spinning = value
            if (value) invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val outer = min(w, h) / 2f
            val radius = outer * (if (pressed) 0.72f else 0.78f)

            glow.shader = RadialGradient(
                cx,
                cy,
                outer,
                intArrayOf(GREEN_SOFT, GREEN_FADE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, outer, glow)

            // Gend khud kaali hai - lakeeren usi pe tairti hain.
            canvas.drawCircle(cx, cy, radius, core)

            strand.strokeWidth = outer * 0.045f
            halo.strokeWidth = outer * 0.115f

            for (i in 0 until STRANDS) {
                val tilt = TILTS[i]
                val wobble = WOBBLES[i]
                val lobes = LOBES[i]

                // Lehar khud sarakti hai, aur har lakeer alag raftaar se.
                val twist = i * 0.9f + phase * DRIFTS[i]

                // Gend bhi ghoomti hai; har lakeer thodi si alag chaal me,
                // warna sab ek saath ghoomti hui jaali jaisi lagti hai.
                val spin = phase * SPEEDS[i]
                val spinCos = cos(spin)
                val spinSin = sin(spin)

                val tiltCos = cos(tilt)
                val tiltSin = sin(tilt)

                var prevX = 0f
                var prevY = 0f
                var prevZ = 0f

                for (s in 0..SEGMENTS) {
                    val t = s.toFloat() / SEGMENTS
                    val angle = t * TWO_PI

                    val lift = wobble * sin(angle * lobes + twist)
                    val liftCos = cos(lift)

                    val x0 = liftCos * cos(angle)
                    val y0 = sin(lift)
                    val z0 = liftCos * sin(angle)

                    // Har lakeer ka apna jhukav.
                    val y1 = y0 * tiltCos - z0 * tiltSin
                    val z1 = y0 * tiltSin + z0 * tiltCos

                    // Ghoomna.
                    val x2 = x0 * spinCos + z1 * spinSin
                    val z2 = -x0 * spinSin + z1 * spinCos

                    val sx = cx + radius * x2
                    val sy = cy + radius * y1

                    if (s > 0) {
                        // 1 = bilkul samne, 0 = bilkul peeche.
                        val depth = (((z2 + prevZ) / 2f) + 1f) / 2f
                        val bright = depth * depth

                        if (bright > 0.55f) {
                            halo.alpha = ((bright - 0.55f) * 150f).toInt().coerceIn(0, 60)
                            canvas.drawLine(prevX, prevY, sx, sy, halo)
                        }

                        strand.alpha = (25f + 225f * bright).toInt().coerceIn(18, 255)
                        canvas.drawLine(prevX, prevY, sx, sy, strand)
                    }

                    prevX = sx
                    prevY = sy
                    prevZ = z2
                }
            }

            if (spinning) {
                phase += SPIN_STEP
                // 60 fps ki zaroorat nahi - 30 pe bhi utna hi smooth lagta hai
                // aur baaki apps ko CPU mil jata hai.
                postInvalidateDelayed(FRAME_MS)
            }
        }

        private companion object {
            const val GREEN = 0xFF00E676.toInt()
            const val GREEN_SOFT = 0x5500E676
            const val GREEN_FADE = 0x1E00E676
            const val STRANDS = 6
            const val SEGMENTS = 30
            const val SPIN_STEP = 0.022f
            const val FRAME_MS = 33L
            const val TWO_PI = 6.2831855f

            /** Jhukav, lehar ki gehrai, lehron ki ginti. */
            val TILTS = floatArrayOf(0.15f, 0.62f, 1.12f, 1.62f, 2.15f, 2.65f)
            val WOBBLES = floatArrayOf(0.55f, 0.38f, 0.62f, 0.30f, 0.48f, 0.66f)
            val LOBES = floatArrayOf(2f, 3f, 2f, 4f, 3f, 2f)

            /** Ghoomne ki aur lehar sarakne ki apni apni raftaar. */
            val SPEEDS = floatArrayOf(1f, 0.82f, 1.18f, 0.9f, 1.32f, 0.72f)
            val DRIFTS = floatArrayOf(0.9f, -1.3f, 1.6f, -0.8f, 1.1f, -1.5f)
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

        /** Bar itni der bekaar khadi rahe to khud band. */
        const val PANEL_IDLE_MS = 35_000L

        /** Hands-free ke mic chhodne ka intezaar. */
        const val MIC_HANDOVER_MS = 450L

        const val NOTIFICATION_ID = 4201
        const val CHANNEL_ID = "ev_bubble"
        const val PREFS = "ev_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"

        // App ke HUD wale rang - bar inhi se banti hai.
        const val SURFACE = 0xF20B0D0B.toInt()
        const val SURFACE_HIGH = 0xFF141815.toInt()
        const val OUTLINE = 0xFF1E241F.toInt()
        const val TEXT_PRIMARY = 0xFFF2F5F2.toInt()
        const val TEXT_MUTED = 0xFF7C857D.toInt()
    }
}
