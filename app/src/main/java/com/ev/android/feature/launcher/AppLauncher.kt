package com.ev.android.feature.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * App kholne ke do chhote helpers.
 *
 * Pehle yahan ek poora shortcut-catalog wala launcher tha, par jab se screen
 * se APPS grid hata hai, sab kuch command se hota hai — aur command ke paas
 * package name pehle se hota hai.
 */
object AppLauncher {

    /** Kisi bhi installed app ko package name se kholta hai. */
    fun launchPackage(context: Context, packageName: String): Boolean {
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull() ?: return false
        return startIntent(context, intent)
    }

    /** Safe startActivity — crash ki jagah false return karta hai. */
    fun startIntent(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
