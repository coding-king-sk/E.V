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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvRed
import com.ev.android.ui.theme.EvSurfaceHigh
import com.ev.android.ui.theme.EvTextMuted
import com.ev.android.ui.theme.EvTextPrimary

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EvBlack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Wahi "E" badge jo main screen pe hai — pehli screen se hi app
            // apni pehchaan wali lagti hai.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.5.dp, EvGreen), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "E",
                        color = EvGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.size(14.dp))

                Text(
                    text = "E.V",
                    color = EvTextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }

            Text(
                text = "SETUP",
                color = EvGreen,
                fontSize = 13.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp),
            )

            Text(
                text = "Chaar chhote step — ek baar kar lo, phir hamesha ke liye " +
                    "bas \"Hey E.V\" bolna hai.",
                color = EvTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            LinearProgressIndicator(
                progress = { doneCount / 4f },
                color = EvGreen,
                trackColor = EvSurfaceHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            )

            Text(
                text = doneCount.toString() + " / 4 HO GAYA",
                color = EvTextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            StepCard(
                number = 1,
                title = "Mic aur notification",
                why = "Bina mic ke E.V sun hi nahi sakta. Notification isliye " +
                    "chahiye kyunki Android background me mic tabhi chalne deta hai " +
                    "jab notification dikhe — aapko hamesha pata rahega ki mic on hai.",
                done = micDone,
                required = true,
                actionLabel = "PERMISSION DO",
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
                actionLabel = "PERMISSION DO",
                onAction = { permissionLauncher.launch(reachPermissions.toTypedArray()) },
            )

            StepCard(
                number = 3,
                title = "Auto-send aur typing (Accessibility)",
                why = "Isse E.V khud WhatsApp ka Send button daba pata hai, dusri app " +
                    "ke message box me type kar pata hai, aur screenshot, screen lock, " +
                    "back/home bhi isi se chalte hain. List me 'E.V auto-send' " +
                    "dhoondh ke on kar do.",
                done = accessibilityDone,
                required = false,
                actionLabel = "SETTINGS KHOLO",
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
                actionLabel = "SETTINGS KHOLO",
                onAction = {
                    runCatching {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (micDone) EvGreen else EvSurfaceHigh)
                    .border(
                        BorderStroke(1.dp, if (micDone) EvGreen else EvOutline),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = micDone) {
                        Onboarding.markDone(context)
                        onFinished()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (doneCount == 4) "SAB HO GAYA \u2014 CHALU KARO" else "AAGE BADHO",
                    color = if (micDone) EvBlack else EvTextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                )
            }

            if (!micDone) {
                Text(
                    text = "Mic ki permission ke bina aage nahi badh sakte.",
                    color = EvRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                text = "STATUS DOBARA CHECK KARO",
                color = EvTextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { refresh++ }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(EvSurfaceHigh)
            .border(
                BorderStroke(1.dp, if (done) EvGreen else EvOutline),
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(
                        BorderStroke(1.dp, if (done) EvGreen else EvOutline),
                        RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (done) "\u2713" else number.toString(),
                    color = if (done) EvGreen else EvTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = title.uppercase(),
                color = if (done) EvGreen else EvTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )

            if (!required) {
                Text(text = "OPTIONAL", color = EvTextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        Text(
            text = why,
            color = EvTextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        if (!done) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, EvGreen), RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = EvGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
