package com.nuvora.app.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.components.BottomNavBar
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.ErrorState
import com.nuvora.designsystem.components.HeroBanner
import com.nuvora.designsystem.components.LandscapeCard
import com.nuvora.designsystem.components.LoadingState
import com.nuvora.designsystem.components.NoConnectionState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraLogo
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.PosterCard
import com.nuvora.designsystem.components.SectionHeader
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Etapa 2: vitrine dos componentes no celular/tablet. Não é a Home final (Etapa 3)
 * e não mostra conteúdo real — os cartões são de exemplo, sem capa.
 */
@Composable
fun MobileCatalog() {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    var selectedTab by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var favorite by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf("nenhum") }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        ) {
            item {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = dimens.screenPaddingH, vertical = dimens.screenPaddingV),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NuvoraLogo(Modifier.weight(1f))
                        NuvoraIcon(NuvoraIcons.Search, contentDescription = "Buscar")
                        NuvoraIcon(NuvoraIcons.User, contentDescription = "Perfil", modifier = Modifier.padding(start = 14.dp))
                    }
                    NuvoraText(
                        text = "Vitrine de componentes · Etapa 2 · último toque: $lastAction",
                        style = NuvoraTheme.typography.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                HeroBanner(
                    title = "Título do conteúdo",
                    subtitle = "Vem da fonte conectada",
                    imageUrl = null,
                    pageCount = 3,
                    modifier = Modifier.padding(horizontal = dimens.screenPaddingH),
                ) {
                    NuvoraButton(text = "Assistir", icon = NuvoraIcons.Play, onClick = { lastAction = "Assistir" })
                    NuvoraButton(
                        text = null,
                        icon = if (favorite) NuvoraIcons.HeartFilled else NuvoraIcons.Heart,
                        style = NuvoraButtonStyle.Secondary,
                        contentDescription = "Favoritar",
                        onClick = { favorite = !favorite; lastAction = "Favoritar" },
                    )
                }
            }
            item {
                Section("Continuar assistindo", action = "Ver tudo", onAction = { lastAction = "Ver tudo" }) {
                    items(listOf(0.62f, 0.3f, 0.85f)) { progress ->
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
                Section("Categorias") {
                    items(CATEGORIES) { label ->
                        CategoryChip(label = label, selected = label == category, onClick = { category = label; lastAction = label })
                    }
                }
            }
            item {
                Section("Adicionados recentemente", action = "Ver tudo", onAction = { lastAction = "Ver tudo" }) {
                    items(List(6) { it + 1 }) { n ->
                        PosterCard(title = "Exemplo $n", imageUrl = null, onClick = { lastAction = "Exemplo $n" })
                    }
                }
            }
            item {
                SectionHeader("Estados de tela", Modifier.padding(horizontal = dimens.screenPaddingH))
            }
            item { LoadingState(Modifier.padding(horizontal = dimens.screenPaddingH)) }
            item { ErrorState(onRetry = { lastAction = "Tentar de novo" }, modifier = Modifier.fillMaxWidth()) }
            item { NoConnectionState(onRetry = { lastAction = "Tentar de novo (sem internet)" }, modifier = Modifier.fillMaxWidth()) }
            item {
                EmptyState(
                    icon = NuvoraIcons.Source,
                    title = "Nenhuma fonte conectada",
                    message = "Conecte uma fonte de conteúdo autorizada para ver filmes e séries aqui.",
                    actionLabel = "Abrir ajustes",
                    onAction = { lastAction = "Abrir ajustes" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        BottomNavBar(
            items = MOBILE_TABS,
            selectedKey = selectedTab,
            onSelect = { selectedTab = it.key; lastAction = it.label },
        )
    }
}

@Composable
private fun Section(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val dimens = NuvoraTheme.dimens
    Column {
        SectionHeader(
            title = title,
            actionLabel = action,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = dimens.screenPaddingH),
        )
        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = dimens.screenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            content = content,
        )
    }
}
