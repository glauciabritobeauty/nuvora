package com.nuvora.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvora.designsystem.R

/** Poppins embutida (só Regular e Medium, para manter o APK pequeno). */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
)

@Immutable
data class NuvoraTypography(
    val display: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Poppins, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

internal fun mobileTypography() = NuvoraTypography(
    display = style(24, 30, FontWeight.Medium),
    title = style(20, 26, FontWeight.Medium),
    section = style(16, 22, FontWeight.Medium),
    body = style(14, 20),
    label = style(13, 18, FontWeight.Medium),
    caption = style(12, 16),
    overline = style(11, 14).copy(letterSpacing = 0.12.em),
)

/** Na TV o texto é lido a uns 3 metros: tudo maior. */
internal fun tvTypography() = NuvoraTypography(
    display = style(36, 44, FontWeight.Medium),
    title = style(26, 32, FontWeight.Medium),
    section = style(20, 26, FontWeight.Medium),
    body = style(16, 24),
    label = style(16, 22, FontWeight.Medium),
    caption = style(14, 20),
    overline = style(13, 18).copy(letterSpacing = 0.12.em),
)
