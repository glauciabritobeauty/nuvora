package com.nuvora.core.data

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.core.data.library.FavoriteEntry
import com.nuvora.core.data.library.HistoryEntry
import com.nuvora.core.data.library.LibraryRepository
import com.nuvora.core.data.library.WatchProgress
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HomeContent
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tudo o que as telas (celular e TV) precisam mostrar. */
@Immutable
data class AppUiState(
    val home: HomeUiState = HomeUiState.Loading,
    val demoOn: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val favorites: List<CardItem> = emptyList(),
    val history: List<CardItem> = emptyList(),
    val catalog: List<CardItem> = emptyList(),
    val categories: List<String> = emptyList(),
) {
    val hasSource: Boolean get() = home is HomeUiState.Content
}

/**
 * "Cérebro" do app, compartilhado por celular e TV: junta a fonte de conteúdo (por enquanto,
 * só a demonstração da versão de teste) com os favoritos e o histórico salvos no aparelho.
 * Sobrevive à rotação da tela; as telas só leem [state] e chamam as ações.
 */
class NuvoraViewModel(application: Application) : AndroidViewModel(application) {
    private val library = LibraryRepository(application)
    private val settings = SettingsRepository(application)
    private val demoOn = MutableStateFlow(false)

    /** Preferência de legendas (Ajustes). */
    val subtitleMode: StateFlow<SubtitleMode> = settings.subtitleMode

    fun setSubtitleMode(mode: SubtitleMode) = settings.setSubtitleMode(mode)

    val state: StateFlow<AppUiState> = combine(
        library.loaded,
        demoOn,
        library.favorites,
        library.history,
    ) { loaded, demo, favorites, history -> buildState(loaded, demo, favorites, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch { library.load() }
    }

    fun setDemo(on: Boolean) {
        demoOn.value = on
        if (on) {
            viewModelScope.launch {
                if (library.history.value.none { it.id.startsWith(DemoContent.ID_PREFIX) }) {
                    library.saveProgress(DemoContent.historySeed(System.currentTimeMillis()))
                }
            }
        }
    }

    fun toggleFavorite(id: String) {
        val known = DemoContent.items.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
            ?: DemoContent.hero.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
            ?: library.favorites.value.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
            ?: return
        viewModelScope.launch { library.toggleFavorite(id, known.first, known.second) }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch { library.removeFavorite(id) }
    }

    fun removeFromHistory(id: String) {
        viewModelScope.launch { library.removeFromHistory(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    /** O que tocar para este item, ou null se não houver fonte para ele. */
    fun playbackFor(id: String): PlaybackRequest? = DemoContent.playbackFor(id)

    /** De onde continuar: a posição salva, se a pessoa parou no meio; senão, do início. */
    fun resumePositionFor(id: String): Long =
        library.history.value.firstOrNull { it.id == id }?.takeIf(WatchProgress::isUnfinished)?.positionMs ?: 0L

    /** Chamado pelo player a cada 10 s e ao sair. */
    fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            library.saveProgress(
                listOf(HistoryEntry(request.id, request.title, request.imageUrl, positionMs, durationMs, System.currentTimeMillis())),
            )
        }
    }

    fun search(query: String): List<CardItem> =
        if (query.isBlank()) emptyList() else state.value.catalog.filter { it.title.contains(query.trim(), ignoreCase = true) }

    private fun buildState(
        loaded: Boolean,
        demo: Boolean,
        favorites: List<FavoriteEntry>,
        history: List<HistoryEntry>,
    ): AppUiState {
        // Itens de exemplo só aparecem com a demonstração ligada.
        fun visible(id: String) = demo || !id.startsWith(DemoContent.ID_PREFIX)

        val favoriteCards = favorites.filter { visible(it.id) }.map { CardItem(it.id, it.title, it.imageUrl) }
        val visibleHistory = history.filter { visible(it.id) }
        val historyCards = visibleHistory.map {
            val finished = WatchProgress.fraction(it) > WatchProgress.MAX_FRACTION
            CardItem(it.id, it.title, it.imageUrl, caption = if (finished) "Assistido" else WatchProgress.remainingLabel(it))
        }
        val continueCards = visibleHistory.filter(WatchProgress::isUnfinished).map {
            CardItem(it.id, it.title, it.imageUrl, caption = WatchProgress.remainingLabel(it), progress = WatchProgress.fraction(it))
        }
        val catalog = if (demo) DemoContent.items else emptyList()
        val home = when {
            !loaded -> HomeUiState.Loading
            demo -> HomeUiState.Content(
                HomeContent(
                    hero = DemoContent.hero,
                    continueWatching = continueCards,
                    categories = DemoContent.categories,
                    recent = catalog,
                    favorites = favoriteCards,
                    history = historyCards,
                ),
            )
            else -> HomeUiState.NoSource
        }
        return AppUiState(
            home = home,
            demoOn = demo,
            favoriteIds = favorites.mapTo(HashSet()) { it.id },
            favorites = favoriteCards,
            history = historyCards,
            catalog = catalog,
            categories = if (demo) DemoContent.categories else emptyList(),
        )
    }
}
