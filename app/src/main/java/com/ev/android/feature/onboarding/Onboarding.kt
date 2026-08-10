@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ev.android.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ev.android.feature.accessibility.AccessibilityHelper

/**
 * Pehli baar wala setup.
 *
 * Pehle user ko khud Android settings me jaake accessibility aur battery
 * optimization dhoondhni padti thi — zyadatar log wahin atak jaate the aur
 * hands-free "kaam nahi karta" lagta tha. Ab ek baar sab ek jagah se ho jata
 * hai.
 */
object Onboarding {

    private const val PREFS = "ev_onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }

    /** Settings se dobara setup chalane ke liye. */
    fun reset(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, false).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Settings se wapas aate hi status dobara check karna hota hai.
    var refresh by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    fun granted(permissions: List<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val micPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val reachPermissions = remember {
        listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
        )
    }

    val micDone = remember(refresh) { granted(micPermissions) }
    val reachDone = remember(refresh) { granted(reachPermissions) }
    val accessibilityDone = remember(refresh) { AccessibilityHelper.isEnabled(context) }
    val batteryDone = remember(refresh) {
        val power = context.getSystemService(PowerManager::class.java)
        power?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val doneCount = listOf(micDone, reachDone, accessibilityDone, batteryDone).count { it }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "\uD83D\uDC4B E.V me swagat hai",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Chaar chhote step — ek baar kar lo, phir hamesha ke liye " +
                    "bas \"Hey E.V\" bolna hai.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            LinearProgressIndicator(
                progress = { doneCount / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            Text(
                text = doneCount.toString() + " / 4 ho gaya",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
            )

            StepCard(
                number = 1,
                title = "Mic aur notification",
                why = "Bina mic ke E.V sun hi nahi sakta. Notification isliye " +
                    "chahiye kyunki Android background me mic tabhi chalne deta hai " +
                    "jab notification dikhe — aapko hamesha pata rahega ki mic on hai.",
                done = micDone,
                required = true,
                actionLabel = "Permission do",
                onAction = { permissionLauncher.launch(micPermissions.toTypedArray()) },
            )

            StepCard(
                number = 2,
                title = "Contacts, SMS aur call",
                why = "\"Rehan ko call lagao\" jaise commands ke liye. Na do to bhi " +
                    "E.V chalega — bas call/SMS wale command dialer khol denge, " +
                    "khud send nahi karenge.",
                done = reachDone,
                required = false,
                actionLabel = "Permission do",
                onAction = { permissionLauncher.launch(reachPermissions.toTypedArray()) },
            )

            StepCard(
                number = 3,
                title = "Auto-send (Accessibility)",
                why = "Isse E.V khud WhatsApp ka Send button daba pata hai, aur " +
                    "screenshot, screen lock, back/home bhi isi se chalte hain. " +
                    "List me 'E.V auto-send' dhoondh ke on kar do.",
                done = accessibilityDone,
                required = false,
                actionLabel = "Settings kholo",
                onAction = {
                    AccessibilityHelper.openSettings(context)
                    refresh++
                },
            )

            StepCard(
                number = 4,
                title = "Battery optimization band karo",
                why = "Ye sabse zaroori step hai. Battery optimization on rehne par " +
                    "Android kuch der baad hands-free service ko chupchap band kar " +
                    "deta hai — phir lagta hai ki E.V kharab ho gaya. List me E.V " +
                    "dhoondh ke 'Don't optimize' / 'Allow' kar do.",
                done = batteryDone,
                required = true,
                actionLabel = "Settings kholo",
                onAction = {
                    runCatching {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                },
            )

            Button(
                onClick = {
                    Onboarding.markDone(context)
                    onFinished()
                },
                enabled = micDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(if (doneCount == 4) "Sab ho gaya — chalu karo" else "Aage badho")
            }

            if (!micDone) {
                Text(
                    text = "Mic ki permission ke bina aage nahi badh sakte.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            TextButton(
                onClick = { refresh++ },
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Text("Status dobara check karo")
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    why: String,
    done: Boolean,
    required: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (done) "\u2705" else number.toString() + "️\u20E3",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!required) {
                    Text(
                        text = "optional",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (!done) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
