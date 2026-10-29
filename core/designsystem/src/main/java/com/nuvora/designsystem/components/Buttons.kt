package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.theme.NuvoraTheme

enum class NuvoraButtonStyle { Primary, Secondary }

private val PillShape = RoundedCornerShape(50)

/** Botão em pílula. Sem [text], vira botão só de ícone (informe [contentDescription]). */
@Composable
fun NuvoraButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: NuvoraButtonStyle = NuvoraButtonStyle.Primary,
    contentDescription: String? = null,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val primary = style == NuvoraButtonStyle.Primary
    val contentColor = if (primary) colors.onPrimary else colors.textPrimary
    NuvoraClickable(
        onClick = onClick,
        shape = PillShape,
        modifier = modifier,
        containerColor = if (primary) colors.primary else Color.Transparent,
        focusedContainerColor = if (primary) colors.primary else colors.surfaceRaised,
        border = if (primary) null else BorderStroke(1.dp, colors.outline),
        focusedBorderColor = if (primary) colors.textPrimary else colors.accent,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = when {
                    text == null -> if (isTv) 12.dp else 10.dp
                    isTv -> 22.dp
                    else -> 18.dp
                },
                vertical = if (isTv) 10.dp else 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                NuvoraIcon(
                    icon = icon,
                    tint = contentColor,
                    size = if (isTv) 20.dp else 18.dp,
                    contentDescription = if (text == null) contentDescription else null,
                )
            }
            if (text != null) {
                NuvoraText(text = text, style = NuvoraTheme.typography.label, color = contentColor, maxLines = 1)
            }
        }
    }
}

/** Categoria (Todos, Filmes, Séries...). Sempre numa linha só. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    NuvoraClickable(
        onClick = onClick,
        shape = PillShape,
        modifier = modifier,
        containerColor = if (selected) colors.primary else colors.surface,
        focusedContainerColor = if (selected) colors.primary else colors.surfaceRaised,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        NuvoraText(
            text = label,
            style = NuvoraTheme.typography.caption,
            color = if (selected) colors.onPrimary else colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (isTv) 18.dp else 14.dp,
                vertical = if (isTv) 8.dp else 6.dp,
            ),
        )
    }
}
