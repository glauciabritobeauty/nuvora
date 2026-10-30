package com.nuvora.feature.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.HomeContent
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.ErrorState
import com.nuvora.designsystem.components.HeroBanner
import com.nuvora.designsystem.components.LandscapeCard
import com.nuvora.designsystem.components.LoadingState
import com.nuvora.designsystem.components.NoConnectionState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.components.SectionHeader
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

private const val ALL = "Todos"

/**
 * Home da TV: banner + fileiras navegáveis pelo controle.
 * Ao abrir, o foco vai para a primeira ação da tela (Assistir, ou o botão do aviso).
 */
@Composable
fun TvHomeScreen(
    state: HomeUiState,
    favoriteIds: Set<String>,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    demoAvailable: Boolean = false,
    onStartDemo: () -> Unit = {},
) {
    val dimens = NuvoraTheme.dimens
    val firstAction = remember { FocusRequester() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = dimens.screenPaddingH, top = dimens.screenPaddingV, bottom = dimens.screenPaddingV),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
    ) {
        if (notice != null) {
            item(key = "notice") {
                NuvoraText(notice, NuvoraTheme.typography.caption, color = NuvoraTheme.colors.violet)
            }
        }
        when (state) {
            HomeUiState.Loading -> item(key = "loading") { LoadingState() }
            HomeUiState.NoSource -> item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = NuvoraIcons.Source,
                        title = "Nenhuma fonte conectada",
                        message = "Conecte uma fonte de conteúdo autorizada para ver filmes e séries aqui.",
                        actionLabel = "Abrir ajustes",
                        onAction = onOpenSettings,
                        actionModifier = Modifier.focusRequester(firstAction),
                    )
                    if (demoAvailable) {
                        NuvoraButton(text = "Ver demonstração", style = NuvoraButtonStyle.Secondary, onClick = onStartDemo)
                    }
                }
            }
            is HomeUiState.Error -> item(key = "error") {
                if (state.noConnection) {
                    NoConnectionState(onRetry, Modifier.fillMaxWidth().padding(top = 48.dp), Modifier.focusRequester(firstAction))
                } else {
                    ErrorState(onRetry, Modifier.fillMaxWidth().padding(top = 48.dp), actionModifier = Modifier.focusRequester(firstAction))
                }
            }
            is HomeUiState.Content -> tvHomeContent(state.content, favoriteIds, firstAction, onPlay, onOpen, onToggleFavorite)
        }
    }

    // Com o controle remoto, sempre precisa haver algo em foco.
    LaunchedEffect(state::class) { runCatching { firstAction.requestFocus() } }
}

private fun LazyListScope.tvHomeContent(
    content: HomeContent,
    favoriteIds: Set<String>,
    firstAction: FocusRequester,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    content.hero.firstOrNull()?.let { hero ->
        item(key = "hero") { TvHero(hero, hero.id in favoriteIds, firstAction, onPlay, onToggleFavorite) }
    }
    if (content.continueWatching.isNotEmpty()) {
        item(key = "continue") {
            TvRow("Continuar assistindo", content.continueWatching) { item ->
                LandscapeCard(
                    title = item.title,
                    imageUrl = item.imageUrl,
                    caption = item.caption,
                    progress = item.progress,
                    onClick = { onPlay(item.id) },
                )
            }
        }
    }
    item(key = "categories_recent") {
        // As categorias filtram de verdade a fileira de recentes logo abaixo.
        var category by rememberSaveable { mutableStateOf(ALL) }
        val filtered = remember(category, content.recent) {
            if (category == ALL) content.recent else content.recent.filter { it.category == category }
        }
        Column(verticalArrangement = Arrangement.spacedBy(NuvoraTheme.dimens.sectionSpacing)) {
            if (content.categories.isNotEmpty()) {
                TvRow("Categorias", listOf(ALL) + content.categories, key = { it }) { label ->
                    CategoryChip(label = label, selected = label == category, onClick = { category = label })
                }
            }
            if (filtered.isNotEmpty()) {
                TvRow("Adicionados recentemente", filtered) { item ->
                    PosterCard(title = item.title, imageUrl = item.imageUrl, onClick = { onOpen(item.id) })
                }
            }
        }
    }
    if (content.favorites.isNotEmpty()) {
        item(key = "favorites") {
            TvRow("Favoritos", content.favorites) { item ->
                PosterCard(title = item.title, imageUrl = item.imageUrl, onClick = { onOpen(item.id) })
            }
        }
    }
    if (content.history.isNotEmpty()) {
        item(key = "history") {
            TvRow("Histórico", content.history) { item ->
                PosterCard(title = item.title, imageUrl = item.imageUrl, onClick = { onOpen(item.id) })
            }
        }
    }
}

@Composable
private fun TvHero(
    hero: HeroItem,
    favorite: Boolean,
    firstAction: FocusRequester,
    onPlay: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    HeroBanner(title = hero.title, subtitle = hero.subtitle, imageUrl = hero.imageUrl) {
        NuvoraButton(
            text = "Assistir",
            icon = NuvoraIcons.Play,
            onClick = { onPlay(hero.id) },
            modifier = Modifier.focusRequester(firstAction),
        )
        NuvoraButton(
            text = if (favorite) "Na minha lista" else "Minha lista",
            icon = if (favorite) NuvoraIcons.HeartFilled else NuvoraIcons.Plus,
            style = NuvoraButtonStyle.Secondary,
            onClick = { onToggleFavorite(hero.id) },
        )
    }
}

@Composable
private fun TvRow(title: String, items: List<CardItem>, card: @Composable (CardItem) -> Unit) =
    TvRow(title, items, key = { it.id }, card = card)

/**
 * Fileira da TV. Ao voltar para ela com as setas, o foco retorna ao último cartão visitado.
 * A folga em volta deixa o cartão em foco crescer sem ser cortado.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T : Any> TvRow(title: String, items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    Column {
        SectionHeader(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(NuvoraTheme.dimens.itemSpacing),
        ) {
            items(items, key = key) { card(it) }
        }
    }
}
