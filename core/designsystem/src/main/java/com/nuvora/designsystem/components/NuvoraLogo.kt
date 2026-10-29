package com.nuvora.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvora.designsystem.theme.NuvoraTheme

/** Símbolo "N" com gradiente azul → violeta e um ponto de luz ciano. */
@Composable
fun NuvoraMark(modifier: Modifier = Modifier) {
    val colors = NuvoraTheme.colors
    Canvas(modifier) {
        val s = size.minDimension / 26f
        val path = Path().apply {
            moveTo(5f * s, 21f * s)
            lineTo(5f * s, 5f * s)
            lineTo(21f * s, 21f * s)
            lineTo(21f * s, 5f * s)
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                listOf(colors.primary, colors.violet),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            ),
            style = Stroke(width = 3.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = colors.accent, radius = 2f * s, center = Offset(21f * s, 5f * s))
    }
}

@Composable
fun NuvoraLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = if (NuvoraTheme.isTv) 32.dp else 26.dp,
    showWordmark: Boolean = true,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "NUVORA" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(markSize * 0.35f),
    ) {
        NuvoraMark(Modifier.size(markSize))
        if (showWordmark) {
            NuvoraText(
                text = "NUVORA",
                style = NuvoraTheme.typography.section.copy(
                    fontSize = (markSize.value * 0.6f).sp,
                    letterSpacing = 0.22.em,
                ),
            )
        }
    }
}
