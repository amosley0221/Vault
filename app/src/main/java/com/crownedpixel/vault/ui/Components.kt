package com.crownedpixel.vault.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** A tap target with no ripple: the brand has no soft edges to ripple into. */
@Composable
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun Monogram(
    letter: String,
    size: Dp,
    textSize: Int,
    modifier: Modifier = Modifier,
    borderColor: Color = VaultColors.Hairline,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(VaultDimens.hairline, borderColor),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = letter.take(1).uppercase(),
            style = cinzel(textSize, FontWeight.W600, VaultColors.Gold),
        )
    }
}

/**
 * Gold outline, transparent fill, uppercase gold label — filling solid gold with onyx text
 * while pressed, over 200ms.
 */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    verticalPadding: Dp = 13.dp,
    horizontalPadding: Dp = 0.dp,
    labelSize: Int = 12,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed && enabled) VaultColors.Gold else Color.Transparent,
        animationSpec = tween(200),
        label = "fill",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> VaultColors.Stone
            pressed -> VaultColors.Onyx
            else -> VaultColors.Gold
        },
        animationSpec = tween(200),
        label = "content",
    )
    Box(
        modifier = modifier
            .border(VaultDimens.hairline, if (enabled) VaultColors.Gold else VaultColors.Hairline)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text.uppercase(), style = labelStyle(labelSize, content))
    }
}

/** An inert bordered box — "UP TO DATE" and friends. */
@Composable
fun QuietBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(VaultDimens.hairline, VaultColors.Hairline)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text.uppercase(), style = labelStyle(12, VaultColors.Stone))
    }
}

@Composable
fun GhostAction(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VaultColors.Stone,
    size: Int = 11,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.press(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text.uppercase(), style = labelStyle(size, color))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = VaultColors.Stone, size: Int = 10) {
    BasicText(text = text.uppercase(), style = labelStyle(size, color), modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = VaultColors.Hairline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VaultDimens.hairline)
            .background(color),
    )
}

/** 2px gold on a translucent gold track. */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VaultDimens.progressHeight)
            .background(VaultColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(VaultDimens.progressHeight)
                .background(VaultColors.Gold),
        )
    }
}

@Composable
fun ProgressRow(stage: String, percent: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(stage)
            SectionLabel("$percent%")
        }
        ProgressTrack(percent / 100f)
    }
}

@Composable
fun StatusBadge(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(VaultDimens.hairline, if (active) VaultColors.Gold else VaultColors.Hairline)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        BasicText(
            text = text.uppercase(),
            style = labelStyle(9, if (active) VaultColors.Gold else VaultColors.Stone),
        )
    }
}

@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) VaultColors.Gold else VaultColors.Hairline,
        animationSpec = tween(200),
        label = "border",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = jost(15, FontWeight.W400, VaultColors.Bone),
        cursorBrush = SolidColor(VaultColors.Gold),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onGo = { onImeAction() },
            onSend = { onImeAction() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .background(VaultColors.Graphite)
            .border(VaultDimens.hairline, border)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    BasicText(text = placeholder, style = jost(15, FontWeight.W300, VaultColors.Stone))
                }
                inner()
            }
        },
    )
}

@Composable
fun StatColumn(label: String, value: String, valueColor: Color = VaultColors.Bone, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel(label, size = 9, modifier = Modifier.padding(bottom = 3.dp))
        BasicText(text = value, style = cinzel(15, FontWeight.W500, valueColor))
    }
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = VaultColors.Stone, size: Int = 13) {
    BasicText(text = text, style = jost(size, FontWeight.W300, color), modifier = modifier)
}

@Composable
fun EllipsizedText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun CenteredLabel(text: String, modifier: Modifier = Modifier, color: Color = VaultColors.Stone, size: Int = 11) {
    BasicText(
        text = text.uppercase(),
        style = labelStyle(size, color).copy(textAlign = TextAlign.Center),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A toggle: 36×18 hairline track, 12px square knob sliding 2px → 20px, gold when on. */
@Composable
fun VaultToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val offset by animateFloatAsState(
        targetValue = if (checked) 20f else 2f,
        animationSpec = tween(200),
        label = "knob",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) VaultColors.Gold else VaultColors.Stone,
        animationSpec = tween(200),
        label = "knobColor",
    )
    Box(
        modifier = modifier
            .width(36.dp)
            .height(18.dp)
            .border(VaultDimens.hairline, if (checked) VaultColors.Gold else VaultColors.Hairline),
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset.dp, top = 2.dp)
                .size(12.dp)
                .background(knobColor),
        )
    }
}

/**
 * Pull-to-refresh in the house style: the list slides down to uncover a hairline label, and a gold
 * bar sweeps while the refresh runs. No Material scaffolding — this brand has no spinners.
 *
 * [content] is laid out in a column beneath the indicator, so a scrolling child should take
 * `Modifier.weight(1f)`.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxPullPx = thresholdPx * 1.8f
    val restingPx = with(density) { 44.dp.toPx() }

    var pull by remember { mutableStateOf(0f) }
    val busy by rememberUpdatedState(refreshing)
    val refreshCallback by rememberUpdatedState(onRefresh)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging back up pays down the pull before the list starts scrolling again.
                if (busy || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y >= 0f || pull <= 0f) return Offset.Zero
                val consumed = -min(pull, -available.y)
                pull = (pull + consumed).coerceAtLeast(0f)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Whatever the list could not use at the top becomes pull, at half rate.
                if (busy || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y <= 0f) return Offset.Zero
                pull = (pull + available.y * 0.5f).coerceAtMost(maxPullPx)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                val armed = pull >= thresholdPx
                pull = 0f
                if (armed && !busy) refreshCallback()
                return available
            }
        }
    }

    val indicator by animateFloatAsState(
        targetValue = if (refreshing) restingPx else pull,
        animationSpec = tween(160),
        label = "pull",
    )

    Column(modifier = modifier.nestedScroll(connection)) {
        if (indicator > 1f) {
            PullIndicator(
                heightPx = indicator,
                refreshing = refreshing,
                armed = pull >= thresholdPx,
            )
        }
        content()
    }
}

@Composable
private fun PullIndicator(heightPx: Float, refreshing: Boolean, armed: Boolean) {
    val height = with(LocalDensity.current) { heightPx.toDp() }
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweepValue",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CenteredLabel(
            text = when {
                refreshing -> "Refreshing…"
                armed -> "Release to refresh"
                else -> "Pull to refresh"
            },
            color = if (refreshing || armed) VaultColors.Gold else VaultColors.Stone,
            size = 10,
        )
        if (refreshing) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(VaultDimens.progressHeight)
                    .background(VaultColors.Track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sweep)
                        .height(VaultDimens.progressHeight)
                        .background(VaultColors.Gold),
                )
            }
        }
    }
}
