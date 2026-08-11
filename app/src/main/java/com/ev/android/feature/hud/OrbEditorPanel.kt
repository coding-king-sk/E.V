package com.ev.android.feature.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary
import kotlin.math.roundToInt

/**
 * Orb pe double tap karne se khulne wala poora page.
 *
 * Upar asli orb dikhta hai (koi alag preview nahi \u2014 wahi component hai), aur
 * neeche har cheez ka control. Har badlav turant dikhta hai aur turant phone
 * me save ho jata hai; "Save" dabana yaad rakhna na pade.
 */
@Composable
fun OrbEditorPanel(
    style: OrbStyle,
    onChange: (OrbStyle) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hex box me jo likha hai wo alag se yaad rakhna padta hai, warna aadha
    // type karte hi (jab tak rang bana nahi) box khud ko mita deta.
    var hex by remember { mutableStateOf(toHex(style.colorArgb)) }

    EvPanel(title = "Orb design", onClose = onClose, modifier = modifier) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            EvOrb(
                listening = false,
                busy = false,
                dimmed = false,
                style = style,
                modifier = Modifier.size(if (style.sizeDp > 200) 200.dp else style.sizeDp.dp),
            )
        }

        Text(
            text = "Har badlav turant save hota hai",
            color = EvTextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        EvSectionHeader("Shakl")

        EvCard {
            EvCardSubtitle(
                "Globe = dots wala ghoomta gola. Ring = sirf beech wali lehrati line. " +
                    "Ball = ek solid chamakta gola."
            )

            Row(modifier = Modifier.padding(top = 12.dp)) {
                ShapeChip("Globe", style.shape == OrbShape.GLOBE) {
                    onChange(style.copy(shape = OrbShape.GLOBE))
                }
                ShapeChip("Ring", style.shape == OrbShape.RING) {
                    onChange(style.copy(shape = OrbShape.RING))
                }
                ShapeChip("Ball", style.shape == OrbShape.BALL) {
                    onChange(style.copy(shape = OrbShape.BALL))
                }
            }
        }

        EvSectionHeader("Rang")

        EvCard {
            EvCardSubtitle("Poore orb ka rang. Chandi wala reference design hai.")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                OrbStyleStore.PRESET_COLORS.forEach { argb ->
                    val selected = argb == style.colorArgb
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) EvGreen else EvOutline,
                                shape = CircleShape,
                            )
                            .clickable {
                                hex = toHex(argb)
                                onChange(style.copy(colorArgb = argb))
                            },
                    )
                }
            }
        }

        EvCard {
            EvCardTitle("Apna rang (hex)")
            EvCardSubtitle("Jaise #00E5FF ya #FF00E5FF. 6 aur 8 digit dono chalte hain.")

            OutlinedTextField(
                value = hex,
                onValueChange = { typed ->
                    hex = typed
                    val parsed = parseHex(typed)
                    if (parsed != null) onChange(style.copy(colorArgb = parsed))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("#00E5FF", color = EvTextMuted, fontSize = 14.sp) },
                colors = evFieldColors(),
            )

            Text(
                text = if (parseHex(hex) == null) "Abhi ye hex sahi nahi hai"
                else "Rang lag gaya",
                color = if (parseHex(hex) == null) EvTextMuted else EvGreen,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        EvSectionHeader("Size")

        OrbSlider(
            label = "Size",
            value = style.sizeDp.toFloat(),
            range = 140f..340f,
            valueText = style.sizeDp.toString() + " dp",
            onChange = { onChange(style.copy(sizeDp = it.roundToInt())) },
        )

        OrbSlider(
            label = "Dots ki lines",
            value = style.rows.toFloat(),
            range = 14f..76f,
            valueText = style.rows.toString(),
            onChange = { onChange(style.copy(rows = it.roundToInt())) },
        )

        OrbSlider(
            label = "Ek line me dots",
            value = style.density.toFloat(),
            range = 18f..110f,
            valueText = style.density.toString(),
            onChange = { onChange(style.copy(density = it.roundToInt())) },
        )

        OrbSlider(
            label = "Dot ka size",
            value = style.dotScale,
            range = 0.5f..2.5f,
            valueText = format1(style.dotScale) + "x",
            onChange = { onChange(style.copy(dotScale = it)) },
        )

        OrbSlider(
            label = "Jhukav",
            value = style.tiltDegrees,
            range = 0f..45f,
            valueText = style.tiltDegrees.roundToInt().toString() + "\u00B0",
            onChange = { onChange(style.copy(tiltDegrees = it)) },
        )

        EvSectionHeader("Harkat")

        OrbSlider(
            label = "Ek chakkar",
            value = style.spinSeconds.toFloat(),
            range = 4f..60f,
            valueText = style.spinSeconds.toString() + " sec",
            onChange = { onChange(style.copy(spinSeconds = it.roundToInt())) },
        )

        OrbSlider(
            label = "Lehar ki unchai",
            value = style.waveHeight,
            range = 0f..0.45f,
            valueText = (style.waveHeight * 100f).roundToInt().toString() + "%",
            onChange = { onChange(style.copy(waveHeight = it)) },
        )

        OrbSlider(
            label = "Kitni lehrein",
            value = style.waveCount.toFloat(),
            range = 1f..8f,
            valueText = style.waveCount.toString(),
            onChange = { onChange(style.copy(waveCount = it.roundToInt())) },
        )

        OrbSlider(
            label = "Peeche ki chamak",
            value = style.glow,
            range = 0f..2.5f,
            valueText = format1(style.glow) + "x",
            onChange = { onChange(style.copy(glow = it)) },
        )

        EvSectionHeader("On / off")

        EvToggleCard(
            title = "Saans",
            subtitle = "Orb dheere-dheere bada-chhota hota rehta hai.",
            checked = style.pulse,
            onChange = { onChange(style.copy(pulse = it)) },
        )

        EvToggleCard(
            title = "Beech wali line",
            subtitle = "Wahi chamakti hui equator line, jo bolte waqt lehrati hai. " +
                "Isse off karoge to wave bhi nahi dikhegi.",
            checked = style.equatorLine,
            onChange = { onChange(style.copy(equatorLine = it)) },
        )

        EvToggleCard(
            title = "Upar-neeche ke ring",
            subtitle = "Do halke ellipse, jinse globe 3D lagta hai.",
            checked = style.poleRings,
            onChange = { onChange(style.copy(poleRings = it)) },
        )

        EvCard {
            EvCardTitle("Wapas pehle jaisa")
            EvCardSubtitle("Saari settings default pe le aata hai.")
            Row(modifier = Modifier.padding(top = 4.dp)) {
                EvDialogButton(
                    label = "Reset karo",
                    onClick = {
                        hex = toHex(OrbStyleStore.DEFAULT.colorArgb)
                        onChange(OrbStyleStore.DEFAULT)
                    },
                )
            }
        }

        EvGap(32)
    }
}

