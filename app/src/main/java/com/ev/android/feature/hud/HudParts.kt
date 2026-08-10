package com.ev.android.feature.hud

import androidx.compose.foundation.BorderStroke
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

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(EvSurfaceHigh)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\u2699", color = EvTextMuted, fontSize = 20.sp)
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
        CircleAction(symbol = "\u25C9", tint = EvGreen, onClick = onCamera)
        CircleAction(
            symbol = "\u2715",
            tint = EvRed,
            enabled = busy || listening,
            onClick = onStop,
        )
        CircleAction(symbol = "\u2B24", tint = EvGreen, onClick = onMic)
    }
}

@Composable
private fun CircleAction(
    symbol: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = if (enabled) tint else EvTextMuted.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.08f))
            .border(BorderStroke(2.dp, color), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = color, fontSize = 22.sp)
    }
}

/** HUD style tile — apps, tools sab isi me dikhte hain. */
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

/** Chhota sa section ka naam — "// APPS" jaisa terminal look. */
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
