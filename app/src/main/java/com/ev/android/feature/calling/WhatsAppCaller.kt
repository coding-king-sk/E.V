package com.ev.android.feature.calling

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.ev.android.feature.launcher.AppLauncher

/**
 * WhatsApp voice/video call.
 *
 * WhatsApp ka koi public deep link call ke liye nahi hai. Jo cheez chalti hai
 * wo ye: WhatsApp har contact ke saath phone ki contact list me apni ek row
 * jodta hai ("Voice call" wali). Us row ka id nikaal ke usi pe ACTION_VIEW
 * bhejna padta hai \u2014 bilkul waise hi jaise Contacts app karti hai.
 *
 * Iska matlab: contact WhatsApp me hona chahiye, warna row milegi hi nahi.
 */
object WhatsAppCaller {

    private const val VOICE_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private const val VIDEO_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

    /** @return false agar contact ki WhatsApp call wali row nahi mili. */
    fun call(context: Context, name: String, video: Boolean): Boolean {
        val mime = if (video) VIDEO_MIME else VOICE_MIME
        val id = findDataId(context, name, mime) ?: return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id),
                mime,
            )
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return AppLauncher.startIntent(context, intent)
    }

    private fun findDataId(context: Context, name: String, mime: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null

        // Permission na ho to Android SecurityException phenkta hai \u2014 crash ki
        // jagah seedha "nahi mila" bolna behtar hai.
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                ContactsContract.Data.MIMETYPE + " = ? AND " +
                    ContactsContract.Data.DISPLAY_NAME_PRIMARY + " LIKE ?",
                arrayOf(mime, "%$clean%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }
}
