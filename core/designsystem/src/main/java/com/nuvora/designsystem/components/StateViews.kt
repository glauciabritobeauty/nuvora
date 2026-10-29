package com.nuvora.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Carregamento: blocos com a forma do conteúdo que vai chegar, pulsando de leve.
 * A animação só mexe na transparência da camada (não redesenha a tela inteira).
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val dimens = NuvoraTheme.dimens
    val colors = NuvoraTheme.colors
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        modifier = modifier
            .graphicsLayer { alpha = pulse.value }
            .semantics { contentDescription = "Carregando" },
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        Box(Modifier.fillMaxWidth().height(dimens.heroHeight).background(colors.surfaceRaised, dimens.heroShape))
        repeat(2) {
            Box(Modifier.padding(top = dimens.itemSpacing).size(140.dp, 16.dp).background(colors.surfaceRaised, RoundedCornerShape(4.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
                repeat(4) {
                    Box(Modifier.size(dimens.landscapeWidth, dimens.landscapeHeight).background(colors.surfaceRaised, dimens.cardShape))
                }
            }
        }
    }
}

/** Mensagem centralizada com ícone e, se houver, um botão de ação. */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = NuvoraTheme.colors
    val typography = NuvoraTheme.typography
    val isTv = NuvoraTheme.isTv
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (isTv) 72.dp else 60.dp)
                .background(colors.surfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            NuvoraIcon(icon = icon, tint = colors.accent, size = if (isTv) 32.dp else 26.dp)
        }
        NuvoraText(
            text = title,
            style = typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        NuvoraText(
            text = message,
            style = typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 420.dp),
        )
        if (actionLabel != null && onAction != null) {
            NuvoraButton(
                text = actionLabel,
                icon = actionIcon,
                onClick = onAction,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Não foi possível carregar",
    message: String = "Algo deu errado do nosso lado. Tente de novo em instantes.",
) = MessageState(
    icon = NuvoraIcons.Refresh,
    title = title,
    message = message,
    modifier = modifier,
    actionLabel = "Tentar de novo",
    actionIcon = NuvoraIcons.Refresh,
    onAction = onRetry,
)

@Composable
fun NoConnectionState(onRetry: () -> Unit, modifier: Modifier = Modifier) = MessageState(
    icon = NuvoraIcons.NoConnection,
    title = "Sem internet",
    message = "Verifique o Wi-Fi ou os dados móveis. Assim que a conexão voltar, é só tentar de novo.",
    modifier = modifier,
    actionLabel = "Tentar de novo",
    actionIcon = NuvoraIcons.Refresh,
    onAction = onRetry,
)

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = MessageState(
    icon = icon,
    title = title,
    message = message,
    modifier = modifier,
    actionLabel = actionLabel,
    onAction = onAction,
)
