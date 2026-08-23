package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.crownedpixel.vault.ui.GhostAction
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Screen
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.SignInMode
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultTextField
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost

@Composable
fun SignInScreen(state: VaultUiState, model: VaultViewModel) {
    val signIn = state.signIn
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (signIn.mode == SignInMode.DEVICE_FLOW) {
            SectionLabel("github.com/login/device", modifier = Modifier.padding(bottom = 14.dp))
            BasicText(
                text = "Device authorization",
                style = cinzel(20, FontWeight.W600),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            BasicText(
                text = "Enter this code on GitHub to link Vault to your account. Grants access to " +
                    "your repositories and releases, and — only when you ask for it — commits a " +
                    "build workflow.",
                style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.7.em),
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultColors.Hairline)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = signIn.userCode.ifBlank { "········" }.replace("-", "·"),
                    style = cinzel(26, FontWeight.W600, VaultColors.Gold, letterSpacing = 0.3.em),
                )
            }
            GhostAction(
                text = "Open github.com/login/device",
                modifier = Modifier.fillMaxWidth(),
                color = VaultColors.Gold,
            ) { model.openDeviceVerification() }
            GoldButton(
                text = if (signIn.busy) "Checking…" else "I've entered the code",
                modifier = Modifier.fillMaxWidth(),
                verticalPadding = 14.dp,
                enabled = !signIn.busy,
            ) { model.confirmDeviceCode() }
        } else {
            SectionLabel("github.com/settings/tokens", modifier = Modifier.padding(bottom = 14.dp))
            BasicText(
                text = "Personal access token",
                style = cinzel(20, FontWeight.W600),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            BasicText(
                text = "This build ships without an OAuth client id, so Vault links to GitHub with " +
                    "a token you create. Give it repo access — and workflow scope if you want Vault " +
                    "to commit build workflows. It is stored in the Android keystore, never synced.",
                style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.7.em),
                modifier = Modifier.padding(bottom = 24.dp),
            )
            VaultTextField(
                value = signIn.tokenValue,
                onValueChange = model::onSignInTokenChange,
                placeholder = "ghp_…",
                onImeAction = model::submitSignInToken,
            )
            GhostAction(
                text = "Create a token on GitHub",
                modifier = Modifier.fillMaxWidth(),
                color = VaultColors.Gold,
            ) { model.openTokenPage() }
            GoldButton(
                text = if (signIn.busy) "Checking…" else "Save token",
                modifier = Modifier.fillMaxWidth(),
                verticalPadding = 14.dp,
                enabled = !signIn.busy,
            ) { model.submitSignInToken() }
        }

        signIn.error?.let { error ->
            BasicText(
                text = error,
                style = jost(12, FontWeight.W300, VaultColors.Gold),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        GhostAction(
            text = "Back",
            modifier = Modifier.fillMaxWidth(),
        ) { model.goTo(if (state.preferences.onboarded) Screen.LIBRARY else Screen.ONBOARDING) }
    }
}
