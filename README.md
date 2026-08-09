# E.V

Android voice assistant app \u2014 Kotlin + Jetpack Compose. Features ek-ek karke add ho rahe hain.

- Package: `com.ev.android`
- Kotlin 2.0.21, Compose BOM 2024.12.01, Material 3
- AGP 8.7.3, Gradle 8.11.1
- minSdk 24, target/compileSdk 35

## Features

### 1. Voice + text commands (Hinglish)
Mic \uD83C\uDFA4 dabao aur bolo, ya type karke \u25B6 dabao. Recognizer `hi-IN` pe set hai, isliye Hinglish theek se pakadta hai.

| Bologe | Hoga |
| --- | --- |
| "YouTube pe paisa song lagao" | YouTube khulega, `paisa song` search hoga, pehla video **khud play** |
| "Tum hi ho baja do" | YouTube pe wo gaana auto-play |
| "Spotify pe arijit singh lagao" | Spotify me wo gaana play |
| "WhatsApp kholo" | WhatsApp open |
| "PhonePe open karo" | PhonePe open |
| "YouTube pe cricket search karo" | Sirf results dikhenge, autoplay nahi |

**Parser kaise kaam karta hai** (`feature/command/CommandParser.kt`):
1. `"<app> pe ..."` pattern se target app nikalta hai
2. Verb detect karta hai \u2014 play (`lagao / baja do / chalao / sunao / play`), open (`kholo / open karo / chalu karo`), search (`search karo / dhundo / khojo`)
3. Bacha hua text = query. Filler words sirf **shuru/aakhir** se hatte hain, taaki gaane ka naam beech me na toote

**Autoplay ka trick** (`CommandExecutor.kt`): `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` \u2014 yehi intent Google Assistant use karta hai. YouTube, YT Music, Spotify, Gaana sab isko handle karke best match seedha play kar dete hain. Fallback chain:
1. `MEDIA_PLAY_FROM_SEARCH` target app pe
2. `ACTION_SEARCH` target app pe
3. `MEDIA_PLAY_FROM_SEARCH` bina package (koi bhi music app)
4. YouTube results page \u2014 app me, warna browser me

### 2. Saare installed apps
Phone ka har launchable app real icon ke saath grid me dikhta hai, tap karo aur khul jaayega. Voice se bhi \u2014 "Amazon kholo" bologe to installed apps ki list me se match ho jayega (exact \u2192 no-space \u2192 prefix \u2192 contains).

`QUERY_ALL_PACKAGES` permission ki zaroorat nahi \u2014 manifest me MAIN/LAUNCHER `<queries><intent>` block hai, jo Play Store policy ke hisaab se safe hai.

### 3. Quick actions
Curated shortcuts + system screens: Camera, Settings, Wi-Fi, Bluetooth, aur popular apps with web/Play Store fallback.

## Permissions
Koi runtime permission nahi chahiye. Voice ke liye system `RecognizerIntent` activity use hoti hai, isliye `RECORD_AUDIO` E.V ko khud nahi chahiye.

## Build locally
```bash
./gradlew assembleDebug
```
Android Studio me project open karke Run bhi kar sakte ho.

> Gradle wrapper jar commit nahi hai (binary). Android Studio first sync pe khud bana dega, ya `gradle wrapper` chala lo.

## CI / Releases
`.github/workflows/release.yml` har `main` push pe:
1. Debug APK build karta hai
2. Tag + release `v1.0.<run_number>` banata hai
3. APK attach karta hai
