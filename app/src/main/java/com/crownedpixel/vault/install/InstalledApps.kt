package com.crownedpixel.vault.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Everything Vault knows about packages already on the device. */
object InstalledApps {

    fun packageInfo(context: Context, packageName: String?): PackageInfo? {
        if (packageName.isNullOrBlank()) return null
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun installedVersion(context: Context, packageName: String?): String? =
        packageInfo(context, packageName)?.versionName

    fun isInstalled(context: Context, packageName: String?): Boolean =
        packageInfo(context, packageName) != null

    fun launchIntent(context: Context, packageName: String?) =
        packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }

    /** Reads the manifest of a downloaded APK without installing it. */
    fun archiveInfo(context: Context, apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, signingFlags())

    /**
     * Android refuses to upgrade a package whose signing certificate changed, and an APK
     * downloaded from a hijacked release would fail exactly that way — so Vault checks before
     * spending the user's time on an install prompt that cannot succeed.
     */
    fun signatureMatchesInstalled(context: Context, apk: File, packageName: String): Boolean {
        val installed = certificateHashes(packageInfo(context, packageName, signingFlags()))
        if (installed.isEmpty()) return true // Not installed, or hashes unavailable: nothing to contradict.
        val incoming = certificateHashes(archiveInfo(context, apk))
        if (incoming.isEmpty()) return true
        return installed.intersect(incoming).isNotEmpty()
    }

    private fun packageInfo(context: Context, packageName: String, flags: Int): PackageInfo? = try {
        context.packageManager.getPackageInfo(packageName, flags)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun certificateHashes(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures ?: emptyArray()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.filterNotNull()
            .map { signature -> digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
            .toSet()
    }
}
