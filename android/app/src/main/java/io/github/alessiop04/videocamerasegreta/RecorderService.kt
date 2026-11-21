package io.github.alessiop04.videocamerasegreta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Servizio in primo piano (tipo camera|microphone) che registra con la fotocamera posteriore anche
 * a schermo spento. Il video va direttamente in galleria (Movies/Videocamera segreta) tramite MediaStore:
 * fermando la registrazione il file viene finalizzato e diventa visibile.
 */
class RecorderService : LifecycleService() {

    companion object {
        private const val TAG = "RecorderService"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_ROTATION = "rotation"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ALBUM = "Movies/Videocamera segreta"
        /** Rete di sicurezza per il wake lock: nessuna registrazione dura più di un giorno. */
        private const val MAX_RECORDING_MS = 24 * 60 * 60 * 1000L

        /** True da quando parte la registrazione a quando il servizio termina (video salvato). */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context, displayRotation: Int) {
            val intent = Intent(context, RecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROTATION, displayRotation)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }

    private var setup: Job? = null
    private var recording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Ultima rotazione letta dal sensore, finché la registrazione non parte. */
    private val sensorRotation = MutableStateFlow<Int?>(null)
    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                surfaceRotationFor(orientation)?.let { sensorRotation.value = it }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> if (!isRunning) begin(intent.getIntExtra(EXTRA_ROTATION, Surface.ROTATION_0))
            ACTION_STOP -> finishRecording()
            else -> stopSelf() // riavvio da parte del sistema: niente da riprendere
        }
        return START_NOT_STICKY
    }

    private fun begin(displayRotation: Int) {
        val audio = hasPermission(Manifest.permission.RECORD_AUDIO)
        // I tipi camera/microphone esistono da Android 11; su Android 10 non servono.
        val types = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or (if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground fallito", e)
            toast(getString(R.string.camera_failed))
            stopSelf()
            return
        }
        isRunning = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "videocamera-segreta:recording")
            .apply { acquire(MAX_RECORDING_MS) }
        if (orientationListener.canDetectOrientation()) orientationListener.enable()

        setup = lifecycleScope.launch {
            launch { Updates.checkDaily(applicationContext) }
            // Aspetta un attimo il sensore: il video esce dritto anche con la rotazione automatica spenta.
            val rotation = withTimeoutOrNull(600) { sensorRotation.filterNotNull().first() } ?: displayRotation
            try {
                startCamera(rotation, audio)
            } catch (e: Exception) {
                Log.e(TAG, "fotocamera non disponibile", e)
                toast(getString(R.string.camera_failed))
                stopSelf()
            }
        }
    }

    @SuppressLint("MissingPermission") // l'audio si abilita solo se RECORD_AUDIO è concesso (vedi begin)
    private suspend fun startCamera(rotation: Int, audio: Boolean) {
        val provider = cameraProvider()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        capture.targetRotation = rotation
        val camera = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        provider.unbindAll()
        provider.bindToLifecycle(this, camera, capture)
        orientationListener.disable()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName(LocalDateTime.now()))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, ALBUM)
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        recording = recorder.prepareRecording(this, output)
            .apply { if (audio) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(this), ::onRecordEvent)
    }

    private suspend fun cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e.cause ?: e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> toast(getString(R.string.recording_started))
            is VideoRecordEvent.Finalize -> {
                recording = null
                val saved = event.outputResults.outputUri != Uri.EMPTY
                if (event.hasError()) Log.w(TAG, "registrazione finalizzata con errore ${event.error}", event.cause)
                // Con errori come spazio esaurito o fotocamera presa da un'altra app il file è comunque salvato.
                toast(if (saved) getString(R.string.video_saved) else getString(R.string.video_failed, event.error))
                stopSelf()
            }
        }
    }

    /** Ferma la registrazione: il servizio termina quando il video è stato finalizzato (evento Finalize). */
    private fun finishRecording() {
        val current = recording
        if (current != null) {
            current.stop()
        } else {
            setup?.cancel() // fotocamera non ancora partita: non c'è niente da salvare
            stopSelf()
        }
    }

    override fun onDestroy() {
        recording?.stop()
        recording = null
        orientationListener.disable()
        wakeLock?.takeIf { it.isHeld }?.release()
        isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        // Toccare la notifica equivale a riaprire l'app: ferma e salva.
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
