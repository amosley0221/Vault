package com.crownedpixel.vault.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives the outcome of a [PackageInstaller] session. The first callback is normally
 * STATUS_PENDING_USER_ACTION, which carries the system's confirmation dialog for us to launch.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                confirmation?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> emit(packageName, true, "Installed")

            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                emit(packageName, false, describe(status, detail))
            }
        }
    }

    private fun emit(packageName: String?, success: Boolean, message: String) {
        InstallEvents.results.tryEmit(InstallResult(packageName, success, message))
    }

    private fun describe(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install blocked by the system"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicts with the installed version — signature mismatch"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK is not compatible with this device"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The APK is malformed"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install"
        else -> detail?.takeIf { it.isNotBlank() } ?: "Install failed"
    }
}
