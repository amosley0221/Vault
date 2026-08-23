package com.crownedpixel.vault.install

import android.content.Context
import android.content.pm.ApplicationInfo
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

    /**
     * When the package was last installed or updated. For repositories that publish under a
     * rolling tag this is the only thing an update can be measured against.
     */
    fun lastUpdateTime(context: Context, packageName: String?): Long? =
        packageInfo(context, packageName)?.lastUpdateTime

    /** One entry per user-installed package: enough to match a repository against. */
    data class InstalledEntry(val packageName: String, val segment: String, val label: String)

    /** A single sweep of the package manager, since the sweep is the expensive part. */
    fun installedIndex(context: Context): List<InstalledEntry> {
        val manager = context.packageManager
        return runCatching { manager.getInstalledPackages(0) }.getOrNull().orEmpty()
            .mapNotNull { info ->
                val application = info.applicationInfo ?: return@mapNotNull null
                if ((application.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                InstalledEntry(
                    packageName = info.packageName,
                    segment = simplify(info.packageName.substringAfterLast('.')),
                    label = simplify(
                        runCatching { manager.getApplicationLabel(application).toString() }
                            .getOrDefault(""),
                    ),
                )
            }
    }

    /**
     * Finds the package a tracked repository probably installed, for apps that arrived on the
     * device some other way — sideloaded by hand, or installed before the repository was tracked.
     * Deliberately strict: the launcher label or the last segment of the package has to match the
     * repository name outright, so an unrelated app is never adopted.
     */
    fun match(index: List<InstalledEntry>, repo: String, displayName: String): String? {
        val wanted = setOf(simplify(repo), simplify(displayName)).filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return null
        return index.firstOrNull { entry ->
            entry.segment in wanted || (entry.label.isNotEmpty() && entry.label in wanted)
        }?.packageName
    }

    private fun simplify(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

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
