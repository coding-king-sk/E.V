package com.ev.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ev.android.feature.bubble.Bubble
import com.ev.android.feature.hud.EvCard
import com.ev.android.feature.hud.EvCardSubtitle
import com.ev.android.feature.hud.EvCardTitle
import com.ev.android.feature.hud.EvDialogButton
import com.ev.android.feature.hud.EvGap
import com.ev.android.feature.hud.EvPanel
import com.ev.android.feature.hud.EvToggleCard
import com.ev.android.feature.hud.HudSectionLabel
import com.ev.android.feature.hud.evFieldColors
import com.ev.android.feature.permissions.PermissionsSection
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import com.ev.android.ui.theme.EvOutline
import com.ev.android.ui.theme.EvRed
import kotlinx.coroutines.launch

/**
 * App ki saari settings \u2014 poore page me, home screen wale HUD look me.
 *
 * Do cheezein jaan boojh ke aisi hain:
 *
 * 1. **SAVE ka button nahi hai.** Har cheez badalte hi save ho jati hai \u2014
 *    SAVE dabana bhool jaane par setting chup-chaap gayab ho jaati thi, aur
 *    usme user ki koi galti nahi thi.
 *
 * 2. **Order kaam ke hisaab se hai, feature ke hisaab se nahi.** Jo roz
 *    badalti hain (bubble, naam, sunna) wo upar; jo ek baar set karke bhool
 *    jaate hain (messaging, AI, permissions) wo neeche; aur offline wake word
 *    ka jhamela sabse aakhir me, kyunki wo sabse kam logon ke kaam ka hai.
 *
 * Groq API key ka field yahan nahi hai \u2014 wo home screen ke API KEY button
 * me hai. Reminders aur naam ki galtiyan (aliases) bhi home ke apne tab me.
 */
