package com.crownedpixel.vault.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.crownedpixel.vault.MainActivity
import com.crownedpixel.vault.R
import com.crownedpixel.vault.data.Dates
import com.crownedpixel.vault.data.GitHubApi
import com.crownedpixel.vault.data.Preferences
import com.crownedpixel.vault.data.TokenStore
import com.crownedpixel.vault.data.UpdateCheck
import com.crownedpixel.vault.data.VaultStore
import com.crownedpixel.vault.data.Versions
import com.crownedpixel.vault.install.InstalledApps
import java.util.concurrent.TimeUnit

/** Polls every tracked release feed on the cadence chosen in Settings. */
class UpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val store = VaultStore(applicationContext)
        val preferences = store.readPreferences()
        val token = TokenStore(applicationContext).readToken()
        val repos = store.readRepos()
        if (repos.isEmpty()) return Result.success()

        var updated = false
        val fresh = repos.map { repo ->
            val newest = runCatching {
                GitHubApi.latestReleaseWithApk(
                    repo.owner,
                    repo.repo,
                    token,
                    preferences.includePrereleases,
                )
            }.getOrNull() ?: return@map repo

            val installed = InstalledApps.installedVersion(applicationContext, repo.packageName)
            val installedAt = InstalledApps.lastUpdateTime(applicationContext, repo.packageName)
            val hasUpdate = UpdateCheck.isNewer(
                latestVersion = newest.version,
                latestMillis = Dates.epochMillis(newest.timestamp),
                installedVersion = installed,
                installedMillis = installedAt,
            )
            // A rolling tag never changes, so the announcement is keyed on the build it points at.
            val marker = "${newest.tag}@${newest.timestamp}"
            if (hasUpdate && preferences.notifyOnRelease && store.notifiedTag(repo.slug) != marker) {
                notify(repo.displayName, Versions.label(newest.version), repo.slug)
                store.markNotified(repo.slug, marker)
            }
            if (repo.latestVersion != newest.version || repo.latestPublishedAt != newest.timestamp) {
                updated = true
            }
            repo.copy(
                latestTag = newest.tag,
                latestVersion = newest.version,
                latestPublishedAt = newest.timestamp,
            )
        }
        if (updated) store.writeRepos(fresh)
        return Result.success()
    }

    private fun notify(name: String, tag: String, slug: String) {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            UpdateScheduler.CHANNEL_ID,
            applicationContext.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = applicationContext.getString(R.string.update_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_SLUG, slug)
        val pending = PendingIntent.getActivity(
            applicationContext,
            slug.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, UpdateScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name $tag")
            .setContentText("A new release is available on GitHub.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(slug.hashCode(), notification)
    }
}

object UpdateScheduler {

    const val CHANNEL_ID = "vault_releases"
    private const val WORK_NAME = "vault_release_check"

    fun schedule(context: Context, preferences: Preferences) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            preferences.checkIntervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
