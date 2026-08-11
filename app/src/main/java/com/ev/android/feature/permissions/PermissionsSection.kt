package com.ev.android.feature.permissions

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.hud.EvActionCard
import com.ev.android.feature.hud.EvDialogButton

/**
 * Saari permissions cards me.
 *
 * Android ki settings se wapas aane par status apne aap refresh nahi hota,
 * isliye "Dobara check karo" button hai — wahi [refresh] badhata hai aur poori
 * list dobara padhi jaati hai.
 */
@Composable
fun PermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val items = remember(refresh) {
        AppPermissions.applicable().map { it to AppPermissions.isGranted(context, it.permission) }
    }
    val missing = items.filterNot { it.second }.map { it.first.permission }

    val accessibilityOn = remember(refresh) { AccessibilityHelper.isEnabled(context) }
    val batteryOk = remember(refresh) { AppPermissions.batteryUnrestricted(context) }
    val alarmsOk = remember(refresh) { AppPermissions.exactAlarmAllowed(context) }
    val writeOk = remember(refresh) { AppPermissions.canWriteSettings(context) }

    Column(modifier = modifier.fillMaxWidth()) {

        Row(modifier = Modifier.fillMaxWidth()) {
            if (missing.isNotEmpty()) {
                EvDialogButton(
                    label = "Sab allow karo",
                    onClick = { launcher.launch(missing.toTypedArray()) },
                )
            }
            EvDialogButton(label = "Dobara check karo", onClick = { refresh++ })
            EvDialogButton(
                label = "App settings",
                onClick = { AppPermissions.openAppSettings(context) },
            )
        }

        items.forEach { (permission, granted) ->
            EvActionCard(
                title = permission.label,
                subtitle = permission.why,
                done = granted,
                actionLabel = "Allow",
                onClick = { launcher.launch(arrayOf(permission.permission)) },
            )
        }

        EvActionCard(
            title = "Accessibility service",
            subtitle = "Typing, WhatsApp auto-send aur YouTube ka pehla video isi se " +
                "chalte hain.",
            done = accessibilityOn,
            actionLabel = "Open",
            onClick = { AccessibilityHelper.openSettings(context) },
        )

        EvActionCard(
            title = "Battery band na karo",
            subtitle = "Warna Android kuch der baad \"Hey E.V\" wali service ko maar " +
                "deta hai aur wake word chup ho jata hai.",
            done = batteryOk,
            actionLabel = "Open",
            onClick = { AppPermissions.openBatterySettings(context) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            EvActionCard(
                title = "Alarms & reminders",
                subtitle = "Reminder theek usi waqt bajne ke liye. Na do to Android " +
                    "apni marzi se der kar deta hai.",
                done = alarmsOk,
                actionLabel = "Open",
                onClick = { AppPermissions.openExactAlarmSettings(context) },
            )
        }

        EvActionCard(
            title = "System settings badalna",
            subtitle = "Sirf \"brightness badhao\" jaise command ke liye. Baaki sab " +
                "iske bina bhi chalta hai.",
            done = writeOk,
            actionLabel = "Open",
            onClick = { AppPermissions.openWriteSettings(context) },
        )
    }
}
