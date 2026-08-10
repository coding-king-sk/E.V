package com.ev.android.feature.camera

import android.content.Context
import android.content.Intent

/**
 * Camera chalane ka chhota sa darwaza.
 *
 * Asli kaam [CameraCaptureActivity] karti hai. Wajah: CameraX ko ek
 * LifecycleOwner chahiye hota hai, jo service ya plain function ke paas nahi
 * hota. Isliye ek patli si activity khulti hai, photo/video leti hai, aur khud
 * band ho jati hai — user ko bas ek jhalak dikhti hai.
 */
object CameraCapture {

    const val EXTRA_MODE = "mode"
    const val EXTRA_FRONT = "front"
    const val EXTRA_SECONDS = "seconds"

    const val MODE_PHOTO = "photo"
    const val MODE_VIDEO = "video"

    fun takePhoto(context: Context, front: Boolean): Boolean =
        launch(context, MODE_PHOTO, front, 0)

    fun recordVideo(context: Context, front: Boolean, seconds: Int): Boolean =
        launch(context, MODE_VIDEO, front, seconds)

    private fun launch(context: Context, mode: String, front: Boolean, seconds: Int): Boolean {
        val intent = Intent(context, CameraCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_FRONT, front)
            putExtra(EXTRA_SECONDS, seconds)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
