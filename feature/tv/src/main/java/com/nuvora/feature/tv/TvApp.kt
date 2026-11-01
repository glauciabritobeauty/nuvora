package com.nuvora.feature.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuvora.core.data.NuvoraViewModel
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.core.player.NuvoraPlayerScreen
import com.nuvora.core.data.sources.SectionKind
import com.nuvora.feature.sources.AddSourceScreen
import com.nuvora.feature.sources.BrowseScreen
import com.nuvora.feature.sources.DetailsScreen
import com.nuvora.core.player.PlayerWarmup
import com.nuvora.feature.sources.HubScreen
import com.nuvora.feature.sources.EpisodesDialog
import com.nuvora.feature.sources.SourcesSection
import com.nuvora.designsystem.components.DialogAction
import com.nuvora.designsystem.components.NavItem
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraDialog
import com.nuvora.designsystem.components.TvSideMenu
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.feature.tv.home.TvHomeScreen
import com.nuvora.feature.tv.screens.TvCategoriesScreen
import com.nuvora.feature.tv.screens.TvListScreen
import com.nuvora.feature.tv.screens.TvSearchScreen
import com.nuvora.feature.tv.screens.TvSettingsScreen

private enum class TvTab(val item: NavItem) {
    Search(NavItem("search", "Buscar", NuvoraIcons.Search)),
    Home(NavItem("home", "Início", NuvoraIcons.Home)),
    Favorites(NavItem("favorites", "Favoritos", NuvoraIcons.Heart)),
    History(NavItem("history", "Histórico", NuvoraIcons.History)),
    Categories(NavItem("categories", "Categorias", NuvoraIcons.Categories)),
    Settings(NavItem("settings", "Ajustes", NuvoraIcons.Settings)),
}

// "Categorias" saiu do menu: a tela de escolha (TV ao vivo, Filmes, Séries) já leva às categorias.
private val MENU = TvTab.entries.filter { it != TvTab.Settings && it != TvTab.Categories }

/** O que a pessoa segurou: um item do histórico ou dos favoritos. */
private data class ItemOptions(val item: CardItem, val fromHistory: Boolean)

/**
 * Experiência de TV: menu lateral + telas, tudo pelo controle remoto.
 * Botão Voltar: do conteúdo vai para o menu; do menu fora do Início vai para o Início; no menu do Início sai do app.
 * Os dados vêm do [NuvoraViewModel] (o mesmo do celular); aqui fica só o que é de tela.
 */
