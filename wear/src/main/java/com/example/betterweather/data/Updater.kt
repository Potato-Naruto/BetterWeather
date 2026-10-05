package com.example.betterweather.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.example.betterweather.data.providers.httpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Available(val version: String, val apkUrl: String) : UpdateStatus
    data class Downloading(val version: String) : UpdateStatus
    data object NeedsPermission : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Manual update check against the GitHub releases of this project. Nothing happens in the background:
 * [check] only looks, and [downloadAndInstall] only runs after the user taps the button.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Potato-Naruto/BetterWeather/releases/latest"

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    private fun parts(v: String) = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()

    private fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun check(context: Context) {
        if (_status.value is UpdateStatus.Checking || _status.value is UpdateStatus.Downloading) return
        _status.value = UpdateStatus.Checking
        _status.value = try {
            val local = installedVersion(context)
            val rel = httpJson(LATEST, mapOf("Accept" to "application/vnd.github+json"))
            val tag = rel.getString("tag_name").removePrefix("v")
            val assets = rel.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (name.endsWith(".apk") && "wear" in name.lowercase()) { apk = a.getString("browser_download_url"); break }
            }
            when {
                !isNewer(tag, local) -> UpdateStatus.UpToDate(local)
                apk == null -> UpdateStatus.Failed("v$tag has no watch APK")
                else -> UpdateStatus.Available(tag, apk)
            }
        } catch (e: Exception) {
            UpdateStatus.Failed(e.message?.take(80) ?: "Couldn't check")
        }
    }

    /** Downloads the APK and hands it to the system installer, which asks the user to confirm. */
    suspend fun downloadAndInstall(context: Context, update: UpdateStatus.Available) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _status.value = UpdateStatus.NeedsPermission
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        _status.value = UpdateStatus.Downloading(update.version)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val out = File(dir, "update.apk")
                val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 20_000
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                out
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            _status.value = update
        } catch (e: Exception) {
            _status.value = UpdateStatus.Failed(e.message?.take(80) ?: "Download failed")
        }
    }
}
