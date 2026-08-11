package com.ev.android.feature.hud

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ev.android.feature.voice.MicLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ek dot se bana ghoomta hua globe — E.V ka chehra.
 *
 * Design reference se teen cheezein aati hain:
 *  1. Dots latitude ki seedhi lines me hain (bikhre hue nahi). Isi se wo
 *     "wireframe globe" wala look aata hai; random dots noise lagte hain.
 *  2. Beech me sirf EK chamakti hui line hai — equator.
 *  3. Poora orb safed/chandi hai. Mic on hone ka pata rang se nahi chalta.
 *
 * Bolte waqt wahi beech wali line awaaz ke saath lehrati hai. Wave dots ki
 * **jagah** hilata hai, unki ginti ya motai nahi — isliye chup hote hi line
 * wapas bilkul seedhi ho jati hai aur design wahi ka wahi rehta hai.
 *
 * Sphere thoda tilt hai, isliye upar-neeche ke rings ellipse dikhte hain aur
 * globe flat circle ki jagah 3D lagta hai.
 */
private class OrbDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val bright: Boolean,
    /** Sirf beech wali line lehrati hai. */
    val equator: Boolean,
    /** Ring me is dot ka kona — wave isi se chalti hai. */
    val phase: Float,
)

/** Kitni latitude lines — isse zyada karne pe sirf GPU load badhta hai. */
private const val ROWS = 46

/** Equator pe itne dots; upar-neeche row chhoti hoti jati hai. */
private const val EQUATOR_DOTS = 56

/** Sphere ka jhukav — iske bina pole rings seedhi line dikhti hain. */
private const val TILT_DEGREES = 14.0

/** Wave me kitni lehrein — zyada karne pe line jhalar jaisi lagne lagti hai. */
private const val WAVE_COUNT = 3.0

/** Sabse zor se bolne pe line itni upar-neeche jayegi (radius ka hissa). */
private const val WAVE_HEIGHT = 0.16f

private fun ringAt(
    y: Float,
    count: Int,
    bright: Boolean,
    equator: Boolean,
    into: MutableList<OrbDot>,
) {
    val radius = sqrt((1f - y * y).coerceAtLeast(0f))
    for (i in 0 until count) {
        val angle = 2.0 * PI * i / count
        into.add(
            OrbDot(
                x = (radius * cos(angle)).toFloat(),
                y = y,
                z = (radius * sin(angle)).toFloat(),
                bright = bright,
                equator = equator,
                phase = angle.toFloat(),
            )
        )
    }
}

/**
 * Sphere ek hi baar banta hai aur har frame me sirf ghumaya jata hai —
 * ~1600 dots har frame banana bekaar ka kaam hota.
 */
private fun buildSphere(): List<OrbDot> {
    val dots = ArrayList<OrbDot>(1800)

    for (row in 0 until ROWS) {
        val theta = PI * (row + 0.5) / ROWS
        val y = cos(theta).toFloat()
        val radius = sin(theta).toFloat()
        val count = max(6, (EQUATOR_DOTS * radius).roundToInt())

        for (i in 0 until count) {
            val angle = 2.0 * PI * i / count
            dots.add(
                OrbDot(
                    x = (radius * cos(angle)).toFloat(),
                    y = y,
                    z = (radius * sin(angle)).toFloat(),
                    bright = false,
                    equator = false,
                    phase = angle.toFloat(),
                )
            )
        }
    }

    // Beech me sirf ek line. Dots thode zyada rakhe hain taaki wo ek theek
    // se chamakti hui lakeer bane, na ki moti patti.
    ringAt(0f, 132, bright = true, equator = true, into = dots)

    // Poles ke paas ek-ek halki ring — inhi se sphere jhuka hua dikhta hai.
    for (y in listOf(0.9f, -0.9f)) {
        val radius = sqrt((1f - y * y).coerceAtLeast(0f))
        ringAt(
            y = y,
            count = max(14, (EQUATOR_DOTS * radius * 1.6f).roundToInt()),
            bright = true,
            equator = false,
            into = dots,
        )
    }

    return dots
}

