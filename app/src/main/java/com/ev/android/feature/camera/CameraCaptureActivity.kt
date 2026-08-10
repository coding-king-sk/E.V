package com.ev.android.feature.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.ev.android.feature.tts.Speaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Photo lo" ka asli kaam karne wali activity.
 *
 * Ye jaan-boojh ke bahut chhoti aur bina button ki hai: khulti hai, camera
 * warm-up ke liye ek pal rukti hai, click/record karti hai, gallery me save
 * karti hai, bol ke batati hai, aur band ho jati hai.
 *
 * Preview isliye dikhate hain ki (a) user ko pata rahe camera on hai — chupke
 * se photo lena galat hai, aur (b) kai phones bina preview ke capture hi nahi
 * karte.
 */
class CameraCaptureActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            done("Camera ki permission nahi hai \u2014 Settings me de do")
            return
        }

        val mode = intent.getStringExtra(CameraCapture.EXTRA_MODE) ?: CameraCapture.MODE_PHOTO
        val front = intent.getBooleanExtra(CameraCapture.EXTRA_FRONT, false)
        val seconds = intent.getIntExtra(CameraCapture.EXTRA_SECONDS, DEFAULT_VIDEO_SECONDS)

        bindCamera(previewView, mode, front, seconds)
    }

    private fun bindCamera(
        previewView: PreviewView,
        mode: String,
        front: Boolean,
        seconds: Int,
    ) {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            val provider = runCatching { future.get() }.getOrNull()
            if (provider == null) {
                done("Camera chalu nahi ho paya")
                return@addListener
            }

            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val bound = runCatching {
                provider.unbindAll()

                if (mode == CameraCapture.MODE_VIDEO) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                androidx.camera.video.FallbackStrategy
                                    .lowerQualityOrHigherThan(Quality.SD),
                            )
                        )
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    videoCapture = capture
                    provider.bindToLifecycle(this, selector, preview, capture)
                } else {
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    provider.bindToLifecycle(this, selector, preview, capture)
                }
            }.isSuccess

            if (!bound) {
                done("Camera chalu nahi ho paya")
                return@addListener
            }

            // Thoda rukna zaroori hai \u2014 warna auto-focus aur exposure set hone se
            // pehle hi photo khinch jati hai aur dhundhli aati hai.
            handler.postDelayed({
                if (mode == CameraCapture.MODE_VIDEO) startRecording(seconds)
                else capturePhoto()
            }, WARMUP_MS)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return done("Camera chalu nahi ho paya")
        val name = "EV_" + timestamp()

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/E.V")
            }
        }

        val options = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    done("Photo le li \u2014 gallery me save ho gayi")
                }

                override fun onError(exception: ImageCaptureException) {
                    done("Photo nahi le paya")
                }
            },
        )
    }

    private fun startRecording(seconds: Int) {
        val capture = videoCapture ?: return done("Camera chalu nahi ho paya")
        val safeSeconds = seconds.coerceIn(3, MAX_VIDEO_SECONDS)
        val name = "EV_" + timestamp()

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/E.V")
            }
        }

        val options = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        val audioAllowed = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val started = runCatching {
            val pending = capture.output.prepareRecording(this, options)
            if (audioAllowed) {
                @Suppress("MissingPermission")
                pending.withAudioEnabled()
            }

            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) done("Video save nahi ho paya")
                    else done("Video ban gaya \u2014 gallery me save ho gaya")
                }
            }
        }.isSuccess

        if (!started) {
            done("Video shuru nahi ho paya")
            return
        }

        Speaker.speak(safeSeconds.toString() + " second ki recording shuru")

        handler.postDelayed({
            runCatching { recording?.stop() }
        }, safeSeconds * 1000L)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Ek hi baar bolna aur ek hi baar band hona — callbacks do baar aa sakte hain. */
    private fun done(message: String) {
        if (finished) return
        finished = true

        Speaker.init(applicationContext)
        Speaker.speak(message)

        handler.postDelayed({ finish() }, 400L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { recording?.stop() }
        recording = null
        super.onDestroy()
    }

    private companion object {
        const val WARMUP_MS = 1200L
        const val DEFAULT_VIDEO_SECONDS = 15
        const val MAX_VIDEO_SECONDS = 300
    }
}
