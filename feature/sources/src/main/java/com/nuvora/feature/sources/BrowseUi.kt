package com.nuvora.feature.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.sources.CategoryRef
import com.nuvora.core.data.sources.HubData
import com.nuvora.core.data.sources.LoadProgress
import com.nuvora.core.data.sources.SectionKind
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.ChannelRow
import com.nuvora.designsystem.components.CoverCard
import com.nuvora.designsystem.components.EmptyState
import com.nuvora.designsystem.components.LoadingState
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraMark
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.NuvoraTextField
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ícone de cada parte nos blocos da tela de escolha. */
private val SectionKind.icon: ImageVector
    get() = when (this) {
        SectionKind.Live -> NuvoraIcons.Source
        SectionKind.Movies -> NuvoraIcons.Play
        SectionKind.Series -> NuvoraIcons.Categories
    }

/**
 * Tela de escolha depois do login: a conta no topo, três blocos grandes (TV ao vivo, Filmes, Séries)
 * e os botões Atualizar e Sair da conta. Abre na hora, com o que já está no aparelho; o resto chega
 * em segundo plano, com um aviso discreto e honesto do andamento.
 */
@Composable
fun HubScreen(
    hub: HubData?,
    loading: LoadProgress?,
    versionName: String,
    onOpen: (SectionKind) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    /** Atalhos extras (na TV: Buscar e Favoritos). */
    shortcuts: @Composable () -> Unit = {},
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val firstTile = remember { FocusRequester() }
    var confirmLogout by remember { mutableStateOf(false) }
    if (hub == null) {
        LoadingState(Modifier.fillMaxSize())
        return
    }
    // Na TV o foco já começa no primeiro bloco.
    LaunchedEffect(Unit) { if (isTv) runCatching { firstTile.requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 40.dp else 20.dp, vertical = if (isTv) 32.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 14.dp),
    ) {
        AccountHeader(hub)
        LoadingNotice(hub, loading, onRetry = onRefresh)
        val tiles = @Composable { kind: SectionKind, modifier: Modifier ->
            val summary = hub.sections.firstOrNull { it.kind == kind }
            SectionTile(
                kind = kind,
                detail = when {
                    summary == null || summary.categories == 0 -> if (hub.complete) "Nada nesta parte" else "Carregando..."
                    hub.complete && summary.items > 0 -> "${formatCount(summary.items)} ${kind.unit}"
                    else -> "${formatCount(summary.categories)} categorias"
                },
                onClick = { onOpen(kind) },
                modifier = if (kind == SectionKind.Live) modifier.focusRequester(firstTile) else modifier,
            )
        }
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionKind.entries.forEach { tiles(it, Modifier.weight(1f).height(200.dp)) }
            }
        } else {
            SectionKind.entries.forEach { tiles(it, Modifier.fillMaxWidth().height(118.dp)) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, if (isTv) Alignment.CenterHorizontally else Alignment.Start),
        ) {
            NuvoraButton(text = "Atualizar", icon = NuvoraIcons.Refresh, style = NuvoraButtonStyle.Secondary, onClick = onRefresh)
            shortcuts()
            NuvoraButton(text = "Sair da conta", icon = NuvoraIcons.ChevronLeft, style = NuvoraButtonStyle.Secondary, onClick = { confirmLogout = true })
        }
        NuvoraText(
            "NUVORA $versionName",
            NuvoraTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    if (confirmLogout) {
        com.nuvora.designsystem.components.NuvoraDialog(
            title = "Sair da conta?",
            message = "As listas saem deste aparelho. O servidor fica salvo: para entrar de novo, basta usuário e senha.",
            onDismiss = { confirmLogout = false },
            actions = listOf(
                com.nuvora.designsystem.components.DialogAction("Sair da conta", onLogout, NuvoraButtonStyle.Primary),
                com.nuvora.designsystem.components.DialogAction("Cancelar", {}),
            ),
        )
    }
}

