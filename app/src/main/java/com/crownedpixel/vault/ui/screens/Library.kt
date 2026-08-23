package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crownedpixel.vault.data.AppStatus
import com.crownedpixel.vault.data.LibraryApp
import com.crownedpixel.vault.ui.CenteredLabel
import com.crownedpixel.vault.ui.EllipsizedText
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.Screen
import com.crownedpixel.vault.ui.StatusBadge
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

@Composable
fun LibraryScreen(state: VaultUiState, model: VaultViewModel) {
    if (state.apps.isEmpty()) {
        EmptyLibrary(state, model)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.apps, key = { it.id }) { app ->
            LibraryRow(app) { model.openDetail(app.id) }
            Hairline(color = VaultColors.Separator)
        }
        item {
            CenteredLabel(
                text = if (state.refreshing) {
                    "Checking releases…"
                } else {
                    "${state.apps.size} ${if (state.apps.size == 1) "repository" else "repositories"} tracked"
                },
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun LibraryRow(app: LibraryApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .press(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(letter = app.initial, size = 42.dp, textSize = 18)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            BasicText(text = app.name, style = jost(16, FontWeight.W500, VaultColors.Bone))
            EllipsizedText(
                text = app.tracked.slug,
                style = jost(12, FontWeight.W400, VaultColors.Stone),
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText(
                text = if (app.status == AppStatus.UPDATING) {
                    "${(app.progress * 100).toInt()}%"
                } else {
                    app.versionLine
                },
                style = cinzel(12, FontWeight.W500, VaultColors.Stone),
            )
            StatusBadge(text = app.badge, active = app.status != AppStatus.CURRENT)
        }
    }
}

@Composable
private fun EmptyLibrary(state: VaultUiState, model: VaultViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(text = "Nothing tracked yet", style = cinzel(18, FontWeight.W600))
            CenteredLabel(
                text = if (state.refreshing) "Checking releases…" else "Add a repository that publishes APKs",
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )
            GoldButton(
                text = "Add repository",
                modifier = Modifier.fillMaxWidth(),
                horizontalPadding = 16.dp,
            ) { model.goTo(Screen.ADD) }
        }
    }
}
