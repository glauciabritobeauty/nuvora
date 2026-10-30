package com.nuvora.feature.tv.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

private const val ALL = "Todos"

/** Moldura das telas da TV: título + conteúdo, dentro da área segura. */
@Composable
private fun TvScreen(title: String, content: @Composable () -> Unit) {
    val dimens = NuvoraTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = dimens.screenPaddingH, top = dimens.screenPaddingV),
    ) {
        NuvoraText(title, NuvoraTheme.typography.title, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

@Composable
private fun TvGrid(items: List<CardItem>, onOpen: (String) -> Unit, onLongPress: ((CardItem) -> Unit)? = null) {
    val dimens = NuvoraTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dimens.posterWidth + dimens.itemSpacing),
        contentPadding = PaddingValues(8.dp, 10.dp, 8.dp, dimens.screenPaddingV),
        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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

@Composable
fun TvSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    hasSource: Boolean,
    results: List<CardItem>,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NuvoraTheme.colors
    val shape = RoundedCornerShape(50)
    // O campo só recebe foco depois do OK. Assim o teclado abre quando o botão já foi solto
    // e não digita sozinho a primeira letra (o "q"), e passar pelo campo com as setas não abre o teclado.
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }
    TvScreen("Buscar") {
        NuvoraClickable(
            onClick = { editing = true },
            shape = shape,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().semantics { contentDescription = "Campo de busca" },
            containerColor = if (editing) colors.surfaceRaised else colors.surface,
            focusedContainerColor = colors.surfaceRaised,
            border = BorderStroke(if (editing) 3.dp else 1.dp, if (editing) colors.accent else colors.outline),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                textStyle = NuvoraTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { if (!it.isFocused && editing) editing = false }
                    // Com o teclado fechado, ↓ sai do campo para os resultados; OK reabre o teclado.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown, Key.DirectionUp -> {
                                editing = false
                                focusManager.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                keyboard?.show()
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { field ->
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NuvoraIcon(NuvoraIcons.Search, tint = colors.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                NuvoraText("Filmes, séries e canais", NuvoraTheme.typography.body, color = colors.textSecondary, maxLines = 1)
                            }
                            field()
                        }
                    }
                },
            )
        }
        Spacer(Modifier.padding(top = 16.dp))
        when {
            !hasSource -> EmptyState(
                icon = NuvoraIcons.Source,
                title = "Nada para buscar ainda",
                message = "Conecte uma fonte de conteúdo autorizada e a busca funciona aqui.",
                actionLabel = "Abrir ajustes",
                onAction = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
            query.isBlank() -> NuvoraText("Digite o nome do que você procura.", NuvoraTheme.typography.body, color = colors.textSecondary)
            results.isEmpty() -> EmptyState(
                icon = NuvoraIcons.Search,
                title = "Nada encontrado",
                message = "Não achamos \"$query\". Confira a escrita ou tente outra palavra.",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> TvGrid(results, onOpen)
        }
    }
}

/** Favoritos ou Histórico: grade quando há itens, aviso amigável quando está vazio. */
@Composable
fun TvListScreen(
    title: String,
    items: List<CardItem>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    onLongPress: (CardItem) -> Unit,
    hint: String,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    TvScreen(title) {
        if (items.isEmpty()) {
            EmptyState(emptyIcon, emptyTitle, emptyMessage, Modifier.fillMaxWidth().padding(top = 32.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvoraText(hint, NuvoraTheme.typography.caption, color = NuvoraTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                if (clearLabel != null && onClear != null) {
                    NuvoraButton(text = clearLabel, style = NuvoraButtonStyle.Secondary, onClick = onClear)
                }
            }
            TvGrid(items, onOpen, onLongPress)
        }
    }
}

/** Categorias: escolhe uma e a grade mostra só o que é dela. */
@Composable
fun TvCategoriesScreen(categories: List<String>, items: List<CardItem>, onOpen: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf(ALL) }
    val filtered = remember(category, items) { if (category == ALL) items else items.filter { it.category == category } }
    TvScreen("Categorias") {
        if (items.isEmpty()) {
            EmptyState(
                NuvoraIcons.Categories,
                "Nenhuma categoria ainda",
                "As categorias aparecem quando houver uma fonte de conteúdo conectada.",
                Modifier.fillMaxWidth().padding(top = 32.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(listOf(ALL) + categories) { label ->
                    CategoryChip(label = label, selected = label == category, onClick = { category = label })
                }
            }
            TvGrid(filtered, onOpen)
        }
    }
}

@Composable
fun TvSettingsScreen(
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
    sourcesContent: @Composable () -> Unit,
) {
    TvScreen("Ajustes") {
        Column(
            Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(bottom = 27.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sourcesContent()
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(18.dp),
    ) {
        NuvoraText(title, NuvoraTheme.typography.label)
        NuvoraText(value, NuvoraTheme.typography.caption, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}
