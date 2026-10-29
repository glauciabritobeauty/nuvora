package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Banner principal (sempre imagem estática, nunca vídeo).
 * Por cima da imagem vai um gradiente da cor de fundo para o texto ficar legível.
 * Sem imagem, desenha a "luz" da marca com gradientes radiais simples (sem desfoque).
 */
@Composable
fun HeroBanner(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    overline: String? = "DESTAQUE",
    pageCount: Int = 1,
    currentPage: Int = 0,
    actions: @Composable () -> Unit,
) {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    val typography = NuvoraTheme.typography
    val isTv = NuvoraTheme.isTv
    val shape = dimens.heroShape
    Box(
        modifier
            .fillMaxWidth()
            .height(dimens.heroHeight)
            .clip(shape)
            .background(if (isTv) colors.background else colors.surface)
            .then(if (isTv) Modifier else Modifier.border(BorderStroke(1.dp, colors.outline), shape))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.2f),
                        radius = size.height * 0.9f,
                    ),
                    radius = size.height * 0.9f,
                    center = Offset(size.width * 0.85f, size.height * 0.2f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.violet.copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(size.width * 0.72f, size.height * 0.45f),
                        radius = size.height * 0.55f,
                    ),
                    radius = size.height * 0.55f,
                    center = Offset(size.width * 0.72f, size.height * 0.45f),
                )
            },
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.background.copy(alpha = 0.92f),
                            0.6f to colors.background.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) 0.55f else 1f)
                .padding(if (isTv) 0.dp else 16.dp),
        ) {
            if (overline != null) {
                NuvoraText(text = overline, style = typography.overline, color = colors.accent)
            }
            NuvoraText(text = title, style = typography.display, maxLines = 2)
            if (subtitle != null) {
                NuvoraText(
                    text = subtitle,
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = if (isTv) 16.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
        if (pageCount > 1) {
            PageDots(
                count = pageCount,
                current = currentPage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isTv) 4.dp else 18.dp),
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = NuvoraTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .height(4.dp)
                    .width(if (active) 14.dp else 4.dp)
                    .clip(if (active) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (active) colors.accent else colors.outline),
            )
        }
    }
}

/** Título de seção, com "Ver tudo" opcional (só no celular; na TV a fileira já rola). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = NuvoraTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NuvoraText(text = title, style = NuvoraTheme.typography.section, maxLines = 1)
        if (actionLabel != null && onAction != null && !NuvoraTheme.isTv) {
            NuvoraClickable(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
                NuvoraText(
                    text = actionLabel,
                    style = NuvoraTheme.typography.caption,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