@Composable
fun SettingsPanel(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var aiOn by remember { mutableStateOf(EvSettings.aiEnabled(context)) }
    var personalOn by remember { mutableStateOf(EvSettings.sendPersonalToAi(context)) }
    var whisperOn by remember { mutableStateOf(EvSettings.whisperStt(context)) }

    var autoSend by remember { mutableStateOf(EvSettings.whatsappAutoSend(context)) }
    var autoType by remember { mutableStateOf(EvSettings.autoType(context)) }

    var bubbleOn by remember { mutableStateOf(EvSettings.bubbleEnabled(context)) }
    var overlayOk by remember { mutableStateOf(Bubble.canShow(context)) }

    var wakeName by remember { mutableStateOf(EvSettings.wakeName(context)) }

    var offlineWake by remember { mutableStateOf(EvSettings.offlineWakeWord(context)) }
    var modelUrl by remember { mutableStateOf(EvSettings.wakeWordModelUrl(context)) }
    var keywords by remember { mutableStateOf(EvSettings.wakeWordKeywords(context)) }
    var modelReady by remember { mutableStateOf(WakeWordModel.isInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val libraryOk = remember { SherpaWakeWord.isLibraryAvailable() }

    EvPanel(
        title = "Settings",
        onClose = onClose,
        modifier = modifier,
    ) {

        // ------------------------------------------------- 1. bubble

        HudSectionLabel("// Floating bubble")

        HudGroup {
            if (!overlayOk) {
                EvCard {
                    Text(
                        text = "\u26A0 \"Display over other apps\" ki permission nahi hai. " +
                            "Ye normal permission dialog se nahi milti \u2014 neeche wale " +
                            "button se Settings khol ke E.V ko allow karo, phir wapas " +
                            "aa ke CHECK dabao.",
                        color = EvRed,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )

                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        EvDialogButton(
                            label = "Permission do",
                            onClick = { context.startActivity(Bubble.permissionIntent(context)) },
                        )
                        EvDialogButton(
                            label = "Check",
                            onClick = { overlayOk = Bubble.canShow(context) },
                        )
                    }
                }
            }

            EvToggleCard(
                title = "Bubble dikhao",
                subtitle = "E.V ka orb har app ke upar tairta rahega. Ek tap = neeche " +
                    "bar aur mic turant chalu, double tap = poori app. Khinch ke " +
                    "chhodoge to kinare pe chipak jayega, 35 second kuch na karo to " +
                    "bar khud band, aur neeche X pe chhodoge ya lamba dabaoge to " +
                    "bubble band.",
                checked = bubbleOn,
                onChange = { on ->
                    bubbleOn = on
                    EvSettings.setBubbleEnabled(context, on)
                    if (on && Bubble.canShow(context)) {
                        Bubble.start(context)
                        onSaved("Bubble chalu")
                    } else {
                        Bubble.stop(context)
                        if (on) onSaved("Pehle overlay permission do") else onSaved("Bubble band")
                    }
                },
            )
        }

        // ------------------------------------------------- 2. naam

        HudSectionLabel("// E.V ka naam")

        HudGroup {
            EvCard {
                EvCardTitle("Kis naam se jagana hai?")
                EvCardSubtitle(
                    "Hands-free mode isi naam ko sunta hai. \"Jarvis\", \"Boss\", jo " +
                        "chaho rakh lo \u2014 phir \"Hey Jarvis\" bol ke jagana. Chhote aur " +
                        "saaf naam behtar pakde jaate hain. Khaali chhodoge to E.V hi " +
                        "rahega."
                )
                OutlinedTextField(
                    value = wakeName,
                    onValueChange = {
                        wakeName = it
                        EvSettings.setWakeName(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    singleLine = true,
                    placeholder = { Text(EvSettings.DEFAULT_WAKE_NAME) },
                    colors = evFieldColors(),
                )
            }
        }

        // ------------------------------------------------- 3. sunna

        HudSectionLabel("// Sunna")

        HudGroup {
            EvToggleCard(
                title = "Mic pe Whisper (Groq)",
                subtitle = "Whisper Hinglish kaafi behtar samajhta hai, par aapki awaaz " +
                    "Groq ke server pe jaati hai aur internet chahiye. Sirf mic button " +
                    "pe lagta hai, wake word pe nahi.",
                checked = whisperOn,
                onChange = {
                    whisperOn = it
                    EvSettings.setWhisperStt(context, it)
                },
            )
        }

        // ------------------------------------------------- 4. messaging

        HudSectionLabel("// Messaging")

        HudGroup {
            EvToggleCard(
                title = "WhatsApp auto send",
                subtitle = "On: message khud chala jayega. Off: chat khul jayega aur " +
                    "message likha hua milega, send aap dabaoge. Dono me Accessibility " +
                    "on hona zaroori hai.",
                checked = autoSend,
                onChange = {
                    autoSend = it
                    EvSettings.setWhatsappAutoSend(context, it)
                },
            )

            EvToggleCard(
                title = "Auto type",
                subtitle = "\"Instagram pe type karo hello\" jaise command. Off karoge to " +
                    "E.V kisi app ke text box me kuch nahi likhega.",
                checked = autoType,
                onChange = {
                    autoType = it
                    EvSettings.setAutoType(context, it)
                },
            )
        }

        // ------------------------------------------------- 5. AI

        HudSectionLabel("// AI")

        HudGroup {
            EvToggleCard(
                title = "AI fallback",
                subtitle = "Jo command E.V khud na samajh paye wahi Groq ko jata hai. " +
                    "Key home screen ke API KEY button me daalni hai.",
                checked = aiOn,
                onChange = {
                    aiOn = it
                    EvSettings.setAiEnabled(context, it)
                },
            )

            EvToggleCard(
                title = "Message/call ka text bhi AI ko bhejo",
                subtitle = "Ye off rakhna behtar hai. In commands me contact ka naam aur " +
                    "message ka text hota hai, aur free tier pe providers prompts ko " +
                    "training me use kar sakte hain.",
                checked = personalOn,
                onChange = {
                    personalOn = it
                    EvSettings.setSendPersonalToAi(context, it)
                },
            )
        }

        // ------------------------------------------------- 6. permissions

        // Onboarding hat gaya hai, isliye permissions ka poora hisaab yahin
        // hai \u2014 par neeche, kyunki inhe ek baar de ke log dobara nahi
        // chhedte.
        HudSectionLabel("// Permissions")

        HudGroup {
            PermissionsSection()
        }

        // ------------------------------------------------- 7. advanced

        HudSectionLabel("// Offline wake word (advanced)")

        HudGroup {
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
                onChange = {
                    offlineWake = it
                    EvSettings.setOfflineWakeWord(context, it)
                },
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
                    "Seedha naam likhne se kaam nahi chalega \u2014 keyword model ke " +
                        "tokens me likhna padta hai. Khaali chhod do to model ki apni " +
                        "keywords.txt chalegi."
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        EvSettings.setWakeWordKeywords(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    singleLine = true,
                    placeholder = { Text("khaali = model ki apni list") },
                    colors = evFieldColors(),
                )

                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = {
                        modelUrl = it
                        EvSettings.setWakeWordModelUrl(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    singleLine = true,
                    placeholder = { Text("Model URL \u2014 khaali = default") },
                    colors = evFieldColors(),
                )
            }
        }

        EvGap(32)
    }
}

/**
 * Ek section ka HUD frame.
 *
 * Home screen ke tabs aur tiles ki tarah patli dhaar wala dabba \u2014 isi se
 * pata chalta hai ki kaunsi cheezein ek saath hain. Pehle sab cards ek lambi
 * line me the aur section kahan khatam hua ye dikhta hi nahi tha.
 */
@Composable
private fun HudGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, EvOutline), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        content()
    }
}
