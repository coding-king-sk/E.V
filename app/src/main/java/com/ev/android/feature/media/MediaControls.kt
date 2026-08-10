package com.ev.android.feature.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

enum class MediaAction {
    NEXT,
    PREVIOUS,
    PLAY_PAUSE,
    PAUSE,
    PLAY,
    FORWARD,
    REWIND,
    STOP,
}

/**
 * Kisi bhi music/video app ko control karta hai \u2014 YouTube, Spotify, Gaana, sab.
 *
 * Trick: hum media **button** ka event bhejte hain, bilkul waise jaise headphone
 * ka button dabaya ho. Jo bhi app abhi play kar rahi hai wahi usse pakadti hai.
 * Isliye kisi app-specific integration ki zaroorat nahi, aur koi permission bhi
 * nahi lagti.
 */
object MediaControls {

    fun perform(context: Context, action: MediaAction): Boolean {
        val keyCode = when (action) {
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            MediaAction.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            MediaAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }
        return dispatch(context, keyCode)
    }

    private fun dispatch(context: Context, keyCode: Int): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false

        return runCatching {
            val now = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0),
            )
            audio.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0),
            )
            true
        }.getOrDefault(false)
    }
}