@Composable
fun EvOrb(
    listening: Boolean,
    busy: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val dots = remember { buildSphere() }

    val transition = rememberInfiniteTransition(label = "orb")

    // Sun-ne ya sochne ke waqt tez ghoomta hai — isse pata chalta hai ki
    // app zinda hai. Idle me dheere, taaki battery aur aankh dono bache.
    val active = listening || busy
    val spinMillis = if (active) 9000 else 22000
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinMillis, easing = LinearEasing)),
        label = "spin",
    )

    val pulse by transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Lehar ring ke chaaron taraf chalti rehti hai — isse wave zinda lagti hai,
    // sirf upar-neeche hilne ki jagah.
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "wave",
    )

    // Mic ka level seedha lagane pe orb jhatke khata hai (rmsdB har 100ms me
    // uchhalta hai), isliye beech me ek chhoti si smoothing.
    val amplitude by animateFloatAsState(
        targetValue = if (listening) MicLevel.value else 0f,
        animationSpec = tween(110, easing = LinearEasing),
        label = "amplitude",
    )

    // Ek hi rang, poore orb me — thanda safed, halka sa neela-chandi.
    val base = Color(0xFFEFF4F6)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension / 2f) * 0.92f * pulse
        val dotScale = radius / 150f

        val spin = Math.toRadians(angle.toDouble())
        val cosSpin = cos(spin).toFloat()
        val sinSpin = sin(spin).toFloat()

        val tilt = Math.toRadians(TILT_DEGREES)
        val cosTilt = cos(tilt).toFloat()
        val sinTilt = sin(tilt).toFloat()

        val fade = if (dimmed) 0.45f else 1f

        // Mic on hone ka ishara: sirf chamak badhti hai, rang wahi rehta hai.
        val boost = if (active) 1.18f else 1f

        // Peeche ki halki roshni — orb kaale background pe tairta hua lagta hai.
        drawCircle(
            color = base.copy(alpha = (0.05f + amplitude * 0.05f) * fade * boost),
            radius = radius * (1.06f + amplitude * 0.05f),
            center = center,
        )

        dots.forEach { dot ->
            // Bolte waqt beech wali line lehrati hai. Baaki poora globe sthir
            // rehta hai — warna orb hilta hua nahi, tootta hua lagta hai.
            val lift = if (dot.equator && amplitude > 0.01f) {
                amplitude * WAVE_HEIGHT *
                    sin(WAVE_COUNT * dot.phase + wavePhase).toFloat()
            } else {
                0f
            }

            // Pehle Y-axis pe ghumao (ye globe ka spin hai)…
            val x1 = dot.x * cosSpin + dot.z * sinSpin
            val z1 = -dot.x * sinSpin + dot.z * cosSpin

            // …phir sphere ko thoda jhukao, taaki upar ka ring dikhe.
            val y0 = dot.y + lift
            val y1 = y0 * cosTilt - z1 * sinTilt
            val z2 = y0 * sinTilt + z1 * cosTilt

            // Aage wale dots bade aur bright, peeche wale chhote aur dhundhle —
            // isi se depth ka ehsaas hota hai.
            val depth = ((z2 + 1f) / 2f).coerceIn(0f, 1f)

            val extra = if (dot.equator) amplitude * 0.25f else 0f

            val alpha = ((if (dot.bright) 0.5f else 0.2f) + depth * 0.68f + extra)
                .coerceIn(0f, 1f) * fade * boost

            val dotRadius = (if (dot.bright) 1.6f else 1.15f) *
                (0.55f + depth * 0.95f) * dotScale

            drawCircle(
                color = base.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = dotRadius,
                center = Offset(
                    x = center.x + x1 * radius,
                    y = center.y - y1 * radius,
                ),
            )
        }
    }
}
