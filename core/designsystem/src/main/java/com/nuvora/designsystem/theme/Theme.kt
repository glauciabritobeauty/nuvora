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

/** Quanto a "Letra maior" aumenta os textos. */
private const val LARGE_TEXT_FACTOR = 1.2f

/**
 * Tema único do NUVORA. [isTv] troca tipografia e medidas; cores e componentes são os mesmos.
 * [largeText] aumenta todos os textos (opção "Letra maior" em Ajustes).
 * Usa só compose-foundation (sem Material) para manter o app leve e com cara própria.
 */
@Composable
fun NuvoraTheme(isTv: Boolean, largeText: Boolean = false, content: @Composable () -> Unit) {
    val typography = remember(isTv, largeText) {
        (if (isTv) tvTypography() else mobileTypography()).scaled(if (largeText) LARGE_TEXT_FACTOR else 1f)
    }
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
