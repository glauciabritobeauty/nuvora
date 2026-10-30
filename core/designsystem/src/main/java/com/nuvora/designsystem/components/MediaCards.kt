package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
