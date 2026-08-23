package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crownedpixel.vault.data.RepoCandidate
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.PickerState
import com.crownedpixel.vault.ui.PullToRefresh
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

/** The full-bleed picker behind "Or pull from" on the Add screen. */
@Composable
fun RepoPickerOverlay(
    picker: PickerState,
    onPick: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Onyx),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionLabel(picker.title, color = VaultColors.Bone)
            SectionLabel(
                text = "Close",
                color = VaultColors.Gold,
                modifier = Modifier.press(onClick = onClose),
            )
        }
        Hairline()

        PullToRefresh(
            refreshing = picker.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            when {
                picker.loading -> PickerMessage(
                    text = "Reading release feeds…",
                    modifier = Modifier.weight(1f),
                )

                picker.error != null -> PickerMessage(
                    text = picker.error,
                    color = VaultColors.Gold,
                    modifier = Modifier.weight(1f),
                )

                picker.items.isEmpty() -> PickerMessage(
                    text = "Nothing here publishes an APK asset",
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(picker.items, key = { it.slug }) { candidate ->
                        PickerRow(candidate) { onPick(candidate.slug) }
                        Hairline(color = VaultColors.Separator)
                    }
                }
            }
        }
    }
}

/**
 * Messages scroll even when they fit, so the pull gesture still reaches the refresh connection on
 * an empty or failed list — which is exactly when it is wanted.
 */
@Composable
private fun PickerMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VaultColors.Stone,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = text,
            style = jost(13, FontWeight.W300, color),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 40.dp),
        )
    }
}

@Composable
private fun PickerRow(candidate: RepoCandidate, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .press(enabled = candidate.buildable, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(
                    text = candidate.slug,
                    style = jost(14, FontWeight.W400, VaultColors.Bone),
                    modifier = Modifier.weight(1f),
                )
                SectionLabel(
                    text = candidate.tag,
                    color = if (candidate.buildable) VaultColors.Gold else VaultColors.Stone,
                )
            }
            BasicText(
                text = candidate.description,
                style = jost(11, FontWeight.W300, VaultColors.Stone),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
