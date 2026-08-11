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
 * Ek dot se bana ghoomta hua globe \u2014 E.V ka chehra.
 *
 * Design reference se teen cheezein aati hain:
 *  1. Dots latitude ki seedhi lines me hain (bikhre hue nahi).
 *  2. Beech me sirf EK chamakti hui line hai \u2014 equator.
 *  3. Poora orb ek hi rang ka hai.
 *
 * Bolte waqt wahi beech wali line awaaz ke saath lehrati hai. Wave dots ki
 * **jagah** hilata hai, unki ginti ya motai nahi \u2014 isliye chup hote hi line
 * wapas bilkul seedhi ho jati hai aur design wahi ka wahi rehta hai.
 *
 * Ab har cheez [OrbStyle] se aati hai, code me fix nahi hai \u2014 user orb pe tap
 * karke ise khud badal sakta hai.
 */
private class OrbDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val bright: Boolean,
    /** Sirf beech wali line lehrati hai. */
    val equator: Boolean,
    /** Ring me is dot ka kona \u2014 wave isi se chalti hai. */
    val phase: Float,
)

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
 * Sphere ek hi baar banta hai aur har frame me sirf ghumaya jata hai \u2014
 * hazaaron dots har frame banana bekaar ka kaam hota.
 */
private fun buildSphere(style: OrbStyle): List<OrbDot> {
    val rows = style.rows.coerceIn(12, 80)
    val equatorDots = style.density.coerceIn(16, 120)

    val dots = ArrayList<OrbDot>(2200)

    for (row in 0 until rows) {
        val theta = PI * (row + 0.5) / rows
        val y = cos(theta).toFloat()
        val radius = sin(theta).toFloat()
        val count = max(6, (equatorDots * radius).roundToInt())

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
    if (style.equatorLine) {
        ringAt(
            y = 0f,
            count = max(48, (equatorDots * 2.35f).roundToInt()),
            bright = true,
            equator = true,
            into = dots,
        )
    }

    // Poles ke paas ek-ek halki ring \u2014 inhi se sphere jhuka hua dikhta hai.
    if (style.poleRings) {
        for (y in listOf(0.9f, -0.9f)) {
            val radius = sqrt((1f - y * y).coerceAtLeast(0f))
            ringAt(
                y = y,
                count = max(14, (equatorDots * radius * 1.6f).roundToInt()),
                bright = true,
                equator = false,
                into = dots,
            )
        }
    }

    return dots
}

@Composable
fun EvOrb(
    listening: Boolean,
    busy: Boolean,
    dimmed: Boolean,
    style: OrbStyle = OrbStyle(),
    modifier: Modifier = Modifier,
) {
    val dots = remember(style.rows, style.density, style.equatorLine, style.poleRings) {
        buildSphere(style)
    }

    val transition = rememberInfiniteTransition(label = "orb")

    // Sun-ne ya sochne ke waqt tez ghoomta hai \u2014 isse pata chalta hai ki
    // app zinda hai. Idle me dheere, taaki battery aur aankh dono bache.
    val active = listening || busy
    val idleMillis = style.spinSeconds.coerceIn(4, 60) * 1000
    val spinMillis = if (active) idleMillis / 2 else idleMillis
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinMillis, easing = LinearEasing)),
        label = "spin",
    )

    val breath by transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val pulse = if (style.pulse) breath else 1f

    // Lehar ring ke chaaron taraf chalti rehti hai \u2014 isse wave zinda lagti hai,
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

    val base = Color(style.colorArgb)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension / 2f) * 0.92f * pulse
        val dotScale = (radius / 150f) * style.dotScale

        val spin = Math.toRadians(angle.toDouble())
        val cosSpin = cos(spin).toFloat()
        val sinSpin = sin(spin).toFloat()

        val tilt = Math.toRadians(style.tiltDegrees.toDouble())
        val cosTilt = cos(tilt).toFloat()
        val sinTilt = sin(tilt).toFloat()

        val fade = if (dimmed) 0.45f else 1f

        // Mic on hone ka ishara: sirf chamak badhti hai, rang wahi rehta hai.
        val boost = if (active) 1.18f else 1f

        // Peeche ki halki roshni \u2014 orb kaale background pe tairta hua lagta hai.
        val halo = ((0.05f + amplitude * 0.05f) * fade * boost * style.glow)
            .coerceIn(0f, 1f)
        if (halo > 0.002f) {
            drawCircle(
                color = base.copy(alpha = halo),
                radius = radius * (1.06f + amplitude * 0.05f),
                center = center,
            )
        }

        dots.forEach { dot ->
            // Bolte waqt beech wali line lehrati hai. Baaki poora globe sthir
            // rehta hai \u2014 warna orb hilta hua nahi, tootta hua lagta hai.
            val lift = if (dot.equator && amplitude > 0.01f) {
                amplitude * style.waveHeight *
                    sin(style.waveCount.toDouble() * dot.phase + wavePhase).toFloat()
            } else {
                0f
            }

            // Pehle Y-axis pe ghumao (ye globe ka spin hai)\u2026
            val x1 = dot.x * cosSpin + dot.z * sinSpin
            val z1 = -dot.x * sinSpin + dot.z * cosSpin

            // \u2026phir sphere ko thoda jhukao, taaki upar ka ring dikhe.
            val y0 = dot.y + lift
            val y1 = y0 * cosTilt - z1 * sinTilt
            val z2 = y0 * sinTilt + z1 * cosTilt

            // Aage wale dots bade aur bright, peeche wale chhote aur dhundhle \u2014
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
