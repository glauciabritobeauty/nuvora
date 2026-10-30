package com.nuvora.feature.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.nuvora.core.player.NuvoraPlayerScreen
import com.nuvora.designsystem.components.DialogAction
import com.nuvora.designsystem.components.NavItem
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

private val MENU = TvTab.entries.filter { it != TvTab.Settings }

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
    var tab by rememberSaveable { mutableStateOf(TvTab.Home) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFocused by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<ItemOptions?>(null) }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Guarda a rolagem e o estado das telas enquanto o player está aberto, para voltar exatamente onde estava.
    val screens = rememberSaveableStateHolder()
    var confirmClear by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }

    val openItem: (String) -> Unit = { id ->
        if (viewModel.playbackFor(id) != null) {
            playingId = id
        } else {
            Toast.makeText(context, "Este item não tem vídeo disponível na fonte.", Toast.LENGTH_SHORT).show()
        }
    }

    val playing = playingId?.let(viewModel::playbackFor)
    if (playing != null) {
        val start = remember(playing.id) { viewModel.resumePositionFor(playing.id) }
        NuvoraPlayerScreen(
            request = playing,
            startPositionMs = start,
            onSaveProgress = { position, duration -> viewModel.saveProgress(playing, position, duration) },
            onClose = { playingId = null },
        )
        return
    }
    val showOptions: (CardItem, Boolean) -> Unit = { item, fromHistory -> options = ItemOptions(item, fromHistory) }

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
            onSelect = { selected -> tab = TvTab.entries.first { it.item.key == selected.key } },
            selectedFocusRequester = menuFocus,
            modifier = Modifier.onFocusChanged { menuFocused = it.hasFocus },
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
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
                    results = remember(query, state.catalog) { viewModel.search(query) },
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
                )
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
