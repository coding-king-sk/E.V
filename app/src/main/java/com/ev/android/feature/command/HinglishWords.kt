package com.ev.android.feature.command

/**
 * Hinglish shabdon ki ek hi jagah wali poori list.
 *
 * ## Ye file kyun hai
 *
 * Google ka recognizer Hinglish ko Latin script me likhta hai aur **har baar
 * ek jaisa nahi likhta**. Ek hi baat ke paanch-paanch spelling aate hain:
 * "awaaz / awaz / aawaz / avaaz", "minut / mint / minit", "bnao / banao".
 * Agar ek bhi shakl chhoot jaye to poori command "samajh nahi aaya" ban jati
 * hai — aur user ko lagta hai ki app kharab hai, jabki sirf spelling nayi thi.
 *
 * Isliye ye file banayi gayi hai: **ek hi jagah jahan har shabd ki saari
 * shaklein likhi hain.** Naya bug mile ("E.V ne ye shabd nahi samjha") to
 * poora parser padhne ki zaroorat nahi — seedha yahan us shabd ki nayi shakl
 * jod do.
 *
 * ## Kaise jodna hai
 *
 * 1. Neeche apne matlab wali list dhoondo (jaise [VOLUME] ya [YES]).
 * 2. Nayi spelling ek nayi line me `"...",` ki tarah likh do — sab lowercase.
 * 3. Bas. Parser ki apni list ke saath ye milti-julti rakhi gayi hai, isliye
 *    dono jagah ek hi soch chalti hai.
 *
 * ## Do kaam ke niyam
 *
 * - **Sab lowercase.** Parser pehle sab kuch lowercase kar deta hai.
 * - **Chhote aam shabd akele mat daalo.** Jaise akela "call" — "call of duty
 *   kholo" bhi call ban jayega. Aise shabd poore phrase ke saath likho
 *   ("call karo", "call lagao").
 */
object HinglishWords {

    // ------------------------------------------------------------ haan / na

    /** "haan" ke saare roop — sawaal-jawab me kaam aate hain. */
    val YES = listOf(
        "haan", "han", "haa", "ha", "haan ji", "han ji", "ji", "ji haan",
        "theek hai", "thik hai", "tik hai", "sahi hai", "bilkul", "kar do",
        "ok", "okay", "okey", "yes", "yep", "yeah", "sure", "done",
    )

    /** "nahi" ke saare roop — inhe sun kar kaam roka jata hai. */
    val NO = listOf(
        "nahi", "nahin", "nai", "na", "mat karo", "rehne do", "rahne do",
        "chhod do", "chod do", "cancel", "cancel karo", "band karo",
        "no", "nope", "nevermind", "stop",
    )

    // ------------------------------------------------------------ ginti

