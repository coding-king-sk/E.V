package com.ev.android.feature.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Mic pe abhi kitni awaaz aa rahi hai — 0 (chup) se 1 (zor se).
 *
 * Ye ek global holder isliye hai ki awaaz ka level recognizer ke andar banta
 * hai aur orb screen ke doosre kone me hai. Beech me har composable se ise
 * haath-o-haath pass karte to LauncherScreen, CommandPane, sab ka signature
 * badalna padta — sirf ek animation ke liye itni tod-phod theek nahi.
 *
 * Compose state hai, isliye value badalte hi orb apne aap dobara draw hota hai.
 */
object MicLevel {

    /** 0f se 1f. Orb isi ko padh kar lehrata hai. */
    var value by mutableFloatStateOf(0f)
        private set

    /**
     * SpeechRecognizer `rmsdB` deta hai, jo lagbhag -2 (bilkul chup) se 10
     * (zor se bolna) tak jata hai. Android is number ki koi guarantee nahi
     * deta, isliye range ko clamp kar dete hain — warna kisi phone pe orb
     * paagalon ki tarah uchhalne lagta.
     */
    fun update(rmsdB: Float) {
        value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    fun reset() {
        value = 0f
    }
}
