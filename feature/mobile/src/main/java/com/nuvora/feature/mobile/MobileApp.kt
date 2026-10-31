package com.nuvora.feature.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuvora.core.data.NuvoraViewModel
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.core.player.NuvoraPlayerScreen
import com.nuvora.feature.sources.AddSourceScreen
import com.nuvora.feature.sources.EpisodesDialog
import com.nuvora.feature.sources.SourcesSection
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.designsystem.components.BottomNavBar
import com.nuvora.designsystem.components.DialogAction
import com.nuvora.designsystem.components.NavItem
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraDialog
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.feature.mobile.home.HomeScreen
import com.nuvora.feature.mobile.home.HomeSection
import com.nuvora.feature.mobile.screens.ListTabScreen
import com.nuvora.feature.mobile.screens.SearchScreen
import com.nuvora.feature.mobile.screens.SectionScreen
import com.nuvora.feature.mobile.screens.SettingsScreen

private enum class Tab(val item: NavItem) {
    Home(NavItem("home", "Início", NuvoraIcons.Home)),
    Search(NavItem("search", "Buscar", NuvoraIcons.Search)),
    Favorites(NavItem("favorites", "Favoritos", NuvoraIcons.Heart)),
    History(NavItem("history", "Histórico", NuvoraIcons.History)),
    Settings(NavItem("settings", "Ajustes", NuvoraIcons.Settings)),
}

/** O que a pessoa segurou: um item do histórico ou dos favoritos. */
private data class ItemOptions(val item: CardItem, val fromHistory: Boolean)

/**
 * Experiência de celular e tablet: Home + abas do menu inferior.
 * Os dados vêm do [NuvoraViewModel] (favoritos e histórico salvos no aparelho);
 * aqui fica só o que é de tela: aba atual, "Ver tudo" aberto, busca e janelas de opções.
 */
@Composable
fun MobileApp(versionName: String, viewModel: NuvoraViewModel = viewModel()) {
    val colors = NuvoraTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subtitleMode by viewModel.subtitleMode.collectAsStateWithLifecycle()
    val largeText by viewModel.largeText.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var section by rememberSaveable { mutableStateOf<HomeSection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf<ItemOptions?>(null) }
    var addingSource by rememberSaveable { mutableStateOf(false) }
    // Guarda a rolagem e o estado das telas enquanto o player está aberto, para voltar exatamente onde estava.
    val screens = rememberSaveableStateHolder()
    var confirmClear by remember { mutableStateOf(false) }
    var choosingSubtitles by remember { mutableStateOf(false) }

    val content = (state.home as? HomeUiState.Content)?.content
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
        )
        return
    }
    // Primeira vez (nenhuma lista): boas-vindas em tela cheia, já pedindo o acesso.
    if (state.home is HomeUiState.NoSource && !addingSource) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AddSourceScreen(
                onSaveLink = viewModel::addLinkSource,
                onSaveFile = viewModel::addFileSource,
                onSaveProvider = viewModel::addProviderSource,
                onFinished = { tab = Tab.Home },
                onBack = {},
                welcome = true,
                extraContent = {
                    if (DemoContent.AVAILABLE) {
                        NuvoraButton(text = "Ver demonstração", style = NuvoraButtonStyle.Secondary, onClick = { viewModel.setDemo(true) })
                    }
                },
            )
        }
        return
    }
    val showOptions: (CardItem, HomeSection) -> Unit = { item, from ->
        options = ItemOptions(item, fromHistory = from != HomeSection.Favorites)
    }
    val goSettings = { section = null; tab = Tab.Settings }

    // Botão voltar do celular: fecha o "Ver tudo"; depois volta para o Início; no Início, sai do app.
    BackHandler(enabled = section != null || tab != Tab.Home) {
        if (section != null) section = null else tab = Tab.Home
    }

    screens.SaveableStateProvider("app") {
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            if (addingSource) {
                AddSourceScreen(
                    onSaveLink = viewModel::addLinkSource,
                    onSaveFile = viewModel::addFileSource,
                    onSaveProvider = viewModel::addProviderSource,
                    onFinished = { addingSource = false; tab = Tab.Home },
                    onBack = { addingSource = false },
                )
                return@Box
            }
            val open = section
            if (open != null) {
                val items = when (open) {
                    HomeSection.ContinueWatching -> content?.continueWatching
                    HomeSection.Recent -> content?.recent
                    HomeSection.Favorites -> state.favorites
                    HomeSection.History -> state.history
                }.orEmpty()
                SectionScreen(
                    title = open.title,
                    items = items,
                    onBack = { section = null },
                    onOpen = openItem,
                    onLongPress = if (open == HomeSection.Recent) null else { item -> showOptions(item, open) },
                )
            } else {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        state = state.home,
                        favoriteIds = state.favoriteIds,
                        onSearch = { tab = Tab.Search },
                        onPlay = openItem,
                        onOpen = openItem,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSeeAll = { section = it },
                        onRetry = {},
                        onOpenSettings = goSettings,
                        onItemOptions = showOptions,
                        notice = if (state.demoOn) {
                            { DemoNotice(onExit = { viewModel.setDemo(false) }) }
                        } else null,
                        emptyExtra = if (DemoContent.AVAILABLE) {
                            {
                                NuvoraButton(
                                    text = "Ver demonstração",
                                    style = NuvoraButtonStyle.Secondary,
                                    onClick = { viewModel.setDemo(true) },
                                )
                            }
                        } else null,
                    )
                    Tab.Search -> SearchScreen(
                        query = query,
                        onQueryChange = { query = it },
                        hasSource = state.hasSource,
                        results = produceState(emptyList<CardItem>(), query, state.catalog) { value = viewModel.search(query) }.value,
                        onOpen = openItem,
                        onOpenSettings = goSettings,
                    )
                    Tab.Favorites -> ListTabScreen(
                        title = "Favoritos",
                        items = state.favorites,
                        emptyIcon = NuvoraIcons.Heart,
                        emptyTitle = "Nenhum favorito ainda",
                        emptyMessage = "Toque no coração de um filme ou série para guardar aqui.",
                        onOpen = openItem,
                        onLongPress = { showOptions(it, HomeSection.Favorites) },
                        hint = "Segure um item para remover.",
                    )
                    Tab.History -> ListTabScreen(
                        title = "Histórico",
                        items = state.history,
                        emptyIcon = NuvoraIcons.History,
                        emptyTitle = "Nada assistido ainda",
                        emptyMessage = "O que você assistir aparece aqui, para voltar de onde parou.",
                        onOpen = openItem,
                        onLongPress = { showOptions(it, HomeSection.History) },
                        hint = "Segure um item para remover.",
                        clearLabel = "Limpar histórico",
                        onClear = { confirmClear = true },
                    )
                    Tab.Settings -> SettingsScreen(
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
                            )
                        },
                    )
                }
            }
        }
        BottomNavBar(
            items = Tab.entries.map { it.item },
            selectedKey = tab.item.key,
            onSelect = { selected ->
                section = null
                addingSource = false
                tab = Tab.entries.first { it.item.key == selected.key }
            },
        )
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

/** Faixa que deixa claro que a Home está em modo de demonstração. */
@Composable
private fun DemoNotice(onExit: () -> Unit) {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = dimens.screenPaddingH)
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.violet, shape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvoraText(
            text = "Modo demonstração: cartões de exemplo, sem conteúdo real.",
            style = NuvoraTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NuvoraClickable(onClick = onExit, shape = RoundedCornerShape(8.dp)) {
            NuvoraText(
                text = "Sair",
                style = NuvoraTheme.typography.label,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
