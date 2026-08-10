package com.ev.android.feature.settings

import android.content.Context

/**
 * App ki settings \u2014 sirf phone me, SharedPreferences me.
 *
 * API key **kabhi** code me hardcode nahi hoti aur na hi GitHub pe jaati hai.
 * User ek baar app me paste karta hai, bas.
 */
object EvSettings {

    private const val PREFS = "ev_settings"
    private const val KEY_API = "groq_api_key"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_AI_PERSONAL = "ai_personal"

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
}
