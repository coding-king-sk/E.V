package com.ev.android.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.feature.hud.EvCard
import com.ev.android.feature.hud.EvCardSubtitle
import com.ev.android.feature.hud.EvCardTitle
import com.ev.android.feature.hud.EvDialogButton
import com.ev.android.feature.hud.EvGap
import com.ev.android.feature.hud.EvSectionHeader
import com.ev.android.feature.hud.EvSheet
import com.ev.android.feature.hud.EvToggleCard
import com.ev.android.feature.hud.evFieldColors
import com.ev.android.feature.permissions.PermissionsSection
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import com.ev.android.ui.theme.EvRed
import kotlinx.coroutines.launch

/**
 * App ki saari settings ek jagah, poori screen pe cards ke roop me.
 *
 * Groq API key ka field yahan jaan-boojh ke nahi hai — wo home screen ke
 * API KEY button me hai. Ek hi cheez do jagah rakhne se ye confusion hota tha
 * ki kaunsi wali asli hai.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    EvSheet(
        title = "Settings",
        onDismiss = onDismiss,
        onSave = {
            EvSettings.setAiEnabled(context, aiOn)
            EvSettings.setSendPersonalToAi(context, personalOn)
            EvSettings.setWhisperStt(context, whisperOn)
            EvSettings.setOfflineWakeWord(context, offlineWake)
            EvSettings.setWakeWordModelUrl(context, modelUrl)
            EvSettings.setWakeWordKeywords(context, keywords)
            onSaved(
                if (aiOn) "AI fallback chalu hai" else "AI fallback band hai"
            )
            onDismiss()
        },
    ) {

        // Onboarding hat gaya hai, isliye permissions ka poora hisaab ab yahan
        // sabse upar hai — typing/auto-send wali accessibility bhi isi list me.
        EvSectionHeader("Permissions")
        PermissionsSection()

        EvSectionHeader("AI")

        EvToggleCard(
            title = "AI fallback",
            subtitle = "Jo command E.V khud na samajh paye wahi Groq ko jata hai. " +
                "Key home screen ke API KEY button me daalni hai.",
            checked = aiOn,
            onChange = { aiOn = it },
        )

        EvToggleCard(
            title = "Message/call ka text bhi AI ko bhejo",
            subtitle = "Ye off rakhna behtar hai. In commands me contact ka naam aur " +
                "message ka text hota hai, aur free tier pe providers prompts ko " +
                "training me use kar sakte hain.",
            checked = personalOn,
            onChange = { personalOn = it },
        )

        EvSectionHeader("Sunna (speech to text)")

        EvToggleCard(
            title = "Mic pe Whisper (Groq)",
            subtitle = "Whisper Hinglish kaafi behtar samajhta hai, par aapki awaaz " +
                "Groq ke server pe jaati hai aur internet chahiye. Sirf mic button " +
                "pe lagta hai, \"Hey E.V\" pe nahi.",
            checked = whisperOn,
            onChange = { whisperOn = it },
        )

        EvSectionHeader("Offline wake word")

        if (!libraryOk) {
            EvCard {
                Text(
                    text = "\u26A0 Native library is phone pe nahi mili. Offline wake " +
                        "word kaam nahi karega \u2014 app Google recognizer pe chalti " +
                        "rahegi.",
                    color = EvRed,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        EvToggleCard(
            title = "Offline wake word",
            subtitle = "Wake word phone ke andar hi pakda jayega, bina internet ke " +
                "aur bina awaaz kahin bheje. Iske liye neeche wala model chahiye.",
            checked = offlineWake,
            onChange = { offlineWake = it },
        )

        EvCard(highlighted = modelReady) {
            EvCardTitle("Model")
            EvCardSubtitle(
                when {
                    downloading -> status.ifBlank { "Download ho raha hai\u2026" }
                    status.isNotBlank() -> status
                    modelReady -> "Model ready hai"
                    else -> "Model abhi download nahi hua (~4 MB)"
                }
            )

            Row(modifier = Modifier.padding(top = 4.dp)) {
                EvDialogButton(
                    label = if (modelReady) "Dobara download" else "Download karo",
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
        }

        EvCard {
            EvCardTitle("Keyword")
            EvCardSubtitle(
                "Seedha \"E.V\" likhne se kaam nahi chalega — keyword model ke " +
                    "tokens me likhna padta hai. Khaali chhod do to model ki apni " +
                    "keywords.txt chalegi."
            )
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                placeholder = { Text("khaali = model ki apni list") },
                colors = evFieldColors(),
            )

            OutlinedTextField(
                value = modelUrl,
                onValueChange = { modelUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                placeholder = { Text("Model URL — khaali = default") },
                colors = evFieldColors(),
            )
        }

        EvGap(32)
    }
}
