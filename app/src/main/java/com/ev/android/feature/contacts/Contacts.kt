package com.ev.android.feature.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val name: String, val phone: String)

object PhoneNumbers {

    /** Default country code used when a saved number has no prefix. */
    private const val DEFAULT_COUNTRY_CODE = "91"

    /** Turns "+91 98765-43210", "098765 43210", "9876543210" into "919876543210". */
    fun normalize(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length < 10 -> null
            digits.length == 10 -> DEFAULT_COUNTRY_CODE + digits
            digits.length == 11 && digits.startsWith("0") ->
                DEFAULT_COUNTRY_CODE + digits.drop(1)
            else -> digits
        }
    }

    /** True when the user literally spoke/typed a phone number instead of a name. */
    fun looksLikeNumber(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        return digits.length >= 10 && raw.none { it.isLetter() }
    }
}

object ContactsRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Looks a contact up by spoken name.
     * Ranking: exact match -> starts with -> contains.
     */
    suspend fun findByName(context: Context, name: String): Contact? =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext null

            val needle = name.trim().lowercase()
            if (needle.isEmpty()) return@withContext null

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )

            val matches = mutableListOf<Contact>()

            runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$needle%"),
                    null,
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow(projection[0])
                    val numberIndex = cursor.getColumnIndexOrThrow(projection[1])
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameIndex) ?: continue
                        val rawNumber = cursor.getString(numberIndex) ?: continue
                        val normalized = PhoneNumbers.normalize(rawNumber) ?: continue
                        matches += Contact(displayName, normalized)
                    }
                }
            }

            val unique = matches.distinctBy { it.phone }

            unique.firstOrNull { it.name.lowercase() == needle }
                ?: unique.firstOrNull { it.name.lowercase().startsWith(needle) }
                ?: unique.firstOrNull()
        }
}
