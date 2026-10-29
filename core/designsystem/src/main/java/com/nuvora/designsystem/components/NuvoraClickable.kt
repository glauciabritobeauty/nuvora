package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Base de tudo que se toca ou seleciona.
 * - Celular: área de toque comum.
 * - TV: recebe foco pelo controle, cresce levemente e ganha borda ciano (sem sombra nem desfoque).
 */
@Composable
fun NuvoraClickable(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = containerColor,
    border: BorderStroke? = null,
    focusedBorderColor: Color = NuvoraTheme.colors.accent,
    content: @Composable BoxScope.() -> Unit,
) {
    if (NuvoraTheme.isTv) {
        val dimens = NuvoraTheme.dimens
        val contentColor = NuvoraTheme.colors.textPrimary
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                focusedContainerColor = focusedContainerColor,
                focusedContentColor = contentColor,
                pressedContainerColor = focusedContainerColor,
                pressedContentColor = contentColor,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = dimens.focusScale),
            border = ClickableSurfaceDefaults.border(
                border = border?.let { Border(border = it, shape = shape) } ?: Border.None,
                focusedBorder = Border(border = BorderStroke(dimens.focusBorder, focusedBorderColor), shape = shape),
            ),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(containerColor, shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .clickable(onClick = onClick),
            content = content,
        )
    }
}
