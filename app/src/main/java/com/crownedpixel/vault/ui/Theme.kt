@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.crownedpixel.vault.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.crownedpixel.vault.R

/**
 * "Crowned Pixel": onyx surfaces, bone type, gold as an accent that only ever fills on press.
 * Zero corner radius, no gradients, no shadows — depth comes from the surface step alone.
 */
object VaultColors {
    val Onyx = Color(0xFF0C0A09)
    val Graphite = Color(0xFF161318)
    val Bone = Color(0xFFEDE8DC)
    val Stone = Color(0xFF8F8A80)
    val Gold = Color(0xFFC6A75E)
    val GoldBright = Color(0xFFE3C57E)

    /** rgba(198,167,94,.25) — every border and rule in the interface. */
    val Hairline = Color(0x40C6A75E)

    /** rgba(198,167,94,.12) — row separators. */
    val Separator = Color(0x1FC6A75E)

    /** rgba(198,167,94,.15) — the track behind a progress bar. */
    val Track = Color(0x26C6A75E)
}

object VaultDimens {
    val hairline = 1.dp
    val progressHeight = 2.dp
    val screenPadding = 20.dp
}

private fun variableFont(resId: Int, weight: FontWeight): Font = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Cinzel = FontFamily(
    variableFont(R.font.cinzel_variable, FontWeight.W500),
    variableFont(R.font.cinzel_variable, FontWeight.W600),
    variableFont(R.font.cinzel_variable, FontWeight.W700),
)

val Jost = FontFamily(
    variableFont(R.font.jost_variable, FontWeight.W300),
    variableFont(R.font.jost_variable, FontWeight.W400),
    variableFont(R.font.jost_variable, FontWeight.W500),
)

/** Display, numerals and version numbers. */
fun cinzel(
    size: Int,
    weight: FontWeight = FontWeight.W600,
    color: Color = VaultColors.Bone,
    letterSpacing: TextUnit = 0.02.em,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = Cinzel,
    fontWeight = weight,
    fontSize = size.sp,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
)

/** Body and interface copy. */
fun jost(
    size: Int,
    weight: FontWeight = FontWeight.W400,
    color: Color = VaultColors.Bone,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = 1.6.em,
): TextStyle = TextStyle(
    fontFamily = Jost,
    fontWeight = weight,
    fontSize = size.sp,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
)

/** Uppercase micro-labels: gold when active, stone otherwise. */
fun labelStyle(size: Int = 10, color: Color = VaultColors.Stone): TextStyle = TextStyle(
    fontFamily = Jost,
    fontWeight = FontWeight.W400,
    fontSize = size.sp,
    color = color,
    letterSpacing = 0.18.em,
)