@Composable
fun TvApp(versionName: String, viewModel: NuvoraViewModel = viewModel()) {
    val colors = NuvoraTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedServer by viewModel.savedServer.collectAsStateWithLifecycle()
    val subtitleMode by viewModel.subtitleMode.collectAsStateWithLifecycle()
    val largeText by viewModel.largeText.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TvTab.Home) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFocused by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<ItemOptions?>(null) }
    var addingSource by rememberSaveable { mutableStateOf(false) }
    // Parte aberta a partir da tela de escolha (TV ao vivo, Filmes, Séries); null = a própria tela de escolha.
    var browseKey by rememberSaveable { mutableStateOf<String?>(null) }
    val hub by viewModel.hub.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastReport.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    // Filme aberto nos detalhes: o vídeo já vai sendo preparado por trás (começa mais rápido no Assistir).
    val detailsRequest = details?.request
    // Também prepara de novo ao voltar do vídeo para os detalhes (para assistir de novo sem esperar).
    val watching = viewModel.playing.collectAsStateWithLifecycle().value != null
    LaunchedEffect(detailsRequest?.url, watching) {
        if (!watching) detailsRequest?.let { PlayerWarmup.prepare(context, it, viewModel.resumePositionFor(it.id), subtitleMode) }
    }
    val closeDetails = {
        PlayerWarmup.release()
        viewModel.closeDetails()
    }
    // Guarda a rolagem e o estado das telas enquanto o player está aberto, para voltar exatamente onde estava.
    val screens = rememberSaveableStateHolder()
    var confirmClear by remember { mutableStateOf(false) }
    var choosingSubtitles by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }

    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val openItem: (String) -> Unit = { id ->
        viewModel.play(id) {
            Toast.makeText(context, "Este item não tem vídeo disponível na fonte.", Toast.LENGTH_SHORT).show()
        }
    }

    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    playing?.let { request ->
        val start = remember(request.id) { viewModel.resumePositionFor(request.id) }
        NuvoraPlayerScreen(
            request = request,
            startPositionMs = start,
            subtitleMode = subtitleMode,
            onSaveProgress = { position, duration -> viewModel.saveProgress(request, position, duration) },
            onClose = viewModel::closePlayer,
            onFirstFrame = { viewModel.noteFirstFrame(request, it) },
            onSummary = { stalls, stalledMs, seeks, watchedMs -> viewModel.noteVideoSummary(request, stalls, stalledMs, seeks, watchedMs) },
        )
        return
    }
    val showOptions: (CardItem, Boolean) -> Unit = { item, fromHistory -> options = ItemOptions(item, fromHistory) }

    // Primeira vez (nenhuma lista): boas-vindas em tela cheia, já pedindo o acesso.
    // Continua na tela depois de salvar, até a pessoa ver "Pronto!" e tocar em "Ir para o Início".
    var welcomeOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.home) { if (state.home is HomeUiState.NoSource) welcomeOpen = true }
    if ((state.home is HomeUiState.NoSource || welcomeOpen) && !addingSource) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            AddSourceScreen(
                onSaveLink = viewModel::addLinkSource,
                onSaveFile = viewModel::addFileSource,
                onSaveProvider = viewModel::addProviderSource,
                savedServer = savedServer,
                onFinished = {
                    welcomeOpen = false
                    tab = TvTab.Home
                },
                onBack = {},
                welcome = true,
                extraContent = {
                    if (DemoContent.AVAILABLE) {
                        NuvoraButton(
                            text = "Ver demonstração",
                            style = NuvoraButtonStyle.Secondary,
                            onClick = {
                                welcomeOpen = false
                                viewModel.setDemo(true)
                            },
                        )
                    }
                },
            )
        }
        return
    }

    BackHandler(enabled = !menuFocused || tab != TvTab.Home) {
        if (!menuFocused) {
            runCatching { menuFocus.requestFocus() }
        } else {
            tab = TvTab.Home
        }
    }

    screens.SaveableStateProvider("app") {
    Row(Modifier.fillMaxSize().background(colors.background)) {
        TvSideMenu(
            items = MENU.map { it.item },
            bottomItems = listOf(TvTab.Settings.item),
            selectedKey = tab.item.key,
            onSelect = { selected ->
                addingSource = false
                browseKey = null
                closeDetails()
                tab = TvTab.entries.first { it.item.key == selected.key }
            },
            selectedFocusRequester = menuFocus,
            modifier = Modifier.onFocusChanged { menuFocused = it.hasFocus },
        )
        Box(Modifier.weight(1f)) {
            if (addingSource) {
                AddSourceScreen(
                    onSaveLink = viewModel::addLinkSource,
                    onSaveFile = viewModel::addFileSource,
                    onSaveProvider = viewModel::addProviderSource,
                    savedServer = savedServer,
                    onFinished = { addingSource = false; tab = TvTab.Home },
                    onBack = { addingSource = false },
                )
                return@Box
            }
            val browsing = browseKey?.let(SectionKind::of)
            when {
                tab == TvTab.Home && details != null -> DetailsScreen(
                    state = details!!,
                    isFavorite = details!!.itemId in state.favoriteIds,
                    onPlay = viewModel::playDetails,
                    onPlayEpisode = viewModel::playDetailsEpisode,
                    onToggleFavorite = { viewModel.toggleFavorite(details!!.itemId) },
                    onBack = closeDetails,
                )
                tab == TvTab.Home && !state.demoOn && browsing != null -> BrowseScreen(
                    kind = browsing,
                    changes = changes,
                    loadCategories = viewModel::categories,
                    loadItems = viewModel::categoryItems,
                    loadAll = viewModel::sectionItems,
                    loadFavorites = viewModel::favoritesIn,
                    search = viewModel::searchIn,
                    onOpen = openItem,
                    onBack = { browseKey = null },
                    onOpenDetails = { item -> if (browsing != SectionKind.Live) viewModel.openDetails(item, browsing) else openItem(item.id) },
                    cachedCategories = viewModel.cachedCategories(browsing),
                    cachedItems = { viewModel.cachedItems(browsing, it) },
                )
                tab == TvTab.Home && !state.demoOn -> HubScreen(
                    hub = hub,
                    loading = loading,
                    versionName = versionName,
                    onOpen = { browseKey = it.key },
                    onRefresh = viewModel::refreshAll,
                    onLogout = { viewModel.logout() },
                    shortcuts = {
                        NuvoraButton(text = "Buscar", icon = NuvoraIcons.Search, style = NuvoraButtonStyle.Secondary, onClick = { tab = TvTab.Search })
                        NuvoraButton(text = "Favoritos", icon = NuvoraIcons.Heart, style = NuvoraButtonStyle.Secondary, onClick = { tab = TvTab.Favorites })
                    },
                )
                else -> when (tab) {
                TvTab.Home -> TvHomeScreen(
                    state = state.home,
                    favoriteIds = state.favoriteIds,
                    onPlay = openItem,
                    onOpen = openItem,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRetry = {},
                    onOpenSettings = { tab = TvTab.Settings },
                    onItemOptions = showOptions,
                    notice = if (state.demoOn) "Modo demonstração: cartões de exemplo, sem conteúdo real. Desligue em Ajustes." else null,
                    demoAvailable = DemoContent.AVAILABLE,
                    onStartDemo = { viewModel.setDemo(true) },
                )
                TvTab.Search -> TvSearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    hasSource = state.hasSource,
                    results = produceState(emptyList<CardItem>(), query, state.catalog) { value = viewModel.search(query) }.value,
                    onOpen = openItem,
                    onOpenSettings = { tab = TvTab.Settings },
                )
                TvTab.Favorites -> TvListScreen(
                    title = "Favoritos",
                    items = state.favorites,
                    emptyIcon = NuvoraIcons.Heart,
                    emptyTitle = "Nenhum favorito ainda",
                    emptyMessage = "Use \"Minha lista\" em um filme ou série para guardar aqui.",
                    onOpen = openItem,
                    onLongPress = { showOptions(it, false) },
                    hint = "Segure o OK em um item para remover.",
                )
                TvTab.History -> TvListScreen(
                    title = "Histórico",
                    items = state.history,
                    emptyIcon = NuvoraIcons.History,
                    emptyTitle = "Nada assistido ainda",
                    emptyMessage = "O que você assistir aparece aqui, para voltar de onde parou.",
                    onOpen = openItem,
                    onLongPress = { showOptions(it, true) },
                    hint = "Segure o OK em um item para remover.",
                    clearLabel = "Limpar histórico",
                    onClear = { confirmClear = true },
                )
                TvTab.Categories -> TvCategoriesScreen(
                    categories = state.categories,
                    items = state.catalog,
                    onOpen = openItem,
                )
                TvTab.Settings -> TvSettingsScreen(
                    versionName = versionName,
                    demoAvailable = DemoContent.AVAILABLE,
                    demoOn = state.demoOn,
                    onToggleDemo = { viewModel.setDemo(!state.demoOn) },
                    historyCount = state.history.size,
                    onClearHistory = { confirmClear = true },
                    subtitleMode = subtitleMode,
                    onChangeSubtitles = { choosingSubtitles = true },
                    largeText = largeText,
                    onToggleLargeText = { viewModel.setLargeText(!largeText) },
                    sourcesContent = {
                        SourcesSection(
                            sources = sources,
                            onAdd = { addingSource = true },
                            onRefresh = viewModel::refreshSource,
                            onRemove = viewModel::removeSource,
                        lastReport = lastReport,
                        )
                    },
                )
            }
            }
        }
    }
    }

    options?.let { (item, fromHistory) ->
        NuvoraDialog(
            title = item.title,
            onDismiss = { options = null },
            actions = listOf(
                if (fromHistory) {
                    DialogAction("Remover do histórico", { viewModel.removeFromHistory(item.id) }, NuvoraButtonStyle.Primary)
                } else {
                    DialogAction("Remover dos favoritos", { viewModel.removeFavorite(item.id) }, NuvoraButtonStyle.Primary)
                },
                DialogAction("Cancelar", {}),
            ),
        )
    }
    if (choosingSubtitles) {
        NuvoraDialog(
            title = "Legendas",
            message = "Como as legendas começam em cada vídeo. Dá para mudar em qualquer vídeo pelo botão Legendas.",
            onDismiss = { choosingSubtitles = false },
            actions = SubtitleMode.entries.map { mode ->
                DialogAction(
                    label = if (mode == subtitleMode) "${mode.label} (atual)" else mode.label,
                    onClick = { viewModel.setSubtitleMode(mode) },
                    style = if (mode == subtitleMode) NuvoraButtonStyle.Primary else NuvoraButtonStyle.Secondary,
                )
            } + DialogAction("Cancelar", {}),
        )
    }
    episodes?.let { series ->
        EpisodesDialog(series, onPick = { viewModel.playEpisode(series, it) }, onDismiss = viewModel::closeEpisodes)
    }
    if (confirmClear) {
        NuvoraDialog(
            title = "Limpar todo o histórico?",
            message = "Isso apaga o histórico e o \"Continuar assistindo\" deste aparelho. Não dá para desfazer.",
            onDismiss = { confirmClear = false },
            actions = listOf(
                DialogAction("Limpar", viewModel::clearHistory, NuvoraButtonStyle.Primary),
                DialogAction("Cancelar", {}),
            ),
        )
    }
}
