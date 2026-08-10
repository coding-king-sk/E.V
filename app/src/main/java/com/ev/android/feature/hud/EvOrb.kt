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
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvGreenGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Beech wala ghoomta hua orb — bindiyon se bana globe.
 *
 * Ye sirf sajawat nahi hai; iski chaal se ek nazar me pata chal jata hai ki
 * E.V kis haalat me hai:
 *
 *  - dheeme ghoomta globe   -> ready, kuch nahi ho raha
 *  - tez ghoomta + halka bada -> sun raha hai / kaam kar raha hai
 *
 * Poori cheez Canvas pe bani hai, koi GIF ya image asset nahi. Isliye APK me
 * ek byte extra nahi jata, har screen size pe sharp rehta hai, aur rang theme
 * ke saath badla ja sakta hai. GIF chalane ke liye alag image library (Coil
 * jaisi) add karni padti, jo sirf ek animation ke liye zyada hai.
 */
@Composable
fun EvOrb(
    listening: Boolean,
    busy: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val active = listening || busy

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 7000 else 20000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = if (active) 1.06f else 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 700 else 2400,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Ek hi baar bante hain, har frame pe nahi — warna 800 se zyada point ka
    // hisaab 60 baar per second hota rehta.
    val dots = remember {
        evenlySpread(540) +
            ring(0f, 96) +
            ring(0.80f, 54) + ring(0.90f, 40) +
            ring(-0.80f, 54) + ring(-0.90f, 40)
    }

    val fade = if (dimmed) 0.75f else 1f

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f * 0.92f * pulse
        val cx = size.width / 2f
        val cy = size.height / 2f

        val angle = (spin * PI / 180.0).toFloat()
        val cosA = cos(angle)
        val sinA = sin(angle)

        dots.forEach { dot ->
            // Y axis ke around ghumao, phir seedha flat kar do.
            val x = dot.x * cosA + dot.z * sinA
            val z = -dot.x * sinA + dot.z * cosA

            // 0 = globe ke peeche, 1 = bilkul saamne.
            val depth = (z + 1f) / 2f

            drawCircle(
                color = if (depth > 0.55f) EvGreenGlow else EvGreen,
                radius = radius * (0.004f + 0.008f * depth),
                center = Offset(cx + x * radius, cy + dot.y * radius),
                alpha = ((0.12f + 0.88f * depth * depth) * fade).coerceIn(0f, 1f),
            )
        }
    }
}

private class Dot(val x: Float, val y: Float, val z: Float)

/**
 * Gole pe barabar failaye hue points.
 *
 * Seedha random ya lat/long grid lene se dots poles pe ikatthe ho jate hain
 * aur equator khaali dikhta hai. Golden angle wala tareeka har point ko
 * barabar jagah deta hai.
 */
private fun evenlySpread(count: Int): List<Dot> {
    val golden = PI * (3.0 - sqrt(5.0))

    return List(count) { index ->
        val y = 1f - (index / (count - 1).toFloat()) * 2f
        val r = sqrt((1f - y * y).coerceAtLeast(0f))
        val theta = (golden * index).toFloat()

        Dot(cos(theta) * r, y, sin(theta) * r)
    }
}

/** Ek nishchit unchai pe dots ka gol chhalla. */
private fun ring(y: Float, count: Int): List<Dot> {
    val r = sqrt((1f - y * y).coerceAtLeast(0f))

    return List(count) { index ->
        val angle = (2.0 * PI * index / count).toFloat()
        Dot(cos(angle) * r, y, sin(angle) * r)
    }
}
