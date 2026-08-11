package com.ev.android.feature.hud

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import com.ev.android.ui.theme.EvGreenGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ek dot se bana ghoomta hua globe \u2014 E.V ka chehra.
 *
 * Design reference se do cheezein aati hain:
 *  1. Dots latitude ki seedhi lines me hain (bikhre hue nahi). Isi se wo
 *     "wireframe globe" wala look aata hai; random dots noise lagte hain.
 *  2. Sphere thoda tilt hai, isliye upar-neeche ke rings ellipse dikhte hain
 *     aur globe flat circle ki jagah 3D lagta hai.
 *
 * Equator aur poles ke paas ki rings jaan-boojh ke zyada bright hain \u2014
 * reference me wahi lines sabse pehle aankh me aati hain.
 */
private class OrbDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val bright: Boolean,
)

/** Kitni latitude lines \u2014 isse zyada karne pe sirf GPU load badhta hai. */
private const val ROWS = 46

/** Equator pe itne dots; upar-neeche row chhoti hoti jati hai. */
private const val EQUATOR_DOTS = 56

/** Sphere ka jhukav \u2014 iske bina pole rings seedhi line dikhti hain. */
private const val TILT_DEGREES = 14.0

private fun ringAt(y: Float, count: Int, bright: Boolean, into: MutableList<OrbDot>) {
    val radius = sqrt((1f - y * y).coerceAtLeast(0f))
    for (i in 0 until count) {
        val angle = 2.0 * PI * i / count
        into.add(
            OrbDot(
                x = (radius * cos(angle)).toFloat(),
                y = y,
                z = (radius * sin(angle)).toFloat(),
                bright = bright,
            )
        )
    }
}

/**
 * Sphere ek hi baar banta hai aur har frame me sirf ghumaya jata hai \u2014
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
                )
            )
        }
    }

    // Chamakne wali lines: beech ki teen (equator band) aur dono taraf ke rings.
    ringAt(0f, 130, true, dots)
    ringAt(0.035f, 128, true, dots)
    ringAt(-0.035f, 128, true, dots)

    for (y in listOf(0.86f, 0.93f, -0.86f, -0.93f)) {
        val radius = sqrt((1f - y * y).coerceAtLeast(0f))
        ringAt(y, max(14, (EQUATOR_DOTS * radius * 1.6f).roundToInt()), true, dots)
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

    // Sun-ne ya sochne ke waqt tez ghoomta hai \u2014 isse pata chalta hai ki
    // app zinda hai. Idle me dheere, taaki battery aur aankh dono bache.
    val spinMillis = if (listening || busy) 9000 else 22000
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

    // Reference me orb safed/chandi jaisa hai. Sun-ne ke waqt halki hari
    // chamak aati hai taaki bina text padhe pata chal jaye ki mic on hai.
    val base = Color(0xFFEAF2EC)
    val accent = if (listening || busy) EvGreenGlow else base

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

        // Peeche ki halki roshni \u2014 orb kaale background pe tairta hua lagta hai.
        drawCircle(
            color = accent.copy(alpha = 0.05f * fade),
            radius = radius * 1.06f,
            center = center,
        )

        dots.forEach { dot ->
            // Pehle Y-axis pe ghumao (ye globe ka spin hai)…
            val x1 = dot.x * cosSpin + dot.z * sinSpin
            val z1 = -dot.x * sinSpin + dot.z * cosSpin

            // …phir sphere ko thoda jhukao, taaki upar ka ring dikhe.
            val y1 = dot.y * cosTilt - z1 * sinTilt
            val z2 = dot.y * sinTilt + z1 * cosTilt

            // Aage wale dots bade aur bright, peeche wale chhote aur dhundhle \u2014
            // isi se depth ka ehsaas hota hai.
            val depth = ((z2 + 1f) / 2f).coerceIn(0f, 1f)

            val alpha = ((if (dot.bright) 0.45f else 0.22f) + depth * 0.7f)
                .coerceIn(0f, 1f) * fade

            val dotRadius = (if (dot.bright) 1.55f else 1.2f) *
                (0.55f + depth * 0.95f) * dotScale

            drawCircle(
                color = (if (dot.bright) accent else base).copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(
                    x = center.x + x1 * radius,
                    y = center.y - y1 * radius,
                ),
            )
        }
    }
}
