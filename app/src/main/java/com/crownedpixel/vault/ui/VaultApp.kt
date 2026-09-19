package com.crownedpixel.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crownedpixel.vault.ui.screens.ActionsBuildScreen
import com.crownedpixel.vault.ui.screens.AddRepositoryScreen
import com.crownedpixel.vault.ui.screens.AppDetailScreen
import com.crownedpixel.vault.ui.screens.LibraryScreen
import com.crownedpixel.vault.ui.screens.OnboardingScreen
import com.crownedpixel.vault.ui.screens.RepoPickerOverlay
import com.crownedpixel.vault.ui.screens.SettingsScreen
import com.crownedpixel.vault.ui.screens.SignInScreen
import com.crownedpixel.vault.ui.screens.UpdatesScreen
import com.crownedpixel.vault.ui.screens.WrapWebsiteScreen

@Composable
fun VaultApp(state: VaultUiState, model: VaultViewModel) {
    BackHandler(enabled = state.screen != Screen.LIBRARY && state.screen != Screen.ONBOARDING) {
        model.back()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultColors.Onyx),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            when (state.screen) {
                Screen.ONBOARDING -> OnboardingScreen(model)
                Screen.SIGN_IN -> SignInScreen(state, model)
                else -> InApp(state, model)
            }
        }

        state.picker?.let { picker ->
            RepoPickerOverlay(
                picker = picker,
                onPick = model::pickFromPicker,
                onRefresh = model::refreshPicker,
                onClose = model::closePicker,
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            )
        }
    }
}

@Composable
private fun ColumnScope.InApp(state: VaultUiState, model: VaultViewModel) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        // A width test rather than a device test: the same layout serves an unfolded foldable, a
        // tablet, and any phone held in landscape, and folds back on the cover screen. Read the
        // width here: Compose's layout scopes are DSL-marked, so it is out of reach once nested.
        val available = maxWidth
        val wide = available >= WIDE_BREAKPOINT
        val listAndDetail = state.screen == Screen.LIBRARY || state.screen == Screen.DETAIL

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(if (wide && listAndDetail) "Library" else state.headerLabel)
            Hairline()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (wide && listAndDetail && state.apps.isNotEmpty()) {
                    TwoPaneLibrary(state, model, available)
                } else {
                    SinglePane(state, model, wide)
                }
            }

            state.notice?.let { message ->
                Notice(message) { model.dismissNotice() }
            }

            TabBar(state, model)
        }
    }
}

/** The list keeps its own pane; the detail for whatever is selected fills the rest. */
@Composable
private fun TwoPaneLibrary(state: VaultUiState, model: VaultViewModel, available: Dp) {
    val listWidth = (available * 0.36f).coerceIn(300.dp, 420.dp)
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(listWidth)) {
            LibraryScreen(state, model, selectedId = state.detail?.id)
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(VaultColors.Hairline),
        )
        Box(modifier = Modifier.weight(1f)) {
            AppDetailScreen(state, model, showBackLink = false)
        }
    }
}

/**
 * One screen at a time. On a wide display the reading measure is capped and centred, since a
 * settings toggle stretched across eight inches is nobody's idea of an improvement.
 */
@Composable
private fun SinglePane(state: VaultUiState, model: VaultViewModel, wide: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = if (wide) Modifier.widthIn(max = 760.dp) else Modifier) {
            when (state.screen) {
                Screen.LIBRARY -> LibraryScreen(state, model)
                Screen.DETAIL -> AppDetailScreen(state, model)
                Screen.UPDATES -> UpdatesScreen(state, model)
                Screen.ADD -> AddRepositoryScreen(state, model)
                Screen.WRAP -> WrapWebsiteScreen(state, model)
                Screen.BUILD -> ActionsBuildScreen(state, model)
                Screen.SETTINGS -> SettingsScreen(state, model)
                else -> Unit
            }
        }
    }
}

private val WIDE_BREAKPOINT = 600.dp

@Composable
private fun AppBar(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(text = "Vault", style = cinzel(20, FontWeight.W600))
        SectionLabel(label)
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Hairline()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultColors.Graphite)
                .press(onClick = onDismiss)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            BasicText(text = message, style = jost(12, FontWeight.W300, VaultColors.Gold))
        }
    }
}

@Composable
private fun TabBar(state: VaultUiState, model: VaultViewModel) {
    val updates = state.updateCount
    val tabs = listOf(
        Screen.LIBRARY to "Library",
        Screen.UPDATES to if (updates > 0) "Updates · $updates" else "Updates",
        Screen.ADD to "Add",
        Screen.SETTINGS to "Settings",
    )
    Column {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEach { (screen, label) ->
                val active = state.screen == screen ||
                    (state.screen == Screen.DETAIL && screen == state.detailOrigin) ||
                    (screen == Screen.ADD && (state.screen == Screen.WRAP || state.screen == Screen.BUILD))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .press { model.goTo(screen) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) VaultColors.Gold else VaultColors.Onyx),
                    )
                    CenteredLabel(
                        text = label,
                        color = if (active) VaultColors.Gold else VaultColors.Stone,
                        size = 10,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
