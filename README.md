# E.V

Android app — Kotlin + Jetpack Compose. Features ek-ek karke add ho rahe hain.

- Package: `com.ev.android`
- Kotlin 2.0.21, Compose BOM 2024.12.01, Material 3
- AGP 8.7.3, Gradle 8.11.1
- minSdk 24, target/compileSdk 35

## Features

### 1. App Launcher ("YouTube kholo", "WhatsApp kholo")
Home screen pe ek search box + app grid hai. Card tap karo, app khul jaata hai.

Launch resolution order:
1. System screen (Settings, Wi-Fi, Bluetooth, Camera)
2. Installed app ka launcher intent
3. Web fallback (jaise youtube.com)
4. Play Store page

Search Hinglish keywords bhi samajhta hai — `gaana`, `khana`, `rasta`, `paisa`, `youtube kholo` sab chalega.

Currently supported: YouTube, WhatsApp, Instagram, Gmail, Chrome, Maps, Telegram, Spotify, PhonePe, Swiggy, Camera, Settings, Wi-Fi, Bluetooth.

Naya app add karna ho to sirf `AppCatalog.shortcuts` me ek entry aur `AndroidManifest.xml` ke `<queries>` me package daal do.

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
