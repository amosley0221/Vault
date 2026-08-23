package com.crownedpixel.vault

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.crownedpixel.vault.data.VaultStore
import com.crownedpixel.vault.work.UpdateScheduler

class VaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UpdateScheduler.CHANNEL_ID,
                getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.update_channel_description)
            },
        )
        UpdateScheduler.schedule(this, VaultStore(this).readPreferences())
    }
}
