package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.crownedpixel.vault.data.AppStatus
import com.crownedpixel.vault.data.Dates
import com.crownedpixel.vault.data.LibraryApp
import com.crownedpixel.vault.ui.GhostAction
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.ProgressRow
import com.crownedpixel.vault.ui.QuietBox
import com.crownedpixel.vault.ui.Screen
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.StatColumn
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

@Composable
fun AppDetailScreen(state: VaultUiState, model: VaultViewModel) {
    val app = state.detail ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SectionLabel(
            text = "← Library",
            modifier = Modifier
                .press { model.goTo(Screen.LIBRARY) }
                .padding(bottom = 18.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(letter = app.initial, size = 56.dp, textSize = 24)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                BasicText(text = app.name, style = cinzel(22, FontWeight.W600))
                BasicText(
                    text = "github.com/${app.tracked.slug}",
                    style = jost(12, FontWeight.W400, VaultColors.Stone),
                )
            }
        }

        if (app.tracked.description.isNotBlank()) {
            BasicText(
                text = app.tracked.description,
                style = jost(14, FontWeight.W300, VaultColors.Bone, lineHeight = 1.6.em),
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )
        }

        Hairline(modifier = Modifier.padding(top = 18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatColumn("Installed", app.installedVersion?.let { "v$it" } ?: "—")
            StatColumn("Latest", app.latestVersion?.let { "v$it" } ?: "—", VaultColors.Gold)
            StatColumn("Published", app.publishedLabel)
        }
        Hairline(modifier = Modifier.padding(bottom = 18.dp))

        when (app.status) {
            AppStatus.UPDATING -> ProgressRow(
                stage = app.progressStage.ifBlank { "Downloading APK" },
                percent = (app.progress * 100).toInt(),
                modifier = Modifier.padding(bottom = 18.dp),
            )

            AppStatus.UPDATE, AppStatus.NOT_INSTALLED -> GoldButton(
                text = if (app.status == AppStatus.NOT_INSTALLED) {
                    "Install v${app.latestVersion ?: "—"}"
                } else {
                    "Update to v${app.latestVersion ?: "—"}"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) { model.install(app.id) }

            AppStatus.CURRENT -> QuietBox(
                text = "Up to date",
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }

        SectionLabel("Releases", modifier = Modifier.padding(bottom = 12.dp))
        if (app.releases.isEmpty()) {
            BasicText(
                text = "No release feed cached yet.",
                style = jost(13, FontWeight.W300, VaultColors.Stone),
            )
        }
        app.releases.take(8).forEach { release ->
            Hairline(color = VaultColors.Separator)
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicText(text = release.tag, style = cinzel(15, FontWeight.W500))
                    BasicText(
                        text = Dates.long(release.publishedAt),
                        style = jost(11, FontWeight.W400, VaultColors.Stone),
                    )
                }
                release.noteLines().forEach { note ->
                    Row(modifier = Modifier.padding(bottom = 2.dp)) {
                        BasicText(text = "·", style = jost(13, FontWeight.W400, VaultColors.Gold))
                        BasicText(
                            text = note,
                            style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        Hairline(modifier = Modifier.padding(top = 18.dp))
        DetailActions(app, model)
    }
}

@Composable
private fun DetailActions(app: LibraryApp, model: VaultViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GhostAction(text = "Open on GitHub", color = VaultColors.Stone) {
            model.openOnGitHub(app.tracked.slug)
        }
        if (app.status == AppStatus.CURRENT) {
            GhostAction(text = "Launch", color = VaultColors.Gold) { model.launch(app.tracked.slug) }
        }
        GhostAction(text = "Untrack", color = VaultColors.Stone) { model.remove(app.tracked.slug) }
    }
}
