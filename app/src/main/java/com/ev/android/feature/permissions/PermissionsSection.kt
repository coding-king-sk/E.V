package com.ev.android.feature.permissions

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.hud.EvDialogButton
import com.ev.android.feature.hud.EvDialogHint
import com.ev.android.feature.hud.EvDialogTitle
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvRed
import com.ev.android.ui.theme.EvTextPrimary

/**
 * Settings ke andar dikhne wali permission list.
 *
 * Har row apna asli status dikhati hai, banaya hua nahi \u2014 isliye Android
 * settings me jaake permission hata dene par yahan bhi turant \u2715 ho jayega
 * ("Dobara check karo" dabane par).
 */
@Composable
fun PermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Android settings se wapas aane ke baad status khud refresh nahi hota,
    // isliye ye counter. Ise badalne se poora section dobara ginta hai.
    var refresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    val items = remember { AppPermissions.applicable() }
    val missing = remember(refresh) { AppPermissions.missing(context) }

    Column(modifier = modifier.fillMaxWidth()) {
        EvDialogTitle("Permissions")

        EvDialogHint(
            text = if (missing.isEmpty()) {
                "Saari permissions mil chuki hain."
            } else {
                "${missing.size} permission abhi baaki hain."
            },
            modifier = Modifier.padding(top = 4.dp),
        )

        if (missing.isNotEmpty()) {
            EvDialogButton(
                label = "Sab allow karo",
                onClick = { permissionLauncher.launch(missing.toTypedArray()) },
            )
        }

        items.forEach { item ->
            val granted = remember(refresh, item.permission) {
                AppPermissions.isGranted(context, item.permission)
            }

            PermissionRow(
                label = item.label,
                why = item.why,
                granted = granted,
                actionLabel = "Allow karo",
                onAction = { permissionLauncher.launch(arrayOf(item.permission)) },
            )
        }

        // ---- Ye normal dialog se nahi milti, Android ki apni screen khulti hai

        PermissionRow(
            label = "Battery optimization band",
            why = "Sabse zaroori. On rehne par Android kuch der baad hands-free " +
                "service chupchap band kar deta hai \u2014 phir lagta hai E.V kharab " +
                "ho gaya.",
            granted = remember(refresh) { AppPermissions.batteryUnrestricted(context) },
            actionLabel = "Settings kholo",
            onAction = {
                AppPermissions.openBatterySettings(context)
                refresh++
            },
        )

        PermissionRow(
            label = "Accessibility (typing + auto-send)",
            why = "Isse E.V WhatsApp ka send button daba pata hai aur dusri app ke " +
                "message box me type kar pata hai. Band ho to \"type karo\" wale " +
                "command kaam nahi karenge.",
            granted = remember(refresh) { AccessibilityHelper.isEnabled(context) },
            actionLabel = "Settings kholo",
            onAction = {
                AccessibilityHelper.openSettings(context)
                refresh++
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(
                label = "Alarms & reminders",
                why = "Reminder theek waqt pe bajne ke liye. Na mile to reminder " +
                    "aayega to sahi, par kuch minute late ho sakta hai.",
                granted = remember(refresh) { AppPermissions.exactAlarmAllowed(context) },
                actionLabel = "Settings kholo",
                onAction = {
                    AppPermissions.openExactAlarmSettings(context)
                    refresh++
                },
            )
        }

        PermissionRow(
            label = "System settings badalna",
            why = "Brightness jaise commands ke liye. Na mile to sirf wahi command " +
                "nahi chalenge.",
            granted = remember(refresh) { AppPermissions.canWriteSettings(context) },
            actionLabel = "Settings kholo",
            onAction = {
                AppPermissions.openWriteSettings(context)
                refresh++
            },
        )

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EvDialogButton(label = "Dobara check karo", onClick = { refresh++ })
            EvDialogButton(
                label = "App settings",
                onClick = { AppPermissions.openAppSettings(context) },
            )
        }

        EvDialogHint(
            text = "Agar allow karne pe koi dialog hi na aaye, to matlab aapne pehle " +
                "\"Don't ask again\" daba diya hai \u2014 phir \"App settings\" se hi " +
                "deni padegi.",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (granted) "\u2713 " else "\u2715 ") + label,
                color = if (granted) EvGreen else EvTextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )

            if (!granted) {
                EvDialogButton(label = actionLabel, onClick = onAction)
            }
        }

        if (!granted) {
            Text(text = why, color = EvRed.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 17.sp)
        } else {
            EvDialogHint(text = why)
        }
    }
}
