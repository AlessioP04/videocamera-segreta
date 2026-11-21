package io.github.alessiop04.videocamerasegreta

import android.view.OrientationEventListener
import android.view.Surface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Rotazione del video a partire dall'angolo del sensore (gradi, 0..359): così il video è dritto
 * anche con la rotazione automatica dello schermo disattivata. Null se il telefono è in piano.
 */
fun surfaceRotationFor(degrees: Int): Int? = when {
    degrees == OrientationEventListener.ORIENTATION_UNKNOWN -> null
    degrees !in 0..359 -> null
    degrees in 45 until 135 -> Surface.ROTATION_270
    degrees in 135 until 225 -> Surface.ROTATION_180
    degrees in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

private val FILE_NAME = DateTimeFormatter.ofPattern("'VID_'yyyyMMdd_HHmmss")

/** Nome del file in galleria, es. VID_20261121_203015 (senza estensione: la aggiunge MediaStore). */
fun videoFileName(time: LocalDateTime): String = FILE_NAME.format(time)
