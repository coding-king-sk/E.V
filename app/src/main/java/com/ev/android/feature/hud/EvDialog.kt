@file:OptIn(ExperimentalMaterial3Api::class)

package com.ev.android.feature.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvGreenDim
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvSurface
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary

/**
 * App ke HUD theme wale dialog aur cards.
 *
 * Ye sab ek jagah isliye hain ki Settings, API key aur permissions teeno ek
 * jaise dikhein \u2014 pehle har screen apna alag Material default use kar rahi thi
 * aur isi wajah se app ke andar do alag design dikh rahe the.
 */

/** Poori screen ka settings page \u2014 chhote dialog me itni cheezein nahi samati. */
@Composable
fun EvSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = EvBlack) {
            Column(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title.uppercase(),
                        color = EvGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.weight(1f),
                    )

                    if (onSave != null) {
                        EvDialogButton(label = "SAVE", onClick = onSave)
                    }
                    EvDialogButton(label = "CLOSE", onClick = onDismiss)
                }

                HorizontalDivider(color = EvOutline)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun EvDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "SAVE",
    dismissLabel: String = "CANCEL",
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = EvSurface,
        titleContentColor = EvGreen,
        textContentColor = EvTextPrimary,
        title = { EvDialogTitle(title) },
        text = { Column(content = content) },
        confirmButton = { EvDialogButton(label = confirmLabel, onClick = onConfirm) },
        dismissButton = { EvDialogButton(label = dismissLabel, onClick = onDismiss) },
    )
}

/** "MESSAGING", "PERMISSIONS" \u2014 cards ke upar wala chhota label. */
@Composable
fun EvSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = EvTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
    )
}

/** Ek settings card. On hone par border green ho jata hai. */
@Composable
fun EvCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(EvSurfaceHigh)
            .border(
                width = 1.dp,
                color = if (highlighted) EvGreen else EvOutline,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
fun EvToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvCard(modifier = modifier, highlighted = checked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                EvCardTitle(title)
                EvCardSubtitle(subtitle)
            }
            Spacer(modifier = Modifier.width(12.dp))
            EvSwitch(checked = checked, onChange = onChange)
        }
    }
}

/**
 * Wo card jiske liye Android ki apni screen kholni padti hai.
 * Mil chuki ho to sirf tick, warna [actionLabel] wala button.
 */
@Composable
fun EvActionCard(
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EvCard(modifier = modifier, highlighted = done) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                EvCardTitle(title)
                EvCardSubtitle(subtitle)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (done) {
                Text(
                    text = "\u2713",
                    color = EvGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = actionLabel.uppercase(),
                    color = EvGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun EvCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EvTextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun EvCardSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EvTextMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun EvDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = EvGreen,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

@Composable
fun EvDialogText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EvTextPrimary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = modifier,
    )
}

@Composable
fun EvDialogHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EvTextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = modifier,
    )
}

/**
 * HUD wala flat button.
 *
 * [maxLines] = 1 jaan-boojh ke fix hai: jagah kam padne par pehle iska text do
 * ya teen lines me tut jata tha, jisse screen pe bada khali hissa ban jata tha.
 * Ab chaudai kam ho to text chhota ho ke "\u2026" me chala jata hai, layout nahi
 * bigadta.
 */
@Composable
fun EvDialogButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label.uppercase(),
        color = if (enabled) EvGreen else EvTextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun EvSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = EvGreen,
            checkedTrackColor = EvGreenDim.copy(alpha = 0.35f),
            checkedBorderColor = EvGreen,
            uncheckedThumbColor = EvTextMuted,
            uncheckedTrackColor = EvSurface,
            uncheckedBorderColor = EvOutline,
        ),
    )
}

@Composable
fun evFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EvTextPrimary,
    unfocusedTextColor = EvTextPrimary,
    focusedBorderColor = EvGreen,
    unfocusedBorderColor = EvOutline,
    focusedLabelColor = EvGreen,
    unfocusedLabelColor = EvTextMuted,
    cursorColor = EvGreen,
    focusedPlaceholderColor = EvTextMuted,
    unfocusedPlaceholderColor = EvTextMuted,
)

/** Cards ke beech ki lakeer, jahan zaroorat ho. */
@Composable
fun EvDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(color = EvOutline, modifier = modifier.padding(vertical = 8.dp))
}

/** Kabhi kabhi cards ke beech thodi jagah chahiye hoti hai. */
@Composable
fun EvGap(height: Int = 8) {
    Spacer(modifier = Modifier.padding(vertical = (height / 2).dp))
}

@Suppress("unused")
private val unusedArrangement = Arrangement.Start
