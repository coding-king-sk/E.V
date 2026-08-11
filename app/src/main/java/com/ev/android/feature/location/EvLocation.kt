package com.ev.android.feature.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.ev.android.feature.permissions.AppPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Meri location batao".
 *
 * Yahan Google Play Services (FusedLocationProvider) jaan-boojh ke nahi liya.
 * Poori app abhi tak plain Android pe chal rahi hai \u2014 ek nayi bhaari
 * dependency sirf ek sawaal ke jawab ke liye theek nahi. Iska nateeja ye hai
 * ki hum last known location padhte hain, live GPS fix nahi lete: jawab turant
 * aata hai, par agar phone ne kaafi der se location nahi li to wo purani ho
 * sakti hai. Isliye jawab me location ki umar bhi batayi jati hai.
 */
object EvLocation {

    /** Isse purani location ko \"purani\" bol ke batate hain. */
    private const val STALE_AFTER_MS = 10 * 60 * 1000L

    suspend fun describe(context: Context): String = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            return@withContext "Location ki permission chahiye \u2014 Settings me " +
                "Permissions se de do"
        }

        val location = lastKnown(context)
            ?: return@withContext "Location abhi nahi mili. Phone ki location on " +
                "karo, ek baar Maps khol ke band karo, phir poochho."

        val place = addressOf(context, location)
        val age = System.currentTimeMillis() - location.time

        val where = place ?: String.format(
            Locale.US,
            "%.5f, %.5f",
            location.latitude,
            location.longitude,
        )

        if (age > STALE_AFTER_MS) {
            "Aap shayad yahan ho: $where (ye location " + minutes(age) + " purani hai)"
        } else {
            "Aap yahan ho: $where"
        }
    }

    /**
     * Sirf lat/long \u2014 mausam jaise features ke liye.
     *
     * Ye blocking hai (disk aur system service padhta hai), isliye ise IO
     * thread se hi bulana.
     */
    fun coordinates(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val location = lastKnown(context) ?: return null
        return location.latitude to location.longitude
    }

    /**
     * Sirf shehar/mohalle ka naam, poora pata nahi.
     *
     * Mausam bataate waqt \"Indore me abhi 31\u00B0\" bolna accha lagta hai. Naam na
     * mile to null \u2014 tab bina jagah ke hi jawab chala jata hai.
     */
    fun shortPlace(context: Context): String? {
        if (!hasPermission(context)) return null
        val location = lastKnown(context) ?: return null
        return addressOf(context, location)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
    }

    private fun hasPermission(context: Context): Boolean =
        AppPermissions.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            AppPermissions.isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Har provider se aakhri location le kar sabse nayi chun lete hain.
     *
     * SuppressLint isliye chalta hai ki permission upar [hasPermission] me check
     * hoti hai, aur poora kaam runCatching me hai \u2014 kuch phones permission hone
     * par bhi SecurityException fenk dete hain.
     */
    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        return providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Lat/long ko mohalle ke naam me badalna.
     *
     * Ye internet maangta hai aur bahut jagah fail hota hai, isliye null aana
     * bilkul normal hai \u2014 aise me hum seedha coordinates bata dete hain.
     */
    private fun addressOf(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null

        val address = runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return null

        val parts = listOfNotNull(
            address.subLocality,
            address.locality ?: address.subAdminArea,
            address.adminArea,
        ).distinct()

        return parts.joinToString(", ").ifBlank { null }
    }

    private fun minutes(millis: Long): String {
        val mins = millis / 60000L
        return if (mins < 60) "$mins minute" else "${mins / 60} ghante"
    }
}
