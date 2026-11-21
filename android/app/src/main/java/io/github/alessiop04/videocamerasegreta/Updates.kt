package io.github.alessiop04.videocamerasegreta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Al massimo una volta al giorno chiede a GitHub l'ultima release (API pubblica, senza token);
 * se è più recente della versione installata mostra una notifica che porta alla pagina di download.
 */
object Updates {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/AlessioP04/videocamera-segreta/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val KEY_NOTIFIED = "notified_version"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    suspend fun checkDaily(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0) < DAY_MS) return@withContext
        try {
            val conn = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "videocamera-segreta-android")
            val json = try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext
                JSONObject(conn.inputStream.reader().readText())
            } finally {
                conn.disconnect()
            }
            prefs.edit { putLong(KEY_LAST_CHECK, now) }
            val tag = json.getString("tag_name")
            if (Versions.isNewer(tag, BuildConfig.VERSION_NAME) && prefs.getString(KEY_NOTIFIED, null) != tag) {
                notify(context, tag.removePrefix("v"), json.getString("html_url"))
                prefs.edit { putString(KEY_NOTIFIED, tag) }
            }
        } catch (e: Exception) {
            Log.w("Updates", "controllo nuova versione fallito", e)
        }
    }

    private fun notify(context: Context, version: String, url: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, url.toUri()), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(context.getString(R.string.update_title, version))
            .setContentText(context.getString(R.string.update_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

object Versions {
    fun parse(v: String): List<Int> = v.trim().removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
