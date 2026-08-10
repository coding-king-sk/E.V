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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvGreenGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beech wala glowing orb.
 *
 * Ye sirf sajawat nahi hai — iski chaal se ek nazar me pata chal jata hai ki
 * E.V kis haalat me hai:
 *
 *  - dheemi saans jaisi pulse  -> ready, kuch nahi ho raha
 *  - tez pulse + tez ghoomta ring -> sun raha hai / kaam kar raha hai
 *  - halka dhundhla            -> hands-free band hai
 *
 * Poori cheez Canvas pe bani hai, koi image asset nahi — isliye APK me ek byte
 * bhi extra nahi jata aur har screen size pe sharp dikhta hai.
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

    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = if (active) 1.10f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 650 else 2200,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 6000 else 24000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    val fade = if (dimmed) 0.35f else 1f

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Andar ka glow — beech me sabse tez, kinare pe gayab.
        val coreRadius = radius * 0.62f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EvGreenGlow.copy(alpha = 0.95f * fade),
                    EvGreen.copy(alpha = 0.45f * fade),
                    Color.Transparent,
                ),
                center = center,
                radius = coreRadius,
            ),
            radius = coreRadius,
            center = center,
        )

        // Do patli rings — depth ke liye.
        drawCircle(
            color = EvGreen.copy(alpha = 0.50f * fade),
            radius = radius * 0.62f,
            center = center,
            style = Stroke(width = 1.5f),
        )
        drawCircle(
            color = EvGreen.copy(alpha = 0.30f * fade),
            radius = radius * 0.46f,
            center = center,
            style = Stroke(width = 1.5f),
        )

        // Bahar ka tick ring — ghoomta rehta hai.
        rotate(degrees = spin, pivot = center) {
            val ticks = 56
            for (index in 0 until ticks) {
                val angle = (2.0 * PI * index / ticks).toFloat()
                val isLong = index % 4 == 0

                val inner = radius * if (isLong) 0.80f else 0.87f
                val outer = radius * 0.97f

                drawLine(
                    color = EvGreen.copy(alpha = (if (isLong) 0.85f else 0.35f) * fade),
                    start = Offset(
                        center.x + cos(angle) * inner,
                        center.y + sin(angle) * inner,
                    ),
                    end = Offset(
                        center.x + cos(angle) * outer,
                        center.y + sin(angle) * outer,
                    ),
                    strokeWidth = if (isLong) 3f else 1.8f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
