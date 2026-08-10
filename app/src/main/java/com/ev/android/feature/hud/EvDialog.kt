@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ev.android.feature.hud

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvSurface
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary

/**
 * App ke apne look wala dialog.
 *
 * Material ka default dialog safed/lilac aata hai, jo kaale HUD ke beech me
 * bilkul alag thalag lagta tha. Sab dialogs yahi wrapper use karte hain taaki
 * rang ek jagah se badle ja sakein.
 */
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
        title = {
            Text(
                text = title.uppercase(),
                color = EvGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        },
        text = { Column(content = content) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = EvGreen),
            ) {
                Text(text = confirmLabel, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = EvTextMuted),
            ) {
                Text(text = dismissLabel, letterSpacing = 1.sp)
            }
        },
    )
}

/** Dialog ke andar section ka naam. */
@Composable
fun EvDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = EvGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

/** Aam padhne wali line. */
@Composable
fun EvDialogText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = EvTextPrimary, fontSize = 14.sp, modifier = modifier)
}

/** Chhoti samjhane wali line. */
@Composable
fun EvDialogHint(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = EvTextMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

/** Dialog ke andar wala text button. */
@Composable
fun EvDialogButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = EvGreen,
            disabledContentColor = EvTextMuted.copy(alpha = 0.5f),
        ),
        modifier = modifier,
    ) {
        Text(text = label, fontSize = 13.sp, letterSpacing = 0.5.sp)
    }
}

/** Theme ke rang wala switch. */
@Composable
fun EvSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = EvBlack,
            checkedTrackColor = EvGreen,
            checkedBorderColor = EvGreen,
            uncheckedThumbColor = EvTextMuted,
            uncheckedTrackColor = EvSurfaceHigh,
            uncheckedBorderColor = EvOutline,
        ),
    )
}

/** Theme ke rang wale text field. */
@Composable
fun evFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EvTextPrimary,
    unfocusedTextColor = EvTextPrimary,
    focusedBorderColor = EvGreen,
    unfocusedBorderColor = EvOutline,
    cursorColor = EvGreen,
    focusedContainerColor = EvSurfaceHigh,
    unfocusedContainerColor = EvSurfaceHigh,
    focusedLabelColor = EvGreen,
    unfocusedLabelColor = EvTextMuted,
    focusedPlaceholderColor = EvTextMuted,
    unfocusedPlaceholderColor = EvTextMuted,
)
