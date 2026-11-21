package io.github.alessiop04.videocamerasegreta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Nessuna interfaccia: aprire l'app avvia la registrazione (o la ferma e salva il video, se è già in corso)
 * e l'activity si chiude subito. La registrazione vive in [RecorderService].
 */
class MainActivity : ComponentActivity() {

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.CAMERA)) {
            startRecording()
        } else {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            // "Non chiedere più": il dialogo non compare più, si può solo concederlo dalle impostazioni.
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ricreata durante la richiesta dei permessi (es. rotazione): il risultato arriverà al launcher.
        if (savedInstanceState != null) return

        if (RecorderService.isRunning) {
            RecorderService.stop(this)
            finish()
            return
        }
        val missing = requiredPermissions().filterNot(::granted)
        if (missing.isEmpty()) {
            startRecording()
            finish()
        } else {
            askPermissions.launch(missing.toTypedArray())
        }
    }

    private fun startRecording() {
        @Suppress("DEPRECATION")
        val displayRotation = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay)
            ?.rotation ?: Surface.ROTATION_0
        RecorderService.start(this, displayRotation)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions() = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO) // facoltativo: senza, il video è muto
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) // facoltativo
    }
}
