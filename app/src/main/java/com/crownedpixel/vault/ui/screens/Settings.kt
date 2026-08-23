package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.crownedpixel.vault.BuildConfig
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultToggle
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

@Composable
fun SettingsScreen(state: VaultUiState, model: VaultViewModel) {
    val preferences = state.preferences
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        SectionLabel("Account", modifier = Modifier.padding(bottom = 12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VaultColors.Hairline)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(letter = state.login?.take(1) ?: "·", size = 36.dp, textSize = 15)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                BasicText(
                    text = state.login ?: "Not signed in",
                    style = jost(14, FontWeight.W400, VaultColors.Bone),
                )
                BasicText(
                    text = if (state.signedIn) {
                        "Repos, releases, stars · 5,000 checks/hr"
                    } else {
                        "Public repositories only · 60 checks/hr"
                    },
                    style = jost(11, FontWeight.W400, VaultColors.Stone),
                )
            }
            SectionLabel(
                text = if (state.signedIn) "Sign out" else "Sign in",
                color = VaultColors.Gold,
                modifier = Modifier.press {
                    if (state.signedIn) model.signOut() else model.beginSignIn()
                },
            )
        }

        SectionLabel(
            text = "Background checks",
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(6, 12, 24).forEach { hours ->
                val active = preferences.checkIntervalHours == hours
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (active) VaultColors.Gold else VaultColors.Hairline)
                        .press { model.setInterval(hours) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SectionLabel(
                        text = "${hours}h",
                        color = if (active) VaultColors.Gold else VaultColors.Stone,
                        size = 11,
                    )
                }
            }
        }
        BasicText(
            text = "Release feeds are polled on this cadence. Uses the GitHub API — " +
                "unauthenticated checks are limited to 60 per hour.",
            style = jost(12, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        SectionLabel("Preferences", modifier = Modifier.padding(bottom = 12.dp))
        PreferenceRow(
            label = "Include pre-releases",
            sub = "Offer alpha and beta tags as updates",
            checked = preferences.includePrereleases,
            onToggle = model::togglePrerelease,
        )
        PreferenceRow(
            label = "Download on Wi-Fi only",
            sub = "Queue background checks until an unmetered network",
            checked = preferences.wifiOnly,
            onToggle = model::toggleWifiOnly,
        )
        PreferenceRow(
            label = "Notify on new releases",
            sub = "Silent notification per repository",
            checked = preferences.notifyOnRelease,
            onToggle = model::toggleNotify,
        )

        SectionLabel(
            text = "Another device",
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        BasicText(
            text = "Vault keeps nothing in the cloud, so a second device is set up from a file you " +
                "export here and open there. Tracked repositories and preferences always travel; " +
                "the GitHub token only when you say so.",
            style = jost(12, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        PreferenceRow(
            label = "Include sign-in token",
            sub = "Anyone holding the file can then act as you on GitHub",
            checked = state.exportWithToken,
            onToggle = { model.setExportWithToken(!state.exportWithToken) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GoldButton(
                text = if (state.transferBusy) "Working…" else "Export library",
                modifier = Modifier.weight(1f),
                enabled = !state.transferBusy,
                labelSize = 10,
                horizontalPadding = 8.dp,
            ) { model.exportLibrary() }
            GoldButton(
                text = "Import library",
                modifier = Modifier.weight(1f),
                enabled = !state.transferBusy,
                labelSize = 10,
                horizontalPadding = 8.dp,
            ) { model.beginImport() }
        }

        Hairline(modifier = Modifier.padding(top = 24.dp))
        BasicText(
            text = "Vault v${BuildConfig.VERSION_NAME} · Personal access tokens are stored in the " +
                "Android keystore, never synced.",
            style = jost(11, FontWeight.W400, VaultColors.Stone, lineHeight = 1.7.em),
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun PreferenceRow(label: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .press(onClick = onToggle)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = label, style = jost(14, FontWeight.W400, VaultColors.Bone))
                BasicText(text = sub, style = jost(11, FontWeight.W400, VaultColors.Stone))
            }
            VaultToggle(checked = checked)
        }
        Hairline(color = VaultColors.Separator)
    }
}
