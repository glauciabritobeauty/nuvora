package com.nuvora.feature.mobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.SubtitleMode

/** Título de tela (abas e "Ver tudo"), com seta de voltar opcional. */
@Composable
internal fun ScreenTitle(title: String, onBack: (() -> Unit)? = null) {
    val dimens = NuvoraTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPaddingH, vertical = dimens.screenPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            NuvoraClickable(onClick = onBack, shape = RoundedCornerShape(50)) {
                Box(Modifier.padding(6.dp)) {
                    NuvoraIcon(NuvoraIcons.ChevronLeft, contentDescription = "Voltar")
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        NuvoraText(text = title, style = NuvoraTheme.typography.title, maxLines = 1)
    }
}

/** Grade de capas, com mais colunas automaticamente no tablet. */
@Composable
internal fun CardGrid(
    items: List<CardItem>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((CardItem) -> Unit)? = null,
) {
    val dimens = NuvoraTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dimens.posterWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.screenPaddingH, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            PosterCard(
                title = item.title,
                imageUrl = item.imageUrl,
                onClick = { onOpen(item.id) },
                onLongClick = onLongPress?.let { { it(item) } },
            )
        }
    }
}

/** "Ver tudo": a seção inteira em grade. */
@Composable
fun SectionScreen(
    title: String,
    items: List<CardItem>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: ((CardItem) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title, onBack)
        CardGrid(items, onOpen, onLongPress = onLongPress)
    }
}

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    hasSource: Boolean,
    results: List<CardItem>,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    val shape = RoundedCornerShape(50)
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Buscar")
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = NuvoraTheme.typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPaddingH),
            decorationBox = { field ->
                Row(
                    modifier = Modifier
                        .background(colors.surface, shape)
                        .border(1.dp, colors.outline, shape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvoraIcon(NuvoraIcons.Search, tint = colors.textSecondary, size = 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            NuvoraText("Filmes, séries e canais", NuvoraTheme.typography.body, color = colors.textSecondary, maxLines = 1)
                        }
                        field()
                    }
                }
            },
        )
        when {
            !hasSource -> EmptyState(
                icon = NuvoraIcons.Source,
                title = "Nada para buscar ainda",
                message = "Conecte uma fonte de conteúdo autorizada e a busca funciona aqui.",
                actionLabel = "Abrir ajustes",
                onAction = onOpenSettings,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            query.isBlank() -> NuvoraText(
                text = "Digite o nome do que você procura.",
                style = NuvoraTheme.typography.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(dimens.screenPaddingH),
            )
            results.isEmpty() -> EmptyState(
                icon = NuvoraIcons.Search,
                title = "Nada encontrado",
                message = "Não achamos \"$query\". Confira a escrita ou tente outra palavra.",
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            else -> CardGrid(results, onOpen, Modifier.padding(top = 12.dp))
        }
    }
}

/** Favoritos ou Histórico: grade quando há itens, aviso amigável quando está vazio. */
@Composable
fun ListTabScreen(
    title: String,
    items: List<CardItem>,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    onLongPress: (CardItem) -> Unit,
    hint: String,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val dimens = NuvoraTheme.dimens
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title)
        if (items.isEmpty()) {
            EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                message = emptyMessage,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.screenPaddingH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvoraText(hint, NuvoraTheme.typography.caption, color = NuvoraTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                if (clearLabel != null && onClear != null) {
                    NuvoraButton(text = clearLabel, style = NuvoraButtonStyle.Secondary, onClick = onClear)
                }
            }
            CardGrid(items, onOpen, Modifier.padding(top = 8.dp), onLongPress)
        }
    }
}

@Composable
fun SettingsScreen(
    versionName: String,
    demoAvailable: Boolean,
    demoOn: Boolean,
    onToggleDemo: () -> Unit,
    historyCount: Int,
    onClearHistory: () -> Unit,
    subtitleMode: SubtitleMode,
    onChangeSubtitles: () -> Unit,
    largeText: Boolean,
    onToggleLargeText: () -> Unit,
) {
    val dimens = NuvoraTheme.dimens
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Ajustes")
        Column(
            modifier = Modifier.padding(horizontal = dimens.screenPaddingH),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoRow("Fontes de conteúdo", "O cadastro de listas e servidores autorizados está sendo construído.")
            InfoRow(
                if (largeText) "Letra maior: ligada" else "Letra maior: desligada",
                "Aumenta todos os textos do aplicativo.",
            )
            NuvoraButton(
                text = if (largeText) "Voltar ao tamanho normal" else "Ligar letra maior",
                style = NuvoraButtonStyle.Secondary,
                onClick = onToggleLargeText,
            )
            InfoRow("Legendas: ${subtitleMode.label}", subtitleMode.description)
            NuvoraButton(text = "Mudar legendas", icon = NuvoraIcons.Subtitles, style = NuvoraButtonStyle.Secondary, onClick = onChangeSubtitles)
            InfoRow(
                "Histórico",
                if (historyCount == 0) "Vazio. O histórico fica só neste aparelho." else "$historyCount itens, guardados só neste aparelho.",
            )
            if (historyCount > 0) {
                NuvoraButton(text = "Limpar histórico", style = NuvoraButtonStyle.Secondary, onClick = onClearHistory)
            }
            InfoRow("Versão do app", versionName)
            if (demoAvailable) {
                InfoRow(
                    "Demonstração (só na versão de teste)",
                    if (demoOn) "Ligada: a Home mostra cartões de exemplo." else "Desligada: a Home mostra o estado real.",
                )
                NuvoraButton(
                    text = if (demoOn) "Desligar demonstração" else "Ver demonstração",
                    style = NuvoraButtonStyle.Secondary,
                    onClick = onToggleDemo,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val colors = NuvoraTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        NuvoraText(title, NuvoraTheme.typography.label)
        NuvoraText(value, NuvoraTheme.typography.caption, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}
