package com.crownedpixel.vault.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.crownedpixel.vault.data.RepoCandidate
import com.crownedpixel.vault.ui.GoldButton
import com.crownedpixel.vault.ui.Hairline
import com.crownedpixel.vault.ui.ProgressTrack
import com.crownedpixel.vault.ui.Screen
import com.crownedpixel.vault.ui.SectionLabel
import com.crownedpixel.vault.ui.VaultColors
import com.crownedpixel.vault.ui.VaultUiState
import com.crownedpixel.vault.ui.VaultViewModel
import com.crownedpixel.vault.ui.cinzel
import com.crownedpixel.vault.ui.jost
import com.crownedpixel.vault.ui.press

private val STEPS = listOf(
    "I" to "Commit vault-android-build.yml to the repository",
    "II" to "GitHub Actions compiles and signs the APK",
    "III" to "Release published — Vault picks it up",
)

@Composable
fun ActionsBuildScreen(state: VaultUiState, model: VaultViewModel) {
    val build = state.build
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
            text = "Build with GitHub Actions",
            style = cinzel(18, FontWeight.W600),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicText(
            text = "Vault commits a build workflow to a repository you own. GitHub Actions " +
                "compiles the APK and publishes it as a release — from then on it updates like " +
                "any other tracked app. Requires write access.",
            style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            modifier = Modifier.padding(bottom = 20.dp),
        )

        SectionLabel("Your repos without APK releases", modifier = Modifier.padding(bottom = 10.dp))
        when {
            build.loading -> BasicText(
                text = "Reading your repositories…",
                style = jost(13, FontWeight.W300, VaultColors.Stone),
            )

            build.candidates.isEmpty() -> BasicText(
                text = build.error ?: "Nothing here needs a build workflow.",
                style = jost(13, FontWeight.W300, VaultColors.Stone, lineHeight = 1.6.em),
            )

            else -> build.candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    selected = candidate.slug == build.selected,
                ) { model.selectBuildRepo(candidate.slug) }
            }
        }

        Hairline(color = VaultColors.Separator, modifier = Modifier.padding(top = 20.dp))
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 20.dp)) {
            STEPS.forEachIndexed { index, (numeral, text) ->
                val stateLabel = when {
                    !build.busy -> ""
                    index < build.stage -> "Done"
                    index == build.stage -> "Running"
                    else -> "Queued"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    BasicText(
                        text = numeral,
                        style = cinzel(
                            13,
                            FontWeight.W500,
                            if (build.busy && index <= build.stage) VaultColors.Gold else VaultColors.Stone,
                        ),
                        modifier = Modifier.width(16.dp),
                    )
                    BasicText(
                        text = text,
                        style = jost(
                            13,
                            FontWeight.W300,
                            if (build.busy && index == build.stage) VaultColors.Bone else VaultColors.Stone,
                            lineHeight = 1.5.em,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    SectionLabel(
                        text = stateLabel,
                        color = if (stateLabel == "Running") VaultColors.Gold else VaultColors.Stone,
                        size = 9,
                    )
                }
            }
        }

        if (build.busy) {
            ProgressTrack(
                fraction = build.percent / 100f,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        } else {
            GoldButton(
                text = "Commit workflow & start build",
                modifier = Modifier.fillMaxWidth(),
                enabled = build.selected != null,
            ) { model.startBuild() }
        }

        build.error?.takeIf { build.candidates.isNotEmpty() }?.let { error ->
            BasicText(
                text = error,
                style = jost(12, FontWeight.W300, VaultColors.Gold, lineHeight = 1.5.em),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CandidateCard(candidate: RepoCandidate, selected: Boolean, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(1.dp, if (selected) VaultColors.Gold else VaultColors.Hairline)
            .press(enabled = candidate.buildable, onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
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
