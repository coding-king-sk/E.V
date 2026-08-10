package com.ev.android.feature.settings

import android.content.Context

/**
 * App ki settings — sirf phone me, SharedPreferences me.
 *
 * API key **kabhi** code me hardcode nahi hoti aur na hi GitHub pe jaati hai.
 * User ek baar app me paste karta hai, bas.
 */
object EvSettings {

    private const val PREFS = "ev_settings"
    private const val KEY_API = "groq_api_key"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_AI_PERSONAL = "ai_personal"
    private const val KEY_OFFLINE_WAKE = "offline_wake"
    private const val KEY_KWS_URL = "kws_model_url"
    private const val KEY_KWS_KEYWORDS = "kws_keywords"
    private const val KEY_KWS_THRESHOLD = "kws_threshold"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API, "").orEmpty().trim()

    fun setApiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API, value.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean = apiKey(context).isNotEmpty()

    /** AI fallback on/off. Key ho to default on. */
    fun aiEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AI_ENABLED, true) && hasApiKey(context)

    fun setAiEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI_ENABLED, value).apply()
    }

    /**
     * Kya message/call wale command bhi AI ko bhejne hain?
     *
     * Default **off**, jaan bujh ke. In commands me contact ka naam aur message
     * ka text hota hai, aur free tier pe providers prompts ko training ke liye
     * use kar sakte hain. User khud on kare tabhi jayega.
     */
    fun sendPersonalToAi(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AI_PERSONAL, false)

    fun setSendPersonalToAi(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI_PERSONAL, value).apply()
    }

    // ------------------------------------------------------- wake word

    /**
     * Offline wake word (sherpa-onnx) use karna hai ya Google recognizer.
     *
     * Default **off** — isme model download karna padta hai aur tuning bhi,
     * isliye user khud chune tabhi.
     */
    fun offlineWakeWord(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OFFLINE_WAKE, false)

    fun setOfflineWakeWord(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OFFLINE_WAKE, value).apply()
    }

    /** KWS model ka download link. Khaali ho to default use hota hai. */
    fun wakeWordModelUrl(context: Context): String =
        prefs(context).getString(KEY_KWS_URL, "").orEmpty().trim()

    fun setWakeWordModelUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_KWS_URL, value.trim()).apply()
    }

    /**
     * Custom keyword, model ke tokens me likha hua (jaise `▁E ▁V`).
     *
     * Khaali chhoda to model ki apni keywords.txt chalti hai.
     */
    fun wakeWordKeywords(context: Context): String =
        prefs(context).getString(KEY_KWS_KEYWORDS, "").orEmpty().trim()

    fun setWakeWordKeywords(context: Context, value: String) {
        prefs(context).edit().putString(KEY_KWS_KEYWORDS, value.trim()).apply()
    }

    /**
     * Kitna pakka hone pe wake maana jaye (0.05 se 0.95).
     *
     * Kam = jaldi jaag jayega par galat bhi jaagega. Zyada = kam galtiyan par
     * kabhi kabhi sunega hi nahi. Har awaaz aur har kamre me farq padta hai,
     * isliye ye user ke haath me hai.
     */
    fun wakeWordThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_KWS_THRESHOLD, 0.25f).coerceIn(0.05f, 0.95f)

    fun setWakeWordThreshold(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_KWS_THRESHOLD, value.coerceIn(0.05f, 0.95f)).apply()
    }
}
