package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.border
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
import com.crownedpixel.vault.data.AppStatus
import com.crownedpixel.vault.data.LibraryApp
import com.crownedpixel.vault.ui.CenteredLabel
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.PullToRefresh
import com.crownedpixel.vault.ui.ProgressTrack
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

@Composable
fun UpdatesScreen(state: VaultUiState, model: VaultViewModel) {
    PullToRefresh(
        refreshing = state.refreshing,
        onRefresh = model::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val pending = state.pendingUpdates
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (pending.isEmpty()) {
                // Scrollable even when it fits, so the pull gesture still reaches the refresh.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(text = "All current", style = cinzel(18, FontWeight.W600))
                    CenteredLabel(
                        text = "Every library is up to date",
                        modifier = Modifier.padding(top = 8.dp),
                        size = 12,
                    )
                }
                return@Column
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val count = state.updateCount
                    SectionLabel("$count update${if (count == 1) "" else "s"} available")
                    GoldButton(
                        text = "Update all",
                        verticalPadding = 8.dp,
                        horizontalPadding = 16.dp,
                        labelSize = 10,
                        enabled = count > 0,
                    ) { model.updateAll() }
                }

                pending.forEach { app ->
                    UpdateCard(
                        app = app,
                        onOpen = { model.openDetail(app.id) },
                        onUpdate = { model.install(app.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(app: LibraryApp, onOpen: () -> Unit, onUpdate: () -> Unit) {
    val updating = app.status == AppStatus.UPDATING
    val queued = updating && app.progressStage == "Queued"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, VaultColors.Hairline)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The name opens the release feed: what is in the update is the reason to tap it.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .press(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(letter = app.initial, size = 36.dp, textSize = 15)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    BasicText(text = app.name, style = jost(15, FontWeight.W500, VaultColors.Bone))
                    BasicText(
                        text = "${app.installedVersion ?: "—"} → ${app.latestVersion ?: "—"}",
                        style = cinzel(11, FontWeight.W500, VaultColors.Stone),
                    )
                }
            }
            if (updating) {
                SectionLabel(
                    text = if (queued) "Queued" else "${(app.progress * 100).toInt()}%",
                    color = if (queued) VaultColors.Stone else VaultColors.Gold,
                    size = 9,
                )
            } else {
                GoldButton(
                    text = "Update",
                    verticalPadding = 7.dp,
                    horizontalPadding = 14.dp,
                    labelSize = 9,
                    onClick = onUpdate,
                )
            }
        }
        if (updating && !queued) {
            ProgressTrack(
                fraction = app.progress,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
