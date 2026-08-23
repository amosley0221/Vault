package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.crownedpixel.vault.ui.CenteredLabel
import com.crownedpixel.vault.ui.GhostAction
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.Monogram
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost

@Composable
fun OnboardingScreen(model: VaultViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Monogram(letter = "V", size = 56.dp, textSize = 26, borderColor = VaultColors.Gold)

        BasicText(
            text = "Your apps,\nfrom the source.",
            style = cinzel(28, FontWeight.W600, lineHeight = 1.25.em),
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )

        BasicText(
            text = "Vault installs and updates Android apps directly from GitHub releases — no " +
                "store in between. Track any repository, your own or starred.",
            style = jost(14, FontWeight.W300, VaultColors.Stone, lineHeight = 1.7.em),
            modifier = Modifier.padding(bottom = 36.dp),
        )

        GoldButton(
            text = "Continue with GitHub",
            modifier = Modifier.fillMaxWidth(),
            verticalPadding = 14.dp,
        ) { model.completeOnboarding(signIn = true) }

        GhostAction(
            text = "Browse without signing in",
            modifier = Modifier.fillMaxWidth(),
        ) { model.completeOnboarding(signIn = false) }

        Hairline(modifier = Modifier.padding(top = 28.dp))

        CenteredLabel(
            text = "Crowned Pixel · Sideloading requires \"install unknown apps\" permission",
            modifier = Modifier.padding(top = 14.dp),
            size = 11,
        )
    }
}
