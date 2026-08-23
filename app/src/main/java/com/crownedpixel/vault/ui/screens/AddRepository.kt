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
import androidx.compose.ui.unit.em
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.Screen
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultTextField
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

@Composable
fun AddRepositoryScreen(state: VaultUiState, model: VaultViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        BasicText(
            text = "Add a repository",
            style = cinzel(18, FontWeight.W600),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicText(
            text = "Track any GitHub repository that publishes APK release assets. New releases " +
                "are checked in the background.",
            style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            modifier = Modifier.padding(bottom = 20.dp),
        )

        SectionLabel("Repository", modifier = Modifier.padding(bottom = 8.dp))
        VaultTextField(
            value = state.addValue,
            onValueChange = model::onAddValueChange,
            placeholder = "owner/repository",
            onImeAction = model::submitAdd,
        )
        state.addError?.let { error ->
            BasicText(
                text = error,
                style = jost(12, FontWeight.W300, VaultColors.Gold, lineHeight = 1.5.em),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        GoldButton(
            text = if (state.adding) "Checking…" else "Add repository",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = !state.adding,
        ) { model.submitAdd() }

        Hairline(modifier = Modifier.padding(top = 28.dp))
        SectionLabel("Or pull from", modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        PullRow(
            label = "My repositories",
            trailing = if (state.signedIn) "with APK releases" else "sign in",
        ) { model.openMinePicker() }
        PullRow(
            label = "Starred repositories with APK releases",
            trailing = if (state.signedIn) "browse" else "sign in",
            modifier = Modifier.padding(top = 10.dp),
        ) { model.openStarredPicker() }

        Hairline(modifier = Modifier.padding(top = 28.dp))
        SectionLabel("No APK published?", modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        OptionCard(
            title = "Wrap a website as an app",
            body = "For repos that are GitHub Pages sites — generates a wrapper APK that opens " +
                "the site full-screen.",
        ) { model.goTo(Screen.WRAP) }
        OptionCard(
            title = "Build with GitHub Actions",
            body = "For Android repos you own — commits a workflow that compiles the APK and " +
                "publishes it as a release.",
            modifier = Modifier.padding(top = 10.dp),
        ) { model.goTo(Screen.BUILD) }
    }
}

@Composable
private fun PullRow(
    label: String,
    trailing: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.Hairline)
            .press(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = jost(14, FontWeight.W400, VaultColors.Bone),
            modifier = Modifier.weight(1f),
        )
        BasicText(text = trailing, style = jost(11, FontWeight.W400, VaultColors.Stone))
    }
}

@Composable
private fun OptionCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.Hairline)
            .press(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        BasicText(
            text = title,
            style = jost(14, FontWeight.W400, VaultColors.Bone),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        BasicText(
            text = body,
            style = jost(11, FontWeight.W300, VaultColors.Stone, lineHeight = 1.5.em),
        )
    }
}
