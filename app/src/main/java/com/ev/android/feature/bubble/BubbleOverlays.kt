package com.ev.android.feature.bubble

/**
 * Screenshot lete waqt E.V ke apne overlay ko chhupane ka raasta.
 *
 * Screenshot Android khud leta hai, aur usme screen pe jo bhi dikh raha ho wo
 * sab aata hai - bubble aur action bar bhi. Wo dono E.V ke apne hain, isliye
 * unhe ek pal ke liye hata dena chahiye, warna user ke screenshot me E.V ka
 * apna hi orb baith jata hai.
 *
 * Yahan sirf ek callback rakha hai. Bubble service chalu ho to wo khud ko is
 * jagah par register kar deti hai; band ho to yahan kuch nahi hota aur
 * screenshot pehle jaisa hi chalta hai.
 */
object BubbleOverlays {

    /** Bubble service chalu hone par yahi bhari jati hai. */
    @Volatile
    var hider: ((Long) -> Unit)? = null

    /**
     * Itni der ke liye E.V ke overlay gayab kar do.
     *
     * @return false agar bubble hi chalu nahi hai - tab chhupane wala kuch nahi
     */
    fun hideFor(millis: Long): Boolean {
        val hide = hider ?: return false
        runCatching { hide(millis) }
        return true
    }
}
