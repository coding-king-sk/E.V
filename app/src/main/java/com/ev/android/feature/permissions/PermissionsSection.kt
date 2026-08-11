package com.ev.android.feature.permissions

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.hud.EvActionCard
import com.ev.android.feature.hud.EvDialogButton

/**
 * Saari permissions cards me.
 *
 * Android ki settings se wapas aane par status apne aap refresh nahi hota,
 * isliye "Dobara check karo" button hai \u2014 wahi [refresh] badhata hai aur poori
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

        // Teeno buttons ek hi Row me the to jagah kam padne par unka text kai
        // lines me tut jata tha \u2014 screen pe wo bada khali hissa lagta tha. Ab
        // "Sab allow karo" apni poori chaudai leta hai aur baaki do neeche.
        if (missing.isNotEmpty()) {
            EvDialogButton(
                label = "Sab allow karo",
                onClick = { launcher.launch(missing.toTypedArray()) },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EvDialogButton(
                label = "Dobara check",
                onClick = { refresh++ },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            EvDialogButton(
                label = "App settings",
                onClick = { AppPermissions.openAppSettings(context) },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
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
            subtitle = "Sirf \"brightness 60% karo\" jaise command ke liye. Baaki sab " +
                "iske bina bhi chalta hai.",
            done = writeOk,
            actionLabel = "Open",
            onClick = { AppPermissions.openWriteSettings(context) },
        )
    }
}
