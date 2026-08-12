package org.cyclingcommons.scout.karoo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ScoutKarooColors {
    val Screen = Color(0xFFF0F0F0)
    val Surface = Color(0xFFFFFFFF)
    val Brand = Color(0xFFD1421F)
    val Recording = Color(0xFF2E8B57)
    val IdleDot = Color(0xFFD1421F)
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF666666)
    val TextOnBrand = Color(0xFFFFFFFF)
    val TextOnPale = Color(0xFF1A1A1A)
    val Outline = Color(0xFFCCCCCC)
    const val TileIdleAlpha = 0.22f
}

object ScoutSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
}

object ScoutDimens {
    val tileCorner = 8.dp
    val cardCorner = 12.dp
}

object ScoutType {
    val overline =
        TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    val tileLabel =
        TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
        )
    val tileCount =
        TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    val countdown =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    val metric =
        TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
}

/** Pale tile hues need dark ink when lit (SPEC §6.1). */
const val PALE_TILE_LUMINANCE = 0.3f
