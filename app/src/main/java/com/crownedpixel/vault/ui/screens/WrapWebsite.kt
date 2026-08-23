package com.crownedpixel.vault.ui.screens

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
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.ProgressRow
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
fun WrapWebsiteScreen(state: VaultUiState, model: VaultViewModel) {
    val wrap = state.wrap
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        SectionLabel(
            text = "← Add",
            modifier = Modifier
                .press { model.goTo(Screen.ADD) }
                .padding(bottom = 18.dp),
        )
        BasicText(
            text = "Wrap a website",
            style = cinzel(18, FontWeight.W600),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicText(
            text = "Vault commits a workflow that generates a small wrapper APK — your site, " +
                "full-screen, with its own icon and launcher entry. The site itself stays on " +
                "GitHub Pages; the wrapper is rebuilt only when you change name or icon.",
            style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            modifier = Modifier.padding(bottom = 20.dp),
        )

        SectionLabel("Repository or Pages URL", modifier = Modifier.padding(bottom = 8.dp))
        VaultTextField(
            value = wrap.repoValue,
            onValueChange = model::onWrapRepoChange,
            placeholder = "owner/repository",
        )
        if (wrap.detecting) {
            BasicText(
                text = "Looking for a Pages site…",
                style = jost(12, FontWeight.W300, VaultColors.Stone),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            wrap.siteUrl?.let { url ->
                BasicText(
                    text = "Pages site detected · ${url.removePrefix("https://").trimEnd('/')}",
                    style = jost(12, FontWeight.W300, VaultColors.Gold),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionLabel("App name", modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        VaultTextField(
            value = wrap.appName,
            onValueChange = model::onWrapNameChange,
            placeholder = "Launcher name",
        )

        Row(
            modifier = Modifier.padding(top = 18.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(
                letter = wrap.appName.trim().take(1).ifBlank { "·" },
                size = 48.dp,
                textSize = 20,
            )
            BasicText(
                text = "Icon from the site's web manifest when present, or a monogram like this one.",
                style = jost(12, FontWeight.W300, VaultColors.Stone, lineHeight = 1.5.em),
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        if (wrap.busy) {
            ProgressRow(
                stage = wrap.stage.ifBlank { "Generating wrapper" },
                percent = wrap.percent,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        } else {
            GoldButton(
                text = "Generate wrapper APK",
                modifier = Modifier.fillMaxWidth(),
            ) { model.startWrap() }
        }

        wrap.error?.let { error ->
            BasicText(
                text = error,
                style = jost(12, FontWeight.W300, VaultColors.Gold, lineHeight = 1.5.em),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
