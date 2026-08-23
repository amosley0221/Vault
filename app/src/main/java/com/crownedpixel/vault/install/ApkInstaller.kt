package com.crownedpixel.vault.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of a committed install session, delivered by [InstallResultReceiver]. */
data class InstallResult(
    val packageName: String?,
    val success: Boolean,
    val message: String,
)

object InstallEvents {
    val results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
}

object ApkInstaller {

    const val ACTION_INSTALL_STATUS = "com.crownedpixel.vault.INSTALL_STATUS"

    /** True when the user has granted Vault the "install unknown apps" permission. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Streams an APK into the cache directory, reporting 0f..1f as it goes. Redirects are
     * followed by hand so that the GitHub credential is never forwarded to the storage host the
     * release asset actually lives on.
     */
    suspend fun download(
        context: Context,
        url: String,
        token: String?,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "downloads").apply { mkdirs() }
            .let { File(it, "vault-${System.currentTimeMillis()}.apk") }

        var currentUrl = url
        var authorize = !token.isNullOrBlank()
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (true) {
            val open = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Vault-APK-Manager")
                if (authorize) setRequestProperty("Authorization", "Bearer $token")
            }
            val status = open.responseCode
            if (status in 300..399) {
                val location = open.getHeaderField("Location")
                open.disconnect()
                if (location.isNullOrBlank() || redirects++ > 5) {
                    throw IOException("Download redirected too many times.")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                authorize = false // The redirect target is object storage; it rejects our header.
                continue
            }
            if (status !in 200..299) {
                open.disconnect()
                throw IOException("Download failed with HTTP $status.")
            }
            connection = open
            break
        }

        val live = connection ?: throw IOException("Download failed.")
        val total = if (expectedSize > 0) expectedSize else live.contentLengthLong.coerceAtLeast(1L)
        try {
            live.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
        } finally {
            live.disconnect()
        }
        if (target.length() == 0L) {
            target.delete()
            throw IOException("The release asset was empty.")
        }
        target
    }

    /**
     * Hands the APK to the platform installer. The system asks the user to confirm unless this is
     * an update to an app Vault itself installed on Android 12 or later; either way the outcome
     * arrives on [InstallEvents].
     */
    suspend fun install(context: Context, apk: File, packageName: String?) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        packageName?.let { params.setAppPackageName(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and up will skip its own "Update this app?" prompt when the app being
            // updated was installed by us in the first place. Where the conditions are not met —
            // a first install, or an app that arrived some other way — the system quietly falls
            // back to asking, so this only ever removes a prompt that was not needed.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("vault.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val intent = Intent(ACTION_INSTALL_STATUS)
                .setPackage(context.packageName)
                .setClass(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