@Composable
private fun AccountHeader(hub: HubData) {
    val colors = NuvoraTheme.colors
    val account = hub.account
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(if (NuvoraTheme.isTv) 60.dp else 52.dp)
                .background(colors.surfaceRaised, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { NuvoraMark(Modifier.size(30.dp)) }
        Column {
            NuvoraText(account?.sourceName ?: "NUVORA", NuvoraTheme.typography.title, maxLines = 1)
            val details = listOfNotNull(
                account?.user,
                account?.expiresAt?.let { "vence ${SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it * 1000))}" },
                account?.screens?.let { if (it == 1) "1 tela" else "$it telas" },
            )
            if (details.isNotEmpty()) {
                NuvoraText(details.joinToString(" · "), NuvoraTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Aviso discreto enquanto a lista ainda está chegando (nunca diz "pronto" antes da hora). */
@Composable
private fun LoadingNotice(hub: HubData, loading: LoadProgress?, onRetry: () -> Unit) {
    val colors = NuvoraTheme.colors
    if (hub.complete && loading == null) return
    val text = when {
        loading?.failed == true -> "Parte da lista não carregou agora. Toque em Atualizar para tentar de novo."
        loading != null && loading.totalCategories > 0 ->
            "Carregando o resto da lista em segundo plano: ${loading.doneCategories} de ${loading.totalCategories} categorias. Já dá para assistir."
        loading != null -> "Carregando a lista em segundo plano: ${formatCount(loading.items)} itens até agora. Já dá para assistir."
        else -> "Carregando o resto da lista em segundo plano. Já dá para assistir."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NuvoraText(text, NuvoraTheme.typography.caption, color = if (loading?.failed == true) colors.error else colors.textSecondary)
        if (loading != null && loading.totalCategories > 0 && !loading.failed) {
            val fraction = loading.doneCategories.toFloat() / loading.totalCategories
            Box(Modifier.fillMaxWidth().height(3.dp).background(colors.outline, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(3.dp).background(colors.accent, RoundedCornerShape(2.dp)))
            }
        }
        if (loading?.failed == true) NuvoraButton(text = "Tentar de novo", style = NuvoraButtonStyle.Secondary, onClick = onRetry)
    }
}

@Composable
private fun SectionTile(kind: SectionKind, detail: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = NuvoraTheme.colors
    NuvoraClickable(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        containerColor = colors.surface,
        focusedContainerColor = colors.surfaceRaised,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            NuvoraIcon(kind.icon, tint = colors.accent, size = if (NuvoraTheme.isTv) 40.dp else 34.dp)
            NuvoraText(kind.label, NuvoraTheme.typography.section)
            NuvoraText(detail, NuvoraTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

/** O que a pessoa escolheu na linha de categorias. */
private const val FAVORITES = "\u0000favoritos"
private const val ALL = "\u0000todos"

/**
 * Dentro de uma parte: voltar, título, busca da parte, categorias numa linha que rola para o lado e,
 * embaixo, as capas (filmes e séries, 3 por linha no celular) ou a lista de canais (TV ao vivo).
 * Só a categoria aberta é buscada no provedor; o resto já pode estar chegando em segundo plano.
 */
@Composable
fun BrowseScreen(
    kind: SectionKind,
    /** Muda quando chegam itens novos (para atualizar sem a pessoa fazer nada). */
    changes: Long,
    loadCategories: suspend (SectionKind) -> List<CategoryRef>,
    loadItems: suspend (CategoryRef) -> Result<List<CardItem>>,
    loadAll: suspend (SectionKind) -> List<CardItem>,
    loadFavorites: suspend (SectionKind) -> List<CardItem>,
    search: suspend (SectionKind, String) -> List<CardItem>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onLongPress: ((CardItem) -> Unit)? = null,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    BackHandler(onBack = onBack)
    var query by rememberSaveable(kind) { mutableStateOf("") }
    val categories by produceState<List<CategoryRef>?>(null, kind, changes) { value = loadCategories(kind) }
    // TV ao vivo começa em "Todos os canais"; filmes e séries, na 1ª categoria.
    var selected by rememberSaveable(kind) { mutableStateOf(if (kind == SectionKind.Live) ALL else "") }
    LaunchedEffect(categories) { if (selected.isEmpty()) categories?.firstOrNull()?.let { selected = it.key } }
    val current = categories?.firstOrNull { it.key == selected }
    var failed by remember(kind, selected) { mutableStateOf(false) }
    // "Todos" e favoritos acompanham o que vai chegando; uma categoria aberta só recarrega quando termina de chegar.
    val items by produceState<List<CardItem>?>(null, kind, selected, query, current?.loaded, if (selected == ALL) changes else 0L) {
        value = when {
            query.isNotBlank() -> search(kind, query)
            selected == FAVORITES -> loadFavorites(kind)
            selected == ALL -> loadAll(kind)
            current != null -> loadItems(current).getOrElse { failed = true; emptyList() }
            else -> if (categories == null) null else emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(top = if (isTv) 24.dp else 8.dp)) {
        Row(
            Modifier.padding(horizontal = if (isTv) 32.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NuvoraButton(text = null, icon = NuvoraIcons.ChevronLeft, style = NuvoraButtonStyle.Secondary, onClick = onBack, contentDescription = "Voltar")
            NuvoraText(kind.label, NuvoraTheme.typography.title, maxLines = 1)
            Box(Modifier.weight(1f).widthIn(max = 420.dp)) {
                NuvoraTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Buscar em ${kind.label}",
                    placeholder = "Buscar",
                    showLabel = false,
                )
            }
        }
        if (query.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = if (isTv) 32.dp else 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { CategoryChip(label = "★ Favoritos", selected = selected == FAVORITES, onClick = { selected = FAVORITES }) }
                item {
                    CategoryChip(
                        label = if (kind == SectionKind.Live) "Todos os canais" else "Todos",
                        selected = selected == ALL,
                        onClick = { selected = ALL },
                    )
                }
                items(categories.orEmpty(), key = { it.key }) { category ->
                    CategoryChip(label = category.name, selected = category.key == selected, onClick = { selected = category.key })
                }
            }
        } else {
            NuvoraText(
                "Resultados em ${kind.label}",
                NuvoraTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = 12.dp),
            )
        }
        val list = items
        // Na TV o foco do controle vai direto para o 1º canal/capa quando a lista aparece.
        val firstItem = remember(kind) { FocusRequester() }
        LaunchedEffect(kind, !list.isNullOrEmpty()) { if (isTv && !list.isNullOrEmpty()) runCatching { firstItem.requestFocus() } }
        when {
            list == null -> LoadingState(Modifier.fillMaxSize())
            list.isEmpty() -> EmptyState(
                if (selected == FAVORITES) NuvoraIcons.Heart else NuvoraIcons.Categories,
                when {
                    failed -> "Não deu para abrir esta categoria agora"
                    query.isNotBlank() -> "Nada encontrado"
                    selected == FAVORITES -> "Nenhum favorito aqui ainda"
                    else -> "Nada aqui ainda"
                },
                when {
                    failed -> "Confira a internet e toque na categoria de novo."
                    selected == ALL || query.isNotBlank() -> "A lista ainda pode estar chegando: tente de novo em instantes."
                    else -> "Escolha outra categoria."
                },
                Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            kind == SectionKind.Live -> LazyColumn(
                contentPadding = PaddingValues(start = if (isTv) 32.dp else 16.dp, end = if (isTv) 32.dp else 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(list, key = { _, item -> item.id }) { index, item ->
                    ChannelRow(
                        modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                        number = index + 1,
                        title = item.title,
                        logoUrl = item.imageUrl,
                        onClick = { onOpen(item.id) },
                        onLongClick = onLongPress?.let { { it(item) } },
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = if (isTv) GridCells.Adaptive(NuvoraTheme.dimens.posterWidth + 20.dp) else GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = if (isTv) 32.dp else 16.dp, end = if (isTv) 32.dp else 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list, key = { _, item -> item.id }) { index, item ->
                    CoverCard(
                        modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                        title = item.title,
                        imageUrl = item.imageUrl,
                        rating = item.rating,
                        onClick = { onOpen(item.id) },
                        onLongClick = onLongPress?.let { { it(item) } },
                    )
                }
            }
        }
    }
}

/** Chave única de uma categoria na tela (a mesma categoria pode existir em duas listas). */
private val CategoryRef.key: String get() = "$sourceId/$id"
