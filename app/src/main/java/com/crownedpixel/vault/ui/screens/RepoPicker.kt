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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crownedpixel.vault.data.RepoCandidate
import com.crownedpixel.vault.ui.CenteredLabel
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.PickerState
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

/** The full-bleed picker behind "Or pull from" on the Add screen. */
@Composable
fun RepoPickerOverlay(
    picker: PickerState,
    onPick: (String) -> Unit,
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

        when {
            picker.loading -> CenteredLabel(
                text = "Reading release feeds…",
                modifier = Modifier.padding(top = 40.dp),
            )

            picker.error != null -> BasicText(
                text = picker.error,
                style = jost(13, FontWeight.W300, VaultColors.Gold),
                modifier = Modifier.padding(20.dp),
            )

            picker.items.isEmpty() -> CenteredLabel(
                text = "Nothing here publishes an APK asset",
                modifier = Modifier.padding(top = 40.dp),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(picker.items, key = { it.slug }) { candidate ->
                    PickerRow(candidate) { onPick(candidate.slug) }
                    Hairline(color = VaultColors.Separator)
                }
            }
        }
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
