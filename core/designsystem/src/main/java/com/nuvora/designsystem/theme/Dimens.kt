package com.nuvora.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Medidas que mudam entre celular e TV. */
@Immutable
data class NuvoraDimens(
    val screenPaddingH: Dp,
    val screenPaddingV: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,
    val landscapeWidth: Dp,
    val landscapeHeight: Dp,
    val heroHeight: Dp,
    val iconSize: Dp,
    val focusBorder: Dp,
    val focusScale: Float,
    val cardShape: RoundedCornerShape,
    val heroShape: RoundedCornerShape,
)

internal val mobileDimens = NuvoraDimens(
    screenPaddingH = 16.dp,
    screenPaddingV = 12.dp,
    sectionSpacing = 20.dp,
    itemSpacing = 10.dp,
    posterWidth = 108.dp,
    posterHeight = 160.dp,
    landscapeWidth = 160.dp,
    landscapeHeight = 90.dp,
    heroHeight = 200.dp,
    iconSize = 22.dp,
    focusBorder = 0.dp,
    focusScale = 1f,
    cardShape = RoundedCornerShape(14.dp),
    heroShape = RoundedCornerShape(18.dp),
)

/** Margens de 48/27 dp deixam o conteúdo dentro da área segura das TVs (overscan). */
internal val tvDimens = NuvoraDimens(
    screenPaddingH = 48.dp,
    screenPaddingV = 27.dp,
    sectionSpacing = 28.dp,
    itemSpacing = 16.dp,
    posterWidth = 140.dp,
    posterHeight = 210.dp,
    landscapeWidth = 220.dp,
    landscapeHeight = 124.dp,
    heroHeight = 260.dp,
    iconSize = 24.dp,
    focusBorder = 3.dp,
    focusScale = 1.06f,
    cardShape = RoundedCornerShape(16.dp),
    heroShape = RoundedCornerShape(20.dp),
)
