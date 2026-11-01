package com.nuvora.feature.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.DetailsState
import com.nuvora.core.data.sources.Episode
import com.nuvora.core.data.sources.SectionKind
import com.nuvora.designsystem.components.CategoryChip
import com.nuvora.designsystem.components.CoverCard
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Detalhes de um filme ou série, antes de assistir: capa, ano, duração, nota, gênero, sinopse,
 * direção e elenco (o que o provedor mandar; nada é inventado), Assistir e Favoritar.
 * Na série, as temporadas e os episódios ficam na própria tela.
 * Enquanto a pessoa lê, o filme já está sendo preparado por trás (ver PlayerWarmup).
 */
@Composable
fun DetailsScreen(
    state: DetailsState,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    BackHandler(onBack = onBack)
    val info = state.info
    val playFocus = remember { FocusRequester() }
    // Na TV o foco já começa no botão principal (Assistir, ou o 1º episódio na série).
    LaunchedEffect(state.itemId, state.loading) { if (isTv) runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // Fundo: a imagem grande do filme, bem escurecida (acabamento premium, sem atrapalhar a leitura).
        (info?.backdrop ?: info?.cover ?: state.imageUrl)?.let { image ->
            Box(Modifier.fillMaxWidth().height(if (isTv) 420.dp else 320.dp)) {
                coil3.compose.AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = 0.28f,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to colors.background)))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 32.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 16.dp),
        ) {
            NuvoraButton(text = "Voltar", icon = NuvoraIcons.ChevronLeft, style = NuvoraButtonStyle.Secondary, onClick = onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(if (isTv) 32.dp else 16.dp)) {
                Box(Modifier.widthIn(max = if (isTv) 220.dp else 130.dp).fillMaxWidth(if (isTv) 0.22f else 0.38f)) {
                    CoverCard(title = state.title, imageUrl = info?.cover ?: state.imageUrl, rating = null, onClick = {})
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NuvoraText(state.title, NuvoraTheme.typography.title, maxLines = 4)
                    val line = listOfNotNull(
                        info?.year,
                        info?.duration,
                        if (state.kind == SectionKind.Series && state.episodes.isNotEmpty()) {
                            val seasons = state.episodes.map { it.season }.distinct().size
                            if (seasons == 1) "1 temporada" else "$seasons temporadas"
                        } else {
                            null
                        },
                        (info?.rating ?: state.rating)?.let { "★ $it" },
                    )
                    if (line.isNotEmpty()) NuvoraText(line.joinToString(" · "), NuvoraTheme.typography.label, color = colors.accent)
                    info?.genre?.let { NuvoraText(it, NuvoraTheme.typography.caption, color = colors.textSecondary) }
                    if (isTv) {
                        info?.plot?.let { NuvoraText(it, NuvoraTheme.typography.body, color = colors.textPrimary, maxLines = 6) }
                        Actions(state, isFavorite, onPlay, onToggleFavorite, playFocus)
                        Credits(state)
                    }
                }
            }
            if (!isTv) {
                info?.plot?.let { NuvoraText(it, NuvoraTheme.typography.body, color = colors.textPrimary) }
                Actions(state, isFavorite, onPlay, onToggleFavorite, playFocus)
                Credits(state)
            }
            if (state.loading) {
                NuvoraText("Buscando as informações no provedor...", NuvoraTheme.typography.caption, color = colors.textSecondary)
            }
            if (state.kind == SectionKind.Series) Episodes(state, onPlayEpisode, playFocus)
        }
    }
}

@Composable
private fun Actions(state: DetailsState, isFavorite: Boolean, onPlay: () -> Unit, onToggleFavorite: () -> Unit, playFocus: FocusRequester) {
    val colors = NuvoraTheme.colors
    if (state.kind == SectionKind.Series) {
        NuvoraButton(
            text = if (isFavorite) "Nos favoritos" else "Favoritar",
            icon = if (isFavorite) NuvoraIcons.HeartFilled else NuvoraIcons.Heart,
            style = NuvoraButtonStyle.Secondary,
            onClick = onToggleFavorite,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        NuvoraButton(text = "Assistir", icon = NuvoraIcons.Play, onClick = onPlay, modifier = Modifier.focusRequester(playFocus))
        NuvoraButton(
            text = if (isFavorite) "Nos favoritos" else "Favoritar",
            icon = if (isFavorite) NuvoraIcons.HeartFilled else NuvoraIcons.Heart,
            style = NuvoraButtonStyle.Secondary,
            onClick = onToggleFavorite,
        )
    }
    if (state.request != null) {
        NuvoraText("⚡ Preparando o vídeo enquanto você lê", NuvoraTheme.typography.caption, color = colors.accent)
    }
}

@Composable
private fun Credits(state: DetailsState) {
    val colors = NuvoraTheme.colors
    val info = state.info ?: return
    val parts = listOfNotNull(info.director?.let { "Direção: $it" }, info.cast?.let { "Elenco: $it" })
    if (parts.isNotEmpty()) NuvoraText(parts.joinToString("  ·  "), NuvoraTheme.typography.caption, color = colors.textSecondary)
}

@Composable
private fun Episodes(state: DetailsState, onPlayEpisode: (Episode) -> Unit, firstFocus: FocusRequester) {
    val colors = NuvoraTheme.colors
    if (state.episodes.isEmpty()) {
        if (!state.loading) NuvoraText("O provedor não mandou os episódios desta série.", NuvoraTheme.typography.body, color = colors.textSecondary)
        return
    }
    val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct().sorted() }
    var season by rememberSaveable(state.itemId) { mutableIntStateOf(seasons.first()) }
    if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons) { number ->
                CategoryChip(label = "Temporada $number", selected = number == season, onClick = { season = number })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.episodes.filter { it.season == season }.forEachIndexed { index, episode ->
            NuvoraClickable(
                onClick = { onPlayEpisode(episode) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                containerColor = colors.surface,
                focusedContainerColor = colors.surfaceRaised,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NuvoraText("E${episode.number} · ${episode.title}", NuvoraTheme.typography.label, maxLines = 2, modifier = Modifier.weight(1f))
                    NuvoraIcon(NuvoraIcons.Play, tint = colors.accent, size = 20.dp)
                }
            }
        }
    }
}
