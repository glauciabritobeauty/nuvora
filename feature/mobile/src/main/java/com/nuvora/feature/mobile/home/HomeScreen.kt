package com.nuvora.feature.mobile.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.ErrorState
import com.nuvora.designsystem.components.HeroBanner
import com.nuvora.designsystem.components.LandscapeCard
import com.nuvora.designsystem.components.LoadingState
import com.nuvora.designsystem.components.NoConnectionState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraLogo
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.components.SectionHeader
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.HomeContent
import com.nuvora.core.data.model.HomeUiState

private const val ALL = "Todos"

/** Home do celular e do tablet. Não guarda dados: recebe o estado pronto e avisa as ações. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    favoriteIds: Set<String>,
    onSearch: () -> Unit,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSeeAll: (HomeSection) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    notice: (@Composable () -> Unit)? = null,
    emptyExtra: (@Composable () -> Unit)? = null,
) {
    val dimens = NuvoraTheme.dimens
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
    ) {
        item(key = "top") {
            ScreenTopBar(onSearch = onSearch)
            notice?.invoke()
        }
        when (state) {
            HomeUiState.Loading -> item(key = "loading") {
                LoadingState(Modifier.padding(horizontal = dimens.screenPaddingH))
            }
            HomeUiState.NoSource -> item(key = "empty") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = NuvoraIcons.Source,
                        title = "Nenhuma fonte conectada",
                        message = "Conecte uma fonte de conteúdo autorizada para ver filmes e séries aqui.",
                        actionLabel = "Abrir ajustes",
                        onAction = onOpenSettings,
                        modifier = Modifier.padding(top = 48.dp),
                    )
                    emptyExtra?.invoke()
                }
            }
            is HomeUiState.Error -> item(key = "error") {
                if (state.noConnection) {
                    NoConnectionState(onRetry = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                } else {
                    ErrorState(onRetry = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                }
            }
            is HomeUiState.Content -> homeContent(state.content, favoriteIds, onPlay, onOpen, onToggleFavorite, onSeeAll)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    content: HomeContent,
    favoriteIds: Set<String>,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSeeAll: (HomeSection) -> Unit,
) {
    if (content.hero.isNotEmpty()) {
        item(key = "hero") { HeroPager(content.hero, favoriteIds, onPlay, onToggleFavorite) }
    }
    if (content.continueWatching.isNotEmpty()) {
        item(key = "continue") {
            CardRow(HomeSection.ContinueWatching, content.continueWatching, onSeeAll) { item ->
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
        // Categorias filtram de verdade a fileira de recentes logo abaixo.
        var category by rememberSaveable { mutableStateOf(ALL) }
        val filtered = remember(category, content.recent) {
            if (category == ALL) content.recent else content.recent.filter { it.category == category }
        }
        val dimens = NuvoraTheme.dimens
        Column(verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            if (content.categories.isNotEmpty()) {
                Column {
                    SectionHeader("Categorias", Modifier.padding(horizontal = dimens.screenPaddingH))
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = dimens.screenPaddingH),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(listOf(ALL) + content.categories) { label ->
                            CategoryChip(label = label, selected = label == category, onClick = { category = label })
                        }
                    }
                }
            }
            if (filtered.isNotEmpty()) {
                CardRow(HomeSection.Recent, filtered, onSeeAll) { item -> Poster(item, onOpen) }
            } else {
                NuvoraText(
                    text = "Nada em \"$category\" por enquanto.",
                    style = NuvoraTheme.typography.body,
                    color = NuvoraTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = dimens.screenPaddingH),
                )
            }
        }
    }
    if (content.favorites.isNotEmpty()) {
        item(key = "favorites") {
            CardRow(HomeSection.Favorites, content.favorites, onSeeAll) { item -> Poster(item, onOpen) }
        }
    }
    if (content.history.isNotEmpty()) {
        item(key = "history") {
            CardRow(HomeSection.History, content.history, onSeeAll) { item -> Poster(item, onOpen) }
        }
    }
}

@Composable
private fun Poster(item: CardItem, onOpen: (String) -> Unit) {
    PosterCard(title = item.title, imageUrl = item.imageUrl, onClick = { onOpen(item.id) })
}

@Composable
private fun HeroPager(
    hero: List<HeroItem>,
    favoriteIds: Set<String>,
    onPlay: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val dimens = NuvoraTheme.dimens
    val pagerState = rememberPagerState { hero.size }
    // Só a página visível e as vizinhas ficam montadas.
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = dimens.screenPaddingH),
        pageSpacing = 10.dp,
        key = { hero[it].id },
    ) { page ->
        val item = hero[page]
        val favorite = item.id in favoriteIds
        HeroBanner(
            title = item.title,
            subtitle = item.subtitle,
            imageUrl = item.imageUrl,
            pageCount = hero.size,
            currentPage = page,
        ) {
            NuvoraButton(text = "Assistir", icon = NuvoraIcons.Play, onClick = { onPlay(item.id) })
            NuvoraButton(
                text = null,
                icon = if (favorite) NuvoraIcons.HeartFilled else NuvoraIcons.Heart,
                style = NuvoraButtonStyle.Secondary,
                contentDescription = if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = { onToggleFavorite(item.id) },
            )
        }
    }
}

@Composable
private fun CardRow(
    section: HomeSection,
    items: List<CardItem>,
    onSeeAll: (HomeSection) -> Unit,
    card: @Composable (CardItem) -> Unit,
) {
    val dimens = NuvoraTheme.dimens
    Column {
        SectionHeader(
            title = section.title,
            actionLabel = "Ver tudo",
            onAction = { onSeeAll(section) },
            modifier = Modifier.padding(horizontal = dimens.screenPaddingH),
        )
        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = dimens.screenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        ) {
            items(items, key = { it.id }) { card(it) }
        }
    }
}

/** Topo das telas: logo à esquerda e lupa à direita. */
@Composable
internal fun ScreenTopBar(onSearch: (() -> Unit)?) {
    val dimens = NuvoraTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPaddingH, vertical = dimens.screenPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvoraLogo(Modifier.weight(1f))
        if (onSearch != null) {
            NuvoraClickable(onClick = onSearch, shape = RoundedCornerShape(50)) {
                Box(Modifier.padding(6.dp)) { NuvoraIcon(NuvoraIcons.Search, contentDescription = "Buscar") }
            }
        }
    }
}
