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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.designsystem.components.NavItem
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

/**
 * Experiência de TV: menu lateral + telas, tudo pelo controle remoto.
 * Botão Voltar: do conteúdo vai para o menu; do menu fora do Início vai para o Início; no menu do Início sai do app.
 * Sem fonte conectada, tudo mostra o estado real. A fonte entra na Etapa 7; salvar favoritos e histórico, na Etapa 5.
 */
@Composable
fun TvApp(versionName: String) {
    val colors = NuvoraTheme.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(TvTab.Home) }
    var demoOn by rememberSaveable { mutableStateOf(false) }
    var favoriteIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFocused by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }

    val favorites = remember(favoriteIds) { favoriteIds.toSet() }
    val content = remember(demoOn, favorites) { if (demoOn) DemoContent.home(favorites) else null }
    val homeState = content?.let { HomeUiState.Content(it) } ?: HomeUiState.NoSource

    val openItem: (String) -> Unit = {
        Toast.makeText(context, "Demonstração: a reprodução chega na Etapa 6.", Toast.LENGTH_SHORT).show()
    }
    val toggleFavorite: (String) -> Unit = { id ->
        favoriteIds = if (id in favorites) favoriteIds - id else favoriteIds + id
    }

    BackHandler(enabled = !menuFocused || tab != TvTab.Home) {
        if (!menuFocused) {
            runCatching { menuFocus.requestFocus() }
        } else {
            tab = TvTab.Home
        }
    }

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
                    state = homeState,
                    favoriteIds = favorites,
                    onPlay = openItem,
                    onOpen = openItem,
                    onToggleFavorite = toggleFavorite,
                    onRetry = {},
                    onOpenSettings = { tab = TvTab.Settings },
                    notice = if (demoOn) "Modo demonstração: cartões de exemplo, sem conteúdo real. Desligue em Ajustes." else null,
                    demoAvailable = DemoContent.AVAILABLE,
                    onStartDemo = { demoOn = true },
                )
                TvTab.Search -> TvSearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    hasSource = content != null,
                    results = remember(query, demoOn) { if (demoOn) DemoContent.search(query) else emptyList() },
                    onOpen = openItem,
                    onOpenSettings = { tab = TvTab.Settings },
                )
                TvTab.Favorites -> TvListScreen(
                    title = "Favoritos",
                    items = content?.favorites.orEmpty(),
                    emptyIcon = NuvoraIcons.Heart,
                    emptyTitle = "Nenhum favorito ainda",
                    emptyMessage = "Use \"Minha lista\" em um filme ou série para guardar aqui.",
                    onOpen = openItem,
                )
                TvTab.History -> TvListScreen(
                    title = "Histórico",
                    items = content?.history.orEmpty(),
                    emptyIcon = NuvoraIcons.History,
                    emptyTitle = "Nada assistido ainda",
                    emptyMessage = "O que você assistir aparece aqui, para voltar de onde parou.",
                    onOpen = openItem,
                )
                TvTab.Categories -> TvCategoriesScreen(
                    categories = content?.categories.orEmpty(),
                    items = content?.recent.orEmpty(),
                    onOpen = openItem,
                )
                TvTab.Settings -> TvSettingsScreen(
                    versionName = versionName,
                    demoAvailable = DemoContent.AVAILABLE,
                    demoOn = demoOn,
                    onToggleDemo = { demoOn = !demoOn },
                )
            }
        }
    }
}
