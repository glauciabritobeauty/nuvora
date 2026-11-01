package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Imagem do cartão. O Coil baixa a imagem já no tamanho do cartão (nunca a original grande).
 * Sem endereço, mostra só o fundo do cartão — nunca uma capa inventada.
 */
@Composable
private fun CardImage(imageUrl: String?) {
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Cartão vertical (capa de filme/série), com título embaixo. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val dimens = NuvoraTheme.dimens
    val colors = NuvoraTheme.colors
    Column(modifier.width(dimens.posterWidth)) {
        NuvoraClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = dimens.cardShape,
            // O título fica fora do cartão; repetido aqui para o leitor de tela e o controle saberem o que está em foco.
            modifier = Modifier
                .size(dimens.posterWidth, dimens.posterHeight)
                .semantics { contentDescription = title },
            containerColor = colors.surfaceRaised,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            CardImage(imageUrl)
        }
        NuvoraText(
            text = title,
            style = NuvoraTheme.typography.caption,
            color = colors.textSecondary,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Cartão horizontal. Com [progress] (0 a 1) vira cartão de "Continuar assistindo",
 * com barra ciano mostrando onde a pessoa parou.
 */
@Composable
fun LandscapeCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
    caption: String? = null,
) {
    val dimens = NuvoraTheme.dimens
    val colors = NuvoraTheme.colors
    Column(modifier.width(dimens.landscapeWidth)) {
        NuvoraClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = dimens.cardShape,
            modifier = Modifier
                .size(dimens.landscapeWidth, dimens.landscapeHeight)
                .semantics { contentDescription = if (caption != null) "$title, $caption" else title },
            containerColor = colors.surfaceRaised,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            CardImage(imageUrl)
            if (progress != null) {
                NuvoraIcon(
                    icon = NuvoraIcons.Play,
                    tint = colors.textPrimary,
                    size = 20.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colors.outline),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
        }
        NuvoraText(
            text = title,
            style = NuvoraTheme.typography.caption,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (caption != null) {
            NuvoraText(text = caption, style = NuvoraTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/**
 * Capa grande de filme/série que ocupa a largura da coluna (grade de 3 no celular):
 * nome escrito sobre a capa, com sombra embaixo, e selo da nota no canto quando o provedor manda.
 */
@Composable
fun CoverCard(
    title: String,
    imageUrl: String?,
    rating: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    NuvoraClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = dimens.cardShape,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .semantics { contentDescription = title },
        containerColor = colors.surfaceRaised,
    ) {
        CardImage(imageUrl)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to colors.background.copy(alpha = 0.92f))),
        )
        if (rating != null) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(colors.background.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvoraText("★", NuvoraTheme.typography.caption, color = colors.accent)
                NuvoraText(rating, NuvoraTheme.typography.caption, color = colors.textPrimary)
            }
        }
        NuvoraText(
            title,
            NuvoraTheme.typography.label,
            color = colors.textPrimary,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Linha de canal ao vivo: número, logo e nome (lista de TV ao vivo). */
@Composable
fun ChannelRow(
    number: Int,
    title: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    NuvoraClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = title },
        containerColor = colors.surface,
        focusedContainerColor = colors.surfaceRaised,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = if (isTv) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NuvoraText(
                number.toString(),
                NuvoraTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.width(if (isTv) 40.dp else 28.dp),
            )
            Box(
                Modifier
                    .size(if (isTv) 56.dp else 46.dp)
                    .background(colors.surfaceRaised, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (logoUrl != null) {
                    AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                } else {
                    NuvoraIcon(NuvoraIcons.Play, tint = colors.accent, size = 20.dp)
                }
            }
            NuvoraText(title, NuvoraTheme.typography.label, color = colors.textPrimary, maxLines = 1)
        }
    }
}
