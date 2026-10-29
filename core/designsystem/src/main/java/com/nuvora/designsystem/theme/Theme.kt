package com.nuvora.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { NuvoraColors() }
private val LocalTypography = staticCompositionLocalOf { mobileTypography() }
private val LocalDimens = staticCompositionLocalOf { mobileDimens }
private val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Tema único do NUVORA. [isTv] troca tipografia e medidas; cores e componentes são os mesmos.
 * Usa só compose-foundation (sem Material) para manter o app leve e com cara própria.
 */
@Composable
fun NuvoraTheme(isTv: Boolean, content: @Composable () -> Unit) {
    val typography = remember(isTv) { if (isTv) tvTypography() else mobileTypography() }
    CompositionLocalProvider(
        LocalColors provides NuvoraColors(),
        LocalTypography provides typography,
        LocalDimens provides if (isTv) tvDimens else mobileDimens,
        LocalIsTv provides isTv,
        content = content,
    )
}

object NuvoraTheme {
    val colors: NuvoraColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: NuvoraTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val dimens: NuvoraDimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
    val isTv: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsTv.current
}
