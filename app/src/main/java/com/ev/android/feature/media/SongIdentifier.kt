package com.ev.android.feature.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ev.android.feature.launcher.AppLauncher

/**
 * "Ye gaana kaun sa hai?"
 *
 * Audio fingerprinting khud banane ke liye ek paid service (ACRCloud jaisa)
 * chahiye hoti hai. Uske bajaye E.V wahi app khol deta hai jo ye kaam sabse
 * achha karta hai \u2014 pehle Shazam, phir Google app ka song search.
 */
object SongIdentifier {

    private const val SHAZAM = "com.shazam.android"
    private const val GOOGLE = "com.google.android.googlequicksearchbox"

    fun identify(context: Context): String {
        if (AppLauncher.launchPackage(context, SHAZAM)) {
            return "Shazam khol diya \u2014 sunne do"
        }

        // Google app ka "Search a song" shortcut.
        val googleSearch = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://search-a-song"))
            .setPackage(GOOGLE)
        if (AppLauncher.startIntent(context, googleSearch)) {
            return "Google se gaana pehchan raha hoon"
        }

        if (AppLauncher.launchPackage(context, GOOGLE)) {
            return "Google app khol diya \u2014 mic dabake 'what's this song' bolo"
        }

        val playStore = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=" + SHAZAM),
        )
        AppLauncher.startIntent(context, playStore)
        return "Gaana pehchanne ke liye Shazam install kar lo"
    }
}
