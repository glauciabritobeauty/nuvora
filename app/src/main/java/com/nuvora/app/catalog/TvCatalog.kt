package com.nuvora.app.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.components.SectionHeader
import com.nuvora.designsystem.components.TvSideMenu
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Etapa 2: vitrine dos componentes na TV, toda navegável pelo controle.
 * Não é a Home final (Etapa 4) e não mostra conteúdo real.
 */
@Composable
fun TvCatalog() {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    var selectedMenu by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var lastAction by remember { mutableStateOf("nenhum") }
    val playFocus = remember { FocusRequester() }

    Row(Modifier.fillMaxSize().background(colors.background)) {
        TvSideMenu(
            items = TV_MENU,
            bottomItems = TV_MENU_BOTTOM,
            selectedKey = selectedMenu,
            onSelect = { selectedMenu = it.key; lastAction = it.label },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 32.dp, end = dimens.screenPaddingH, top = dimens.screenPaddingV, bottom = dimens.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        ) {
            item {
                NuvoraText(
                    text = "Vitrine de componentes · Etapa 2 · último comando: $lastAction",
                    style = NuvoraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            item {
                HeroBanner(
                    title = "Título do conteúdo",
                    subtitle = "Descrição curta, vinda da fonte conectada",
                    imageUrl = null,
                ) {
                    NuvoraButton(
                        text = "Assistir",
                        icon = NuvoraIcons.Play,
                        onClick = { lastAction = "Assistir" },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    NuvoraButton(
                        text = "Minha lista",
                        icon = NuvoraIcons.Plus,
                        style = NuvoraButtonStyle.Secondary,
                        onClick = { lastAction = "Minha lista" },
                    )
                }
            }
            item {
                TvSection("Continuar assistindo") {
                    items(listOf(0.55f, 0.3f, 0.8f, 0.1f)) { progress ->
                        LandscapeCard(
                            title = "Cartão de exemplo",
                            caption = "Faltam ${((1 - progress) * 50).toInt()} min",
                            imageUrl = null,
                            progress = progress,
                            onClick = { lastAction = "Continuar assistindo" },
                        )
                    }
                }
            }
            item {
                TvSection("Categorias") {
                    items(CATEGORIES) { label ->
                        CategoryChip(label = label, selected = label == category, onClick = { category = label; lastAction = label })
                    }
                }
            }
            item {
                TvSection("Adicionados recentemente") {
                    items(List(8) { it + 1 }) { n ->
                        PosterCard(title = "Exemplo $n", imageUrl = null, onClick = { lastAction = "Exemplo $n" })
                    }
                }
            }
            item { SectionHeader("Estados de tela") }
            item { LoadingState() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    ErrorState(onRetry = { lastAction = "Tentar de novo" }, modifier = Modifier.width(300.dp))
                    NoConnectionState(onRetry = { lastAction = "Tentar de novo (sem internet)" }, modifier = Modifier.width(300.dp))
                    EmptyState(
                        icon = NuvoraIcons.Source,
                        title = "Nenhuma fonte conectada",
                        message = "Conecte uma fonte de conteúdo autorizada para ver filmes e séries aqui.",
                        actionLabel = "Abrir ajustes",
                        onAction = { lastAction = "Abrir ajustes" },
                        modifier = Modifier.width(300.dp),
                    )
                }
            }
        }
    }

    // Na TV, o foco já começa no "Assistir" para o controle funcionar de imediato.
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun TvSection(title: String, content: LazyListScope.() -> Unit) {
    val dimens = NuvoraTheme.dimens
    Column {
        SectionHeader(title)
        // Folga vertical para o cartão em foco crescer sem ser cortado.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            content = content,
        )
    }
}
