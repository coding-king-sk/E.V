package com.ev.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.feature.hud.EvDialog
import com.ev.android.feature.hud.EvDialogButton
import com.ev.android.feature.hud.EvDialogHint
import com.ev.android.feature.hud.EvDialogTitle
import com.ev.android.feature.hud.EvSwitch
import com.ev.android.feature.hud.evFieldColors
import com.ev.android.feature.permissions.PermissionsSection
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvRed
import com.ev.android.ui.theme.EvTextPrimary
import kotlinx.coroutines.launch

/**
 * App ki saari settings ek jagah.
 *
 * API key yahin paste hoti hai aur phone me hi rehti hai \u2014 isi wajah se key
 * repo me daalne ki zaroorat nahi padti.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(EvSettings.apiKey(context)) }
    var aiOn by remember { mutableStateOf(EvSettings.aiEnabled(context)) }
    var personalOn by remember { mutableStateOf(EvSettings.sendPersonalToAi(context)) }
    var whisperOn by remember { mutableStateOf(EvSettings.whisperStt(context)) }

    var offlineWake by remember { mutableStateOf(EvSettings.offlineWakeWord(context)) }
    var modelUrl by remember { mutableStateOf(EvSettings.wakeWordModelUrl(context)) }
    var keywords by remember { mutableStateOf(EvSettings.wakeWordKeywords(context)) }
    var modelReady by remember { mutableStateOf(WakeWordModel.isInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val libraryOk = remember { SherpaWakeWord.isLibraryAvailable() }

    EvDialog(
        title = "Settings",
        onDismiss = onDismiss,
        onConfirm = {
            EvSettings.setApiKey(context, key)
            EvSettings.setAiEnabled(context, aiOn)
            EvSettings.setSendPersonalToAi(context, personalOn)
            EvSettings.setWhisperStt(context, whisperOn)
            EvSettings.setOfflineWakeWord(context, offlineWake)
            EvSettings.setWakeWordModelUrl(context, modelUrl)
            EvSettings.setWakeWordKeywords(context, keywords)
            onSaved(
                if (EvSettings.aiEnabled(context)) "AI fallback chalu hai"
                else "AI fallback band hai"
            )
            onDismiss()
        },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            // Onboarding hat gaya hai, isliye permissions ka poora hisaab ab
            // yahan sabse upar hai \u2014 typing/auto-send wali accessibility bhi
            // isi list me hai, home screen pe ab wo button nahi.
            PermissionsSection()

            Divider()

            EvDialogTitle("AI (Groq)")

            EvDialogHint(
                text = "Jo command E.V khud na samajh paye, wahi AI ko bheja jayega. " +
                    "Baaki sab pehle jaisa offline hi chalega.",
                modifier = Modifier.padding(top = 4.dp),
            )

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                label = { Text("Groq API key") },
                placeholder = { Text("gsk_...") },
                colors = evFieldColors(),
            )

            EvDialogHint(
                text = "console.groq.com/keys se free milti hai. Key sirf is phone " +
                    "me save hoti hai.",
                modifier = Modifier.padding(top = 4.dp),
            )

            SettingRow(
                label = "AI fallback on",
                checked = aiOn,
                onChange = { aiOn = it },
            )

            SettingRow(
                label = "Message/call ka text bhi AI ko bhejo",
                checked = personalOn,
                onChange = { personalOn = it },
            )

            EvDialogHint(
                text = "Ye off rakhna behtar hai. In commands me contact ka naam aur " +
                    "message ka text hota hai, aur free tier pe providers prompts ko " +
                    "training ke liye use kar sakte hain.",
                modifier = Modifier.padding(top = 4.dp),
            )

            Divider()

            EvDialogTitle("Sunna (speech to text)")

            SettingRow(
                label = "Mic pe Whisper (Groq) use karo",
                checked = whisperOn,
                onChange = { whisperOn = it },
            )

            EvDialogHint(
                text = "Whisper Hinglish kaafi behtar samajhta hai. Lekin isme aapki " +
                    "awaaz Groq ke server pe jaati hai aur internet chahiye \u2014 " +
                    "bina internet ke mic Google recognizer pe hi chalega. Ye sirf " +
                    "mic button pe lagta hai, hands-free \"Hey E.V\" pe nahi.",
                modifier = Modifier.padding(top = 4.dp),
            )

            Divider()

            EvDialogTitle("Offline wake word")

            EvDialogHint(
                text = "Wake word phone ke andar hi pakda jayega, internet ke bina " +
                    "aur bina awaaz kahin bheje. Iske liye ek ~4 MB ka model " +
                    "download karna padta hai.",
                modifier = Modifier.padding(top = 4.dp),
            )

            if (!libraryOk) {
                Text(
                    text = "\u26A0 Native library is phone pe nahi mili. Offline wake " +
                        "word kaam nahi karega \u2014 app Google recognizer pe chalti " +
                        "rahegi.",
                    color = EvRed,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SettingRow(
                label = "Offline wake word use karo",
                checked = offlineWake,
                onChange = { offlineWake = it },
            )

            EvDialogHint(
                text = when {
                    downloading -> status.ifBlank { "Download ho raha hai\u2026" }
                    status.isNotBlank() -> status
                    modelReady -> "Model ready hai"
                    else -> "Model abhi download nahi hua"
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(modifier = Modifier.padding(top = 4.dp)) {
                EvDialogButton(
                    label = if (modelReady) "Dobara download" else "Model download karo",
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        status = "Download shuru\u2026"
                        val url = modelUrl.ifBlank { WakeWordModel.DEFAULT_URL }
                        scope.launch {
                            val result = WakeWordModel.download(
                                context = context,
                                url = url,
                                onProgress = { percent ->
                                    status = if (percent >= 0) {
                                        "Download $percent%"
                                    } else {
                                        "Download ho raha hai\u2026"
                                    }
                                },
                            )
                            downloading = false
                            modelReady = WakeWordModel.isInstalled(context)
                            status = result.fold(
                                onSuccess = { "Model ready hai" },
                                onFailure = { "Nahi hua: ${it.message}" },
                            )
                        }
                    },
                )

                if (modelReady) {
                    EvDialogButton(
                        label = "Hatao",
                        enabled = !downloading,
                        onClick = {
                            WakeWordModel.delete(context)
                            modelReady = false
                            status = "Model hata diya"
                        },
                    )
                }
            }

            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text("Keyword (model ke tokens me)") },
                placeholder = { Text("khaali = model ki apni list") },
                colors = evFieldColors(),
            )

            EvDialogHint(
                text = "Yahan seedha \"E.V\" likhne se kaam nahi chalega \u2014 keyword " +
                    "model ke tokens me likhna padta hai. Khaali chhod do to model " +
                    "ki apni keywords.txt chalegi.",
                modifier = Modifier.padding(top = 4.dp),
            )

            OutlinedTextField(
                value = modelUrl,
                onValueChange = { modelUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text("Model URL (optional)") },
                placeholder = { Text("khaali = default") },
                colors = evFieldColors(),
            )
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = EvOutline,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = EvTextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        EvSwitch(checked = checked, onChange = onChange)
    }
}