@Composable
private fun ShapeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EvSurfaceHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) EvGreen else EvOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = if (selected) EvGreen else EvTextPrimary,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun OrbSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    EvCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvCardTitle(label, modifier = Modifier.weight(1f))
            Text(text = valueText, color = EvTextMuted, fontSize = 13.sp)
        }

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = EvGreen,
                activeTrackColor = EvGreen,
                inactiveTrackColor = EvOutline,
            ),
        )
    }
}

/** "#FF00E5FF" \u2014 aisa hi text hex box me dikhta hai. */
private fun toHex(argb: Long): String {
    val raw = java.lang.Long.toHexString(argb)
    val padded = ("00000000" + raw).takeLast(8)
    return "#" + padded.uppercase()
}

/** "#00E5FF" ya "#FF00E5FF" \u2014 dono chalte hain. Galat ho to null. */
private fun parseHex(raw: String): Long? {
    var clean = raw.trim().removePrefix("#")
    if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2)
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    // 6 digit me alpha nahi hota \u2014 poora opaque maan lo.
    return if (clean.length == 6) value or 0xFF000000L else value
}

/** "1.4" \u2014 String.format se bacha hai kyunki wo locale pe alag chalta hai. */
private fun format1(value: Float): String {
    val rounded = (value * 10f).roundToInt()
    return (rounded / 10).toString() + "." + (rounded % 10).toString()
}
