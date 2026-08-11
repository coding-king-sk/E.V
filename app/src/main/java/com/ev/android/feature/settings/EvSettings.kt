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
    private const val KEY_OFFLINE_WAKE = "offline_wake"
    private const val KEY_KWS_URL = "kws_model_url"
    private const val KEY_KWS_KEYWORDS = "kws_keywords"
    private const val KEY_KWS_THRESHOLD = "kws_threshold"
    private const val KEY_WHISPER = "stt_whisper"
    private const val KEY_ALIASES = "contact_aliases"
    private const val KEY_AUTO_SEND = "whatsapp_auto_send"
    private const val KEY_AUTO_TYPE = "auto_type"

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

    // ------------------------------------------------------- auto-send / type

    /**
     * WhatsApp me message khud se send ho ya sirf type ho ke ruk jaye.
     *
     * Default **on**, kyunki isi ke liye Accessibility on karte hain. Par
     * kabhi kabhi bhejne se pehle padhna hota hai \u2014 tab ise off kar do,
     * message likha hua milega aur send aap dabaoge.
     */
    fun whatsappAutoSend(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SEND, true)

    fun setWhatsappAutoSend(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SEND, value).apply()
    }

    /** "instagram pe type karo hello" wala feature on/off. */
    fun autoType(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_TYPE, true)

    fun setAutoType(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_TYPE, value).apply()
    }

    // ------------------------------------------------------- contact aliases

    /**
     * Galat suni gayi naam ki list, user ki apni.
     *
     * Speech recognizer Hinglish naam angrezi shabdon me badal deta hai \u2014
     * "Kais" ko "case", "Armaan" ko "a man". Ye har phone aur har contact ke
     * liye alag hota hai, isliye code me hardcode karna bekaar hai. User ek
     * baar likh deta hai:
     *
     * ```
     * case = Kais
     * a man = Armaan
     * ```
     *
     * Text ke roop me hi rakhte hain taaki Settings me seedha edit ho sake.
     */
    fun aliasesRaw(context: Context): String =
        prefs(context).getString(KEY_ALIASES, "").orEmpty()

    fun setAliasesRaw(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ALIASES, value.trim()).apply()
    }

    /** Alias text ko map me badalta hai. Galat lines chup-chaap chhod di jaati hain. */
    fun aliases(context: Context): Map<String, String> = parseAliases(aliasesRaw(context))

    /**
     * Jo naam bola gaya uska asli contact naam.
     *
     * Match na ho to wahi naam wapas \u2014 taaki kuch bigde na.
     */
    fun resolveAlias(context: Context, spokenName: String): String {
        val key = spokenName.lowercase().trim()
        if (key.isEmpty()) return spokenName
        return aliases(context)[key] ?: spokenName
    }

    internal fun parseAliases(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val map = LinkedHashMap<String, String>()
        raw.lines().forEach { line ->
            // "case = Kais" aur "case: Kais" dono chalte hain \u2014 likhne wale ko
            // yaad rakhna na pade ki kaunsa sahi hai.
            val parts = line.split("=", ":", limit = 2)
            if (parts.size != 2) return@forEach

            val from = parts[0].lowercase().trim()
            val to = parts[1].trim()
            if (from.isNotEmpty() && to.isNotEmpty()) map[from] = to
        }
        return map
    }

    // ------------------------------------------------------- sunna (STT)

    /**
     * Mic button pe Groq Whisper use karna hai ya Google recognizer.
     *
     * Default **off**. Whisper Hinglish behtar samajhta hai, par uske liye
     * awaaz Groq ke server pe jaati hai aur internet chahiye \u2014 ye faisla user
     * ka hona chahiye, hamara nahi.
     */
    fun whisperStt(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WHISPER, false) && hasApiKey(context)

    fun setWhisperStt(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WHISPER, value).apply()
    }

    // ------------------------------------------------------- wake word

    /**
     * Offline wake word (sherpa-onnx) use karna hai ya Google recognizer.
     *
     * Default **off** \u2014 isme model download karna padta hai aur tuning bhi,
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
     * Custom keyword, model ke tokens me likha hua (jaise `\u2581E \u2581V`).
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
