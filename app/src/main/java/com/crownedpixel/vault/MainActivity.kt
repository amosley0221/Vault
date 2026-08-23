package com.crownedpixel.vault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crownedpixel.vault.install.ApkInstaller
import com.crownedpixel.vault.ui.VaultApp
import com.crownedpixel.vault.ui.VaultEvent
import com.crownedpixel.vault.ui.VaultViewModel

class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Declined notifications simply mean no release alerts. */ }

    private val openSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* The install permission is re-read the next time an install starts. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForNotifications()

        setContent {
            val model: VaultViewModel = viewModel()
            val state by model.state.collectAsState()

            LaunchedEffect(Unit) {
                intent?.getStringExtra(EXTRA_SLUG)?.let { model.openDetail(it) }
            }

            LaunchedEffect(model) {
                model.events.collect { event -> handle(event) }
            }

            VaultApp(state = state, model = model)
        }
    }

    private fun handle(event: VaultEvent) {
        when (event) {
            is VaultEvent.OpenUrl -> startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            is VaultEvent.Launch -> startActivity(event.intent)

            VaultEvent.RequestInstallPermission ->
                openSettings.launch(ApkInstaller.unknownSourcesSettingsIntent(this))
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_SLUG = "vault.slug"
    }
}
