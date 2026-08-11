package com.ev.android.feature.hud

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
 * Ghoomta hua bindiyon ka globe.
 *
 * GIF ko seedha chalane ke liye ek image library (Coil) chahiye hoti, sirf ek
 * animation ke liye. Isliye wahi cheez yahan Canvas pe banayi gayi hai — na
 * extra library, na kisi screen size pe blur.
 */
private class Dot(val x: Float, val y: Float, val z: Float)

/**
 * Golden-angle spiral: sphere pe [count] point barabar failane ka sabse saaf
 * tareeka. Latitude/longitude grid se karte to dono poles pe dots ka gucchha
 * ban jata.
 */
private fun evenlySpread(count: Int): List<Dot> {
    val golden = PI * (3.0 - sqrt(5.0))
    return (0 until count).map { i ->
        val y = 1.0 - (i / (count - 1.0)) * 2.0
        val radius = sqrt((1.0 - y * y).coerceAtLeast(0.0))
        val theta = golden * i
        Dot(
            x = (cos(theta) * radius).toFloat(),
            y = y.toFloat(),
            z = (sin(theta) * radius).toFloat(),
        )
    }
}

/** Ek horizontal chhalla — equator aur poles ke paas ki lakeeron ke liye. */
private fun ring(y: Float, count: Int): List<Dot> {
    val radius = sqrt((1f - y * y).coerceAtLeast(0f))
    return (0 until count).map { i ->
        val angle = 2.0 * PI * i / count
        Dot(
            x = (cos(angle) * radius).toFloat(),
            y = y,
            z = (sin(angle) * radius).toFloat(),
        )
    }
}

@Composable
fun EvOrb(
    listening: Boolean,
    busy: Boolean,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val active = listening || busy

    // Dot set ek hi baar banta hai — har frame pe banane ki koi zaroorat nahi.
    val dots = remember {
        evenlySpread(1000) +
            ring(0f, 150) +
            ring(0.02f, 120) +
            ring(-0.02f, 120) +
            ring(0.78f, 84) +
            ring(0.88f, 64) +
            ring(0.94f, 44) +
            ring(-0.78f, 84) +
            ring(-0.88f, 64) +
            ring(-0.94f, 44)
    }

    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 7000 else 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = if (active) 1.06f else 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val fade = if (dimmed) 0.75f else 1f

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f * 0.92f * pulse
        val cx = size.width / 2f
        val cy = size.height / 2f

        val angle = spin * PI.toFloat() / 180f
        val cosA = cos(angle)
        val sinA = sin(angle)

        dots.forEach { dot ->
            // Y-axis ke around ghumao.
            val x = dot.x * cosA + dot.z * sinA
            val z = -dot.x * sinA + dot.z * cosA

            // z = -1 (sabse peeche) .. +1 (sabse aage) -> 0..1
            val depth = (z + 1f) / 2f

            drawCircle(
                color = if (depth > 0.5f) EvGreenGlow else EvGreen,
                radius = radius * (0.005f + 0.010f * depth * depth),
                center = Offset(cx + x * radius, cy + dot.y * radius),
                alpha = ((0.16f + 0.84f * depth * depth) * fade).coerceIn(0f, 1f),
            )
        }
    }
}
