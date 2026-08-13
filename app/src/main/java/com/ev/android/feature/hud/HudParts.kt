package com.ev.android.feature.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvRed
import com.ev.android.ui.theme.EvSurface
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary

/**
 * HUD ke chhote-chhote hisse.
 *
 * Sab yahan ek jagah rakhe hain taaki screen ka main file sirf logic dikhaye,
 * pixel-pushing nahi.
 */

/** Upar wali line: E badge + naam, aur daayin taraf network status + gear. */
@Composable
fun HudHeader(
    networkOnline: Boolean,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(2.dp, EvGreen), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "E",
                color = EvGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = "E.V",
            color = EvTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "NETWORK",
                color = EvTextMuted,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = if (networkOnline) "CONNECTED" else "OFFLINE",
                color = if (networkOnline) EvGreen else EvTextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }

        // Gear ab tabs jaisa hi dikhta hai \u2014 wahi 14dp corner, wahi border,
        // wahi hara rang. Pehle ye akela sletee gol button tha aur baaki screen
        // se alag hi lagta tha.
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EvGreen.copy(alpha = 0.10f))
                .border(BorderStroke(1.5.dp, EvGreen), RoundedCornerShape(14.dp))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\u2699", color = EvGreen, fontSize = 22.sp)
        }
    }
}

/** Scroll hone wali tab strip. */
@Composable
fun HudTabs(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tabs, key = { it }) { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) EvGreen.copy(alpha = 0.10f) else EvSurface)
                    .border(
                        BorderStroke(1.5.dp, if (active) EvGreen else EvOutline),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(
                    text = tab,
                    color = if (active) EvGreen else EvTextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

/** CORE / MIC / API KEY wale teen chhote status boxes. */
@Composable
fun HudStatusRow(
    micOn: Boolean,
    coreActive: Boolean,
    apiKeySet: Boolean,
    onMicClick: () -> Unit,
    onApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusBox(
            title = "CORE",
            value = if (coreActive) "ACTIVE" else "IDLE",
            highlighted = coreActive,
            modifier = Modifier.weight(1f),
        )
        StatusBox(
            title = "MIC",
            value = if (micOn) "ON" else "OFF",
            highlighted = micOn,
            onClick = onMicClick,
            modifier = Modifier.weight(1f),
        )
        StatusBox(
            title = "API KEY",
            value = if (apiKeySet) "READY" else "SET KEY",
            highlighted = apiKeySet,
            onClick = onApiKeyClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusBox(
    title: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(EvSurfaceHigh)

    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            color = EvTextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = value,
            color = if (highlighted) EvGreen else EvTextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Neeche ke teen buttons ke icon. */
private enum class HudIcon { CAMERA, CLOSE, MIC }

/** Camera / stop / mic wala neeche ka pill. */
@Composable
fun HudActionBar(
    busy: Boolean,
    listening: Boolean,
    onCamera: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(EvSurfaceHigh)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(icon = HudIcon.CAMERA, tint = EvGreen, onClick = onCamera)

        // Pehle ye tabhi laal hota tha jab kuch chal raha ho, warna sletee \u2014
        // design me hamesha laal hai, aur stop dabana kabhi nuksaan nahi karta.
        CircleAction(icon = HudIcon.CLOSE, tint = EvRed, onClick = onStop)

        CircleAction(icon = HudIcon.MIC, tint = EvGreen, onClick = onMic)
    }
}

@Composable
private fun CircleAction(
    icon: HudIcon,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = if (enabled) tint else EvTextMuted.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .border(BorderStroke(2.dp, color), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icons Canvas pe bane hain. material-icons-extended add karne se APK
        // me kai MB jud jate, sirf do icon ke liye.
        Canvas(modifier = Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val line = w * 0.09f

            when (icon) {
                HudIcon.CAMERA -> {
                    // Upar ka chhota ubhaar
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.28f, h * 0.10f),
                        size = Size(w * 0.30f, h * 0.18f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                        style = Stroke(width = line),
                    )
                    // Body
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, h * 0.24f),
                        size = Size(w, h * 0.58f),
                        cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                        style = Stroke(width = line),
                    )
                    // Lens
                    drawCircle(
                        color = color,
                        radius = w * 0.16f,
                        center = Offset(w / 2f, h * 0.53f),
                        style = Stroke(width = line),
                    )
                }

                HudIcon.CLOSE -> {
                    drawLine(
                        color = color,
                        start = Offset(w * 0.24f, h * 0.24f),
                        end = Offset(w * 0.76f, h * 0.76f),
                        strokeWidth = line * 1.15f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(w * 0.76f, h * 0.24f),
                        end = Offset(w * 0.24f, h * 0.76f),
                        strokeWidth = line * 1.15f,
                        cap = StrokeCap.Round,
                    )
                }

                HudIcon.MIC -> {
                    // Capsule
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.34f, h * 0.06f),
                        size = Size(w * 0.32f, h * 0.46f),
                        cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                        style = Stroke(width = line),
                    )
                    // Neeche ka arc
                    drawArc(
                        color = color,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.18f, h * 0.28f),
                        size = Size(w * 0.64f, h * 0.46f),
                        style = Stroke(width = line, cap = StrokeCap.Round),
                    )
                    // Danda aur base
                    drawLine(
                        color = color,
                        start = Offset(w / 2f, h * 0.74f),
                        end = Offset(w / 2f, h * 0.88f),
                        strokeWidth = line,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(w * 0.30f, h * 0.92f),
                        end = Offset(w * 0.70f, h * 0.92f),
                        strokeWidth = line,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** HUD style tile \u2014 apps, tools sab isi me dikhte hain. */
@Composable
fun HudTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EvSurfaceHigh)
            .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = label.uppercase(),
                color = EvTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (icon != null) 8.dp else 0.dp),
            )
        }
    }
}

/** Chhota sa section ka naam \u2014 "// APPS" jaisa terminal look. */
@Composable
fun HudSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = EvGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}
