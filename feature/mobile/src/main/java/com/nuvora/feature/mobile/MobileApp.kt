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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.components.BottomNavBar
import com.nuvora.designsystem.components.NavItem
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.feature.mobile.home.HomeScreen
import com.nuvora.feature.mobile.home.HomeSection
import com.nuvora.core.data.model.HomeUiState
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

/**
 * Experiência de celular e tablet: Home + abas do menu inferior.
 * Sem fonte conectada, tudo mostra o estado real (vazio). A fonte entra na Etapa 7;
 * favoritos e histórico passam a ser salvos no aparelho na Etapa 5.
 */
@Composable
fun MobileApp(versionName: String) {
    val colors = NuvoraTheme.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var section by rememberSaveable { mutableStateOf<HomeSection?>(null) }
    var demoOn by rememberSaveable { mutableStateOf(false) }
    var favoriteIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }

    val favorites = remember(favoriteIds) { favoriteIds.toSet() }
    val content = remember(demoOn, favorites) { if (demoOn) DemoContent.home(favorites) else null }
    val homeState = content?.let { HomeUiState.Content(it) } ?: HomeUiState.NoSource

    val openItem: (String) -> Unit = {
        Toast.makeText(context, "Demonstração: a reprodução chega na Etapa 6.", Toast.LENGTH_SHORT).show()
    }
    val toggleFavorite: (String) -> Unit = { id ->
        favoriteIds = if (id in favorites) favoriteIds - id else favoriteIds + id
    }
    val goSettings = { section = null; tab = Tab.Settings }

    // Botão voltar do celular: fecha o "Ver tudo"; depois volta para o Início; no Início, sai do app.
    BackHandler(enabled = section != null || tab != Tab.Home) {
        if (section != null) section = null else tab = Tab.Home
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            val open = section
            if (open != null) {
                val items = when (open) {
                    HomeSection.ContinueWatching -> content?.continueWatching
                    HomeSection.Recent -> content?.recent
                    HomeSection.Favorites -> content?.favorites
                    HomeSection.History -> content?.history
                }.orEmpty()
                SectionScreen(open.title, items, onBack = { section = null }, onOpen = openItem)
            } else {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        state = homeState,
                        favoriteIds = favorites,
                        onSearch = { tab = Tab.Search },
                        onPlay = openItem,
                        onOpen = openItem,
                        onToggleFavorite = toggleFavorite,
                        onSeeAll = { section = it },
                        onRetry = {},
                        onOpenSettings = goSettings,
                        notice = if (demoOn) {
                            { DemoNotice(onExit = { demoOn = false }) }
                        } else null,
                        emptyExtra = if (DemoContent.AVAILABLE) {
                            {
                                NuvoraButton(
                                    text = "Ver demonstração",
                                    style = NuvoraButtonStyle.Secondary,
                                    onClick = { demoOn = true },
                                )
                            }
                        } else null,
                    )
                    Tab.Search -> SearchScreen(
                        query = query,
                        onQueryChange = { query = it },
                        hasSource = content != null,
                        results = remember(query, demoOn) { if (demoOn) DemoContent.search(query) else emptyList() },
                        onOpen = openItem,
                        onOpenSettings = goSettings,
                    )
                    Tab.Favorites -> ListTabScreen(
                        title = "Favoritos",
                        items = content?.favorites.orEmpty(),
                        emptyIcon = NuvoraIcons.Heart,
                        emptyTitle = "Nenhum favorito ainda",
                        emptyMessage = "Toque no coração de um filme ou série para guardar aqui.",
                        onOpen = openItem,
                    )
                    Tab.History -> ListTabScreen(
                        title = "Histórico",
                        items = content?.history.orEmpty(),
                        emptyIcon = NuvoraIcons.History,
                        emptyTitle = "Nada assistido ainda",
                        emptyMessage = "O que você assistir aparece aqui, para voltar de onde parou.",
                        onOpen = openItem,
                    )
                    Tab.Settings -> SettingsScreen(
                        versionName = versionName,
                        demoAvailable = DemoContent.AVAILABLE,
                        demoOn = demoOn,
                        onToggleDemo = { demoOn = !demoOn },
                    )
                }
            }
        }
        BottomNavBar(
            items = Tab.entries.map { it.item },
            selectedKey = tab.item.key,
            onSelect = { selected ->
                section = null
                tab = Tab.entries.first { it.item.key == selected.key }
            },
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
