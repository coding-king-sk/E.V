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
import com.ev.android.feature.bubble.Bubble
import com.ev.android.feature.hud.EvCard
import com.ev.android.feature.hud.EvCardSubtitle
import com.ev.android.feature.hud.EvCardTitle
import com.ev.android.feature.hud.EvDialogButton
import com.ev.android.feature.hud.EvGap
import com.ev.android.feature.hud.EvPanel
import com.ev.android.feature.hud.EvSectionHeader
import com.ev.android.feature.hud.EvToggleCard
import com.ev.android.feature.hud.evFieldColors
import com.ev.android.feature.permissions.PermissionsSection
import com.ev.android.feature.reminders.Reminders
import com.ev.android.feature.wakeword.SherpaWakeWord
import com.ev.android.feature.wakeword.WakeWordModel
import com.ev.android.ui.theme.EvRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App ki saari settings \u2014 poore page me.
 *
 * Groq API key ka field yahan jaan-boojh ke nahi hai \u2014 wo home screen ke
 * API KEY button me hai. Ek hi cheez do jagah rakhne se ye confusion hota tha
 * ki kaunsi wali asli hai.
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
    var aliases by remember { mutableStateOf(EvSettings.aliasesRaw(context)) }

    var bubbleOn by remember { mutableStateOf(EvSettings.bubbleEnabled(context)) }
    var overlayOk by remember { mutableStateOf(Bubble.canShow(context)) }

    var wakeName by remember { mutableStateOf(EvSettings.wakeName(context)) }

    var reminders by remember { mutableStateOf(Reminders.upcoming(context)) }
    val notificationsOff = remember { Reminders.notificationsBlocked(context) }
    val reminderClock = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

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
        onSave = {
            EvSettings.setAiEnabled(context, aiOn)
            EvSettings.setSendPersonalToAi(context, personalOn)
            EvSettings.setWhisperStt(context, whisperOn)
            EvSettings.setWhatsappAutoSend(context, autoSend)
            EvSettings.setAutoType(context, autoType)
            EvSettings.setAliasesRaw(context, aliases)
            EvSettings.setWakeName(context, wakeName)
            EvSettings.setOfflineWakeWord(context, offlineWake)
            EvSettings.setWakeWordModelUrl(context, modelUrl)
            EvSettings.setWakeWordKeywords(context, keywords)

            // Bubble ko yahin chalu/band kar dete hain \u2014 save daba ke bahar
            // jaane par turant farq dikhna chahiye.
            EvSettings.setBubbleEnabled(context, bubbleOn)
            if (bubbleOn && Bubble.canShow(context)) {
                Bubble.start(context)
            } else {
                Bubble.stop(context)
            }

            onSaved(
                if (aiOn) "AI fallback chalu hai" else "AI fallback band hai"
            )
            onClose()
        },
    ) {

        // Onboarding hat gaya hai, isliye permissions ka poora hisaab ab yahan
        // sabse upar hai \u2014 typing/auto-send wali accessibility bhi isi list me.
        EvSectionHeader("Permissions")
        PermissionsSection()

        // ---------------------------------------------------- floating bubble

        EvSectionHeader("Floating bubble")

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
            subtitle = "E.V ka orb har app ke upar tairta rahega. Tap karo to E.V " +
                "khul jayega, ungli se jahan chaho khinch ke rakh do, aur lamba " +
                "daba ke band kar do.",
            checked = bubbleOn,
            onChange = { bubbleOn = it },
        )

        // ---------------------------------------------------- E.V ka naam

        EvSectionHeader("E.V ka naam")

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
                onValueChange = { wakeName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                placeholder = { Text(EvSettings.DEFAULT_WAKE_NAME) },
                colors = evFieldColors(),
            )
        }

        EvSectionHeader("Messaging")

        EvToggleCard(
            title = "WhatsApp auto send",
            subtitle = "On: message khud chala jayega. Off: chat khul jayega aur " +
                "message likha hua milega, send aap dabaoge. Dono me Accessibility " +
                "on hona zaroori hai.",
            checked = autoSend,
            onChange = { autoSend = it },
        )

        EvToggleCard(
            title = "Auto type",
            subtitle = "\"Instagram pe type karo hello\" jaise command. Off karoge to " +
                "E.V kisi app ke text box me kuch nahi likhega.",
            checked = autoType,
            onChange = { autoType = it },
        )

        // ------------------------------------------------------- reminders

        EvSectionHeader("Reminders")

        if (notificationsOff) {
            EvCard {
                Text(
                    text = "\u26A0 Notifications band hain. Reminder ka waqt aane par E.V " +
                        "bol to dega, par screen pe kuch nahi dikhega.",
                    color = EvRed,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        if (reminders.isEmpty()) {
            EvCard {
                EvCardTitle("Abhi koi reminder nahi")
                EvCardSubtitle(
                    "Bol ke lagao: \"kal subah 8 baje yaad dilana ki dawai leni hai\" " +
                        "ya \"2 minute baad yaad dilana paani peena hai\". Yahan list " +
                        "me dikhega ki kab bajega \u2014 isse pata chal jata hai ki " +
                        "reminder set hua ya nahi."
                )
            }
        } else {
            reminders.forEach { reminder ->
                EvCard {
                    EvCardTitle(reminder.text)
                    EvCardSubtitle(reminderClock.format(Date(reminder.at)))

                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        EvDialogButton(
                            label = "Hatao",
                            onClick = {
                                Reminders.cancel(context, reminder.id)
                                reminders = Reminders.upcoming(context)
                            },
                        )
                    }
                }
            }
        }

        EvSectionHeader("Naam ki galtiyan (aliases)")

        EvCard {
            EvCardTitle("Galat suna jaata hai?")
            EvCardSubtitle(
                "Speech recognizer Hinglish naam angrezi shabdon me badal deta hai \u2014 " +
                    "\"Kais\" ko \"case\". Har phone aur har contact ke liye ye alag hota " +
                    "hai, isliye ise app me fix karna theek nahi. Yahan ek line me ek " +
                    "jodi likho:\n\ncase = Kais\na man = Armaan"
            )
            OutlinedTextField(
                value = aliases,
                onValueChange = { aliases = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                minLines = 3,
                placeholder = { Text("case = Kais") },
                colors = evFieldColors(),
            )
        }

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
                "pe lagta hai, wake word pe nahi.",
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
                "Seedha naam likhne se kaam nahi chalega \u2014 keyword model ke " +
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
                placeholder = { Text("Model URL \u2014 khaali = default") },
                colors = evFieldColors(),
            )
        }

        EvGap(32)
    }
}
