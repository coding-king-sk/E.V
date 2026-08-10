# E.V

Hinglish voice assistant for Android — Kotlin + Jetpack Compose, single-activity, no backend.

Bolo ya likho, aur phone ka kaam ho jaye: app kholna, gaana chalana, photo lena, message bhejna, reminder lagana. Sab kuch ek hi command line se.

```
"instagram kholo"
"tum hi ho baja do"
"photo lo"
"rehan ko call lagao"
"kal subah 8 baje yaad dilana ki dawai leni hai"
"torch on karo aur photo lo"
```

- Package `com.ev.android` · minSdk 24 · target/compileSdk 35
- Kotlin 2.0.21 · Compose BOM 2024.12.01 · Material 3 · AGP 8.7.3
- APK har `main` push pe apne aap ban'ti hai → [Releases](../../releases)

## Screen

Ek hi HUD screen, chaar tab:

| Tab | Kya hai |
| --- | --- |
| **COMMAND** | Orb, status, mic/camera buttons, command box |
| **NOTES** | Chhote notes, sirf phone me |
| **GALLERY** | E.V se li hui photo/video |
| **HISTORY** | Pichle command — "kya suna" vs "kya samjha" |

Apps kholna aur torch/volume jaise kaam tabs me nahi hain — wo bolke ya likhke hote hain.

## Features

### Awaaz
- **Hands-free** — "Hey E.V" bolo, background service sunta rehta hai
- **Hindi TTS** — phone ki sabse acchi Hindi voice apne aap chuni jaati hai
- **Whisper (optional)** — Settings me on karo to mic button Groq Whisper use karega, jo Hinglish behtar samajhta hai. Internet aur API key chahiye
- **Offline wake word (adhoora)** — sherpa-onnx ka code aur native library repo me hai, par abhi listening service se juda nahi hai

### Kaam
- Koi bhi installed app kholna ("amazon kholo")
- Gaana/video chalana — YouTube, YT Music, Spotify, Gaana
- "Ye kaun sa gaana hai"
- WhatsApp / SMS bhejna, call lagana
- Camera — "photo lo", "selfie lo", "video banao" (khud khulta hai, khud kheenchta hai)
- Dusri app me type karna — "instagram pe type karo hello"
- Timer, alarm, reminder (reboot ke baad bhi zinda rehte hain)
- Torch, WiFi, Bluetooth, volume, brightness, silent, screenshot, screen lock
- **Multitasking** — "torch on karo aur photo lo" ek hi vaakya me

### AI (optional)
Jo command offline parser na samjhe, wahi Groq ko jata hai. Key na ho to app poori tarah offline chalti hai, bas thodi kam samajhdaar.

## Kaise kaam karta hai

```
awaaz / text
    ↓
CommandParser        offline, turant, free
    ↓ (samajh na aaye)
AiCommandResolver    Groq → command
    ↓ (phir bhi nahi)
Conversation         Groq → seedha jawab
    ↓
CommandExecutor → Intent / AccessibilityService / CameraX
```

| Folder | Kaam |
| --- | --- |
| `feature/command` | Parser, executor, time parsing |
| `feature/voice` | Hands-free service, mic input, Whisper |
| `feature/wakeword` | sherpa-onnx KWS (adhoora) |
| `feature/camera` | CameraX se auto photo/video |
| `feature/accessibility` | Auto-send aur dusri app me typing |
| `feature/ai` | Groq chat + Whisper |
| `feature/hud` | HUD ke UI parts |

## Permissions

Koi permission zabardasti nahi — jo feature use karoge, uske waqt hi maangi jayegi.

| Permission | Kis liye | Na do to |
| --- | --- | --- |
| Mic | Hands-free, Whisper | Sirf typing chalegi |
| Camera | Photo/video | Camera command band |
| Contacts / SMS / Call | "X ko call lagao" | Dialer khulega, khud call nahi |
| Accessibility | Auto-send, typing | Message likha milega, bhejna khud padega |
| Battery optimization off | Hands-free zinda rahe | Service kuch der baad mar jayegi |

API key sirf phone me (SharedPreferences) rehti hai — repo me kabhi nahi.

## Build

```bash
./gradlew assembleDebug
```

Ya Android Studio me open karke Run. Gradle wrapper jar commit nahi hai; first sync pe khud ban jata hai.

## CI

`.github/workflows/release.yml` — har `main` push pe unit tests chalte hain, phir debug APK ban ke `v<version>-<run>` tag pe release ho jaati hai. Tests fail hue to APK nahi banti.

## Abhi kya baaki hai

- Wake word ko listening service se jodna
- KWS model ka download URL verify karna
- Release signing (abhi debug-signed APK hai)
- Phone pe poora testing round
