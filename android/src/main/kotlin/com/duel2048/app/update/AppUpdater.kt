package com.duel2048.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.duel2048.app.BuildConfig
import com.duel2048.app.net.LeaderboardClient
import com.duel2048.shared.protocol.AppRelease
import com.duel2048.shared.protocol.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * On startup, asks the game server if a newer APK exists. The APK must be signed with the same
 * certificate as the installed app. Android still shows one system confirm screen; a normal app
 * cannot replace itself silently.
 */
object AppUpdater {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    suspend fun checkAndInstall(context: Context, serverWsUrl: String) {
        val release = fetch(serverWsUrl).getOrNull() ?: return
        if (release.versionCode <= BuildConfig.VERSION_CODE || release.url.isBlank()) return
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            _status.value = "Allow installs from this app, then open it again."
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            return
        }
        _status.value = "Downloading ${release.versionName}…"
        val apk = download(app, serverWsUrl, release.url).getOrElse {
            _status.value = "Update download failed."
            return
        }
        _status.value = "Installing ${release.versionName}…"
        install(app, apk)
    }

    private suspend fun fetch(serverWsUrl: String): Result<AppRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val url = LeaderboardClient.httpBase(serverWsUrl) + "/app/update"
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                Protocol.json.decodeFromString(AppRelease.serializer(), response.body?.string().orEmpty())
            }
        }
    }

    private suspend fun download(context: Context, serverWsUrl: String, path: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (path.startsWith("http")) path else LeaderboardClient.httpBase(serverWsUrl) + path
            val file = File(context.cacheDir, "update.apk")
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty apk")
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            file
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, Intent(context, UpdateResultReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }
}

class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            confirm?.let { context.startActivity(it) }
        }
    }
}
