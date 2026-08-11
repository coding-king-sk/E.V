package com.ev.android.feature.hud

import android.content.Context

/**
 * Orb ka poora design, ek jagah.
 *
 * Pehle ye sab file ke andar `const val` the \u2014 badalna ho to code badalna
 * padta tha. Ab har cheez yahan hai aur user khud app ke andar se badal sakta
 * hai (orb pe tap karke).
 *
 * Har value ki apni hadd hai. Bahut zyada dots pe phone garam hota hai aur
 * bahut kam pe globe jaali jaisa lagta hai, isliye slider ki seemaayein jaan
 * boojh ke tang rakhi hain.
 */
data class OrbStyle(
    /** Dots ka rang (ARGB). */
    val colorArgb: Long = 0xFFEFF4F6,
    /** Screen pe orb kitna bada dikhega. */
    val sizeDp: Int = 250,
    /** Kitni latitude lines. */
    val rows: Int = 46,
    /** Equator pe kitne dots \u2014 upar-neeche rows apne aap chhoti hoti jaati hain. */
    val density: Int = 56,
    /** Har dot ka size (guna). */
    val dotScale: Float = 1f,
    /** Ek chakkar poora hone me kitne second (idle me). */
    val spinSeconds: Int = 22,
    /** Sphere ka jhukav \u2014 isi se pole rings ellipse dikhte hain. */
    val tiltDegrees: Float = 14f,
    /** Sabse zor se bolne pe beech wali line kitni upar-neeche jayegi. */
    val waveHeight: Float = 0.16f,
    /** Ring ke chaaron taraf kitni lehrein. */
    val waveCount: Int = 3,
    /** Peeche wali roshni kitni tez. */
    val glow: Float = 1f,
    /** Halka sa saans lena (dheere bada-chhota hona). */
    val pulse: Boolean = true,
    /** Beech wali chamakti hui line. */
    val equatorLine: Boolean = true,
    /** Upar-neeche ke do halke ring. */
    val poleRings: Boolean = true,
)

/** Orb ka design phone me save rehta hai \u2014 app band karne pe bhi. */
object OrbStyleStore {

    val DEFAULT = OrbStyle()

    /** Tayyar rang \u2014 chandi, green, neela, jamuni, gulabi, sunehra, laal, cyan. */
    val PRESET_COLORS = listOf(
        0xFFEFF4F6,
        0xFF00E676,
        0xFF29B6F6,
        0xFF7C4DFF,
        0xFFFF4081,
        0xFFFFC107,
        0xFFFF5252,
        0xFF00E5FF,
    )

    private const val PREFS = "ev_orb"

    fun load(context: Context): OrbStyle {
        val p = prefs(context)
        return OrbStyle(
            colorArgb = p.getLong("color", DEFAULT.colorArgb),
            sizeDp = p.getInt("size", DEFAULT.sizeDp).coerceIn(140, 340),
            rows = p.getInt("rows", DEFAULT.rows).coerceIn(12, 80),
            density = p.getInt("density", DEFAULT.density).coerceIn(16, 120),
            dotScale = p.getFloat("dot", DEFAULT.dotScale).coerceIn(0.5f, 2.5f),
            spinSeconds = p.getInt("spin", DEFAULT.spinSeconds).coerceIn(4, 60),
            tiltDegrees = p.getFloat("tilt", DEFAULT.tiltDegrees).coerceIn(0f, 45f),
            waveHeight = p.getFloat("wave", DEFAULT.waveHeight).coerceIn(0f, 0.45f),
            waveCount = p.getInt("waves", DEFAULT.waveCount).coerceIn(1, 8),
            glow = p.getFloat("glow", DEFAULT.glow).coerceIn(0f, 2.5f),
            pulse = p.getBoolean("pulse", DEFAULT.pulse),
            equatorLine = p.getBoolean("equator", DEFAULT.equatorLine),
            poleRings = p.getBoolean("poles", DEFAULT.poleRings),
        )
    }

    fun save(context: Context, style: OrbStyle) {
        prefs(context).edit()
            .putLong("color", style.colorArgb)
            .putInt("size", style.sizeDp)
            .putInt("rows", style.rows)
            .putInt("density", style.density)
            .putFloat("dot", style.dotScale)
            .putInt("spin", style.spinSeconds)
            .putFloat("tilt", style.tiltDegrees)
            .putFloat("wave", style.waveHeight)
            .putInt("waves", style.waveCount)
            .putFloat("glow", style.glow)
            .putBoolean("pulse", style.pulse)
            .putBoolean("equator", style.equatorLine)
            .putBoolean("poles", style.poleRings)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