    /**
     * Bole gaye ginti ke shabd — "do minute", "paanch baje".
     *
     * "no" yahan jaan-boojh ke nahi hai. Wo [NO] me bhi hai ("nahi" ke roop
     * me), aur "no" ko 9 maanne se "no, rehne do" jaise jawab ginti ban jate
     * the. Nau ke liye "nau" pehle se maujood hai.
     */
    val NUMBERS = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "tin" to 3, "char" to 4, "chaar" to 4,
        "panch" to 5, "paanch" to 5, "chhe" to 6, "che" to 6, "chah" to 6,
        "saat" to 7, "sat" to 7, "aath" to 8, "ath" to 8, "nau" to 9,
        "das" to 10, "dus" to 10, "gyarah" to 11, "barah" to 12, "pandrah" to 15,
        "bees" to 20, "bis" to 20, "pachees" to 25, "tees" to 30, "chalis" to 40,
        "pachas" to 50, "pachaas" to 50, "saath" to 60, "sau" to 100,
        "aadha" to 30, "adha" to 30, "aadhe" to 30,
    )

    // ------------------------------------------------------------- waqt

    val MINUTES = listOf("minute", "minutes", "minit", "minut", "mint", "min", "mins", "m")
    val SECONDS = listOf("second", "seconds", "secend", "sekend", "sec", "secs")
    val HOURS = listOf("ghanta", "ghante", "ghanto", "ghnta", "hour", "hours", "hr", "hrs")
    val LATER = listOf("baad", "bad", "bad me", "baad me", "later", "ke baad")
    val TODAY = listOf("aaj", "aj", "today", "abhi", "abhi ke abhi", "turant")
    val TOMORROW = listOf("kal", "kl", "tomorrow", "agle din")
    val DAY_AFTER = listOf("parso", "parsu", "parson")
    val MORNING = listOf("subah", "subeh", "suba", "savere", "morning")
    val EVENING = listOf("shaam", "sham", "shyam", "evening")
    val NIGHT = listOf("raat", "rat", "night")
    val NOON = listOf("dopahar", "dopeher", "dupahar", "afternoon")
    val OCLOCK = listOf("baje", "baja", "bje", "bajay", "o clock", "oclock")

    // -------------------------------------------------------- aam kaam

    val OPEN = listOf(
        "kholo", "khol", "khol do", "kholdo", "khol de", "kholde", "kholna",
        "open", "open karo", "open kar", "open kardo",
        "chalu karo", "chalu kar do", "start karo", "launch karo", "nikalo",
    )

    val CLOSE = listOf(
        "band karo", "band kar do", "bandh karo", "bnd karo",
        "close karo", "close", "hata do", "hatao", "quit", "exit",
    )

    val PLAY = listOf(
        "lagao", "laga do", "lagado", "laga de", "lagade", "laga", "lgao",
        "bajao", "baja do", "bajado", "bja do",
        "chalao", "chala do", "chalado", "chala de",
        "sunao", "suna do", "sunado", "sunna hai",
        "play", "play karo", "play kar do",
    )

    val SEARCH = listOf(
        "search karo", "search kar do", "search", "serch karo",
        "dhoondo", "dhundo", "dhoond do", "dhund do", "khojo", "khoj do",
        "pata karo", "find karo", "find", "google karo",
    )

    val SEND = listOf(
        "bhejo", "bhej do", "bhejdo", "bhej", "bhejna", "bhej dena", "bhej dijiye",
        "send", "send karo", "send kar do", "share", "share karo", "forward karo",
    )

    val CALL = listOf(
        "call karo", "call kar do", "call lagao", "call laga do", "call milao",
        "phone karo", "phone lagao", "phone milao", "dial karo", "baat karni hai",
    )

    val TYPE = listOf(
        "type karo", "type kar do", "type kardo", "type", "taip karo",
        "likho", "likh do", "likh dena", "write karo",
    )

    val TELL = listOf(
        "batao", "bata do", "bta do", "bta", "bataiye", "batana",
        "samjhao", "samjha do", "padho", "padh do", "padh ke batao",
        "tell me", "read",
    )

    // ----------------------------------------------------- phone control

    val VOLUME = listOf(
        "volume", "vloume", "volum", "sound", "saund",
        "awaz", "awaaz", "aawaz", "aawaaz", "aavaz", "aavaaz", "avaaz", "avaz",
    )

    val BRIGHTNESS = listOf(
        "brightness", "brightnes", "braitness", "brightnass",
        "roshni", "rosni", "roshani", "chamak", "screen light", "light",
    )

    val TORCH = listOf(
        "torch", "tourch", "toarch", "flashlight", "flash light", "flash",
        "batti", "bati", "light jala",
    )

    val WIFI = listOf("wifi", "wi-fi", "wi fi", "waifai", "vaifai", "vifi")
    val BLUETOOTH = listOf("bluetooth", "blutooth", "bluetuth", "blue tooth", "blututh")
    val DATA = listOf("mobile data", "internet", "data", "net", "net on", "net band")

    val UP = listOf(
        "badhao", "badha do", "badhado", "bada do", "bdhao", "increase", "up",
        "tez", "tej", "tezz", "zyada", "jyada", "zada", "jada", "high", "full",
    )

    val DOWN = listOf(
        "kam", "kam karo", "ghatao", "ghata do", "decrease", "down", "low",
        "dheema", "dhima", "dhime", "dheeme", "dhimi", "dheemi",
        "halka", "halki", "halke", "slow",
    )

    val ON = listOf("on", "onn", "chalu", "chaalu", "jala", "jalao", "jala do", "enable", "start")
    val OFF = listOf("off", "of", "band", "bandh", "bnd", "bujha", "bujhao", "bujha do", "disable")

    // ----------------------------------------------------------- screen

    val SCREENSHOT = listOf(
        "screenshot", "screen shot", "screenshoot", "scrinshot", "skrinshot",
        "screen capture", "capture", "ss",
    )

    val SCREEN = listOf("screen", "skreen", "scrin", "display", "page", "ye wala")

    val SCROLL_DOWN = listOf(
        "scroll down", "scroll niche", "scroll neeche", "niche karo", "neeche karo",
        "niche kar do", "neeche kar do", "niche jao", "neeche jao", "scroll",
        "next reel", "agli reel", "agla reel", "next post", "agli post", "next short",
    )

    val SCROLL_UP = listOf(
        "scroll up", "scroll upar", "scroll uper", "upar karo", "uper karo",
        "upar kar do", "uper kar do", "upar jao", "uper jao",
        "pichli reel", "pichla reel", "previous reel", "pichli post", "wapas upar",
    )

    // ------------------------------------------------------ camera / media

    val PHOTO = listOf(
        "photo", "foto", "photu", "picture", "pic", "tasveer", "tasvir", "snap",
        "selfie", "selfi", "celfie",
    )

    val TAKE = listOf("lo", "le lo", "lelo", "le", "khicho", "kheecho", "khincho", "nikalo", "click karo")

    val VIDEO = listOf("video", "vidio", "vedio", "vidoe", "recording", "record", "reel banao")

    val MAKE = listOf("banao", "bana do", "banado", "bnao", "bna do", "bnado", "banana", "make")

    val SONG = listOf("gaana", "gana", "gane", "gaane", "song", "songs", "music", "track", "gnaa")

    val NEXT = listOf("next", "agla", "agli", "aage wala", "badlo", "badal do", "change karo")
    val PREVIOUS = listOf("previous", "pichla", "pichli", "pehle wala", "last wala", "peeche wala")
    val PAUSE = listOf("roko", "rok do", "ruko", "pause", "pause karo", "band karo", "thehro")

    // --------------------------------------------------------- sawaal

    val HOW_MUCH = listOf(
        "kitna", "kitni", "kitne", "kitnaa", "ktna", "ktni",
        "how much", "how many", "level", "status",
    )

    val WHAT = listOf("kya", "kia", "kyaa", "what", "konsa", "kaunsa", "kaun sa", "kon sa", "which")

    val WHERE = listOf("kahan", "kaha", "kidhar", "kithe", "where", "kis jagah")

    val REMAINING = listOf("bacha", "bachi", "bache", "baki", "baaki", "remaining", "left")

    val BATTERY = listOf("battery", "batri", "baitri", "baitry", "batery", "btry", "charging")

    val STORAGE = listOf("storage", "stroage", "strorage", "memory", "space", "jagah", "gb")

    val WEATHER = listOf(
        "mausam", "mosam", "mausham", "mausm", "mousam", "mausam kaisa",
        "weather", "wether", "wheather",
        "baarish", "barish", "barsat", "barsaat", "temperature", "tapman",
    )

    val LOCATION = listOf(
        "location", "lokeshan", "lokeshn", "jagah", "address", "pata",
        "main kahan hoon", "main kaha hu", "mai kahan hu", "where am i",
    )

    // ------------------------------------------------------ jodne wale

    /**
     * "pe / par / me" — ye sabse zaroori list hai.
     *
     * en-IN recognizer "pe" ko aksar **"per"** likh deta hai. Isse pehle poora
     * naam bigad jata tha ("whatsapp per rehan" me "per" naam ka hissa ban
     * jata tha) aur koi contact match hi nahi hota tha.
     */
    val ON_WORD = listOf("pe", "per", "pey", "peh", "par", "pr", "me", "mein", "main", "on", "in")

    /** Vaakya jodne wale shabd — inse do kaam alag hote hain. */
    val AND = listOf("aur", "or", "and", "phir", "fir", "uske baad", "iske baad", "then")

    /** Bekar bharti ke shabd — inka matlab kuch nahi hota. */
    val FILLER = listOf(
        "mujhe", "muje", "mereko", "zara", "jara", "please", "plz", "bhai", "yaar",
        "beta", "ok to", "to", "na", "thoda", "thodi", "ek kaam karo", "suno",
    )

    /**
     * Wake word ke roop.
     *
     * "E.V" ko recognizer bahut tarah se likhta hai — ye list isi liye hai.
     */
    val WAKE = listOf(
        "ev", "e v", "e.v", "eevee", "evi", "eve", "heyev", "hey ev", "hi ev",
        "ye v", "a v", "ai v", "ev bhai", "ev suno",
    )

    /**
     * Kisi bhi list me ye shabd hai ya nahi — poora shabd milega, aadha nahi.
     *
     * "kam" dhoondhte waqt "kamra" match na ho jaye, isliye seedha `contains`
     * nahi kiya gaya.
     *
     * Punctuation bhi shabd ka kinara maana jata hai. Pehle sirf space par
     * shabd tootte the, isliye "awaaz thodi dhime." ka aakhri shabd
     * "dhime." reh jata tha aur list se match hi nahi hota tha — aur bolne
     * wale ko lagta tha ki E.V ne baat samjhi hi nahi.
     */
    fun has(text: String, words: List<String>): Boolean {
        val clean = text.lowercase()
            .replace(Regex("[?!.,;:\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val hay = " " + clean + " "
        return words.any { hay.contains(" " + it + " ") }
    }

    /** Bole gaye ginti ko number me badalna — "paanch" -> 5. */
    fun number(word: String): Int? =
        word.trim().lowercase().let { it.toIntOrNull() ?: NUMBERS[it] }
}
