package com.ev.android.feature.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wo photo aur video jo E.V ne khud li hain.
 *
 * Poori gallery jaan bujh ke nahi dikhayi — uske liye storage permission
 * maangni padti ("ye app meri saari photos kyun dekh rahi hai?"), aur phone me
 * pehle se ek gallery app hoti hi hai. Yahan sirf `Pictures/E.V` aur
 * `Movies/E.V` — jo hum khud banate hain, isliye koi permission nahi chahiye.
 */
object EvGallery {

    private const val FOLDER = "E.V"

    data class Item(
        val uri: Uri,
        val name: String,
        val at: Long,
        val isVideo: Boolean,
    )

    suspend fun load(context: Context): List<Item> = withContext(Dispatchers.IO) {
        runCatching {
            (query(context, video = false) + query(context, video = true))
                .sortedByDescending { it.at }
        }.getOrDefault(emptyList())
    }

    private fun query(context: Context, video: Boolean): List<Item> {
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            pathColumn,
        )

        val result = mutableListOf<Item>()

        context.contentResolver.query(
            collection,
            projection,
            "$pathColumn LIKE ?",
            arrayOf("%$FOLDER%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                result.add(
                    Item(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        name = cursor.getString(nameIndex).orEmpty(),
                        // MediaStore seconds me deta hai, Date ko millis chahiye.
                        at = cursor.getLong(dateIndex) * 1000L,
                        isVideo = video,
                    )
                )
            }
        }

        return result
    }

    /** Thumbnail. Purane Android pe null — wahan emoji dikh jayega. */
    suspend fun thumbnail(context: Context, uri: Uri): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(320, 320), null).asImageBitmap()
            }.getOrNull()
        }

    /** Phone ki apni gallery/player me kholo. */
    fun open(context: Context, item: Item) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
