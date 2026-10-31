package com.nuvora.core.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nuvora.core.data.demo.DemoContent
import com.nuvora.core.data.library.FavoriteEntry
import com.nuvora.core.data.library.HistoryEntry
import com.nuvora.core.data.library.LibraryRepository
import com.nuvora.core.data.library.WatchProgress
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.HomeContent
import com.nuvora.core.data.model.HomeUiState
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.core.data.security.CryptoBox
import com.nuvora.core.data.settings.SettingsRepository
import com.nuvora.core.data.sources.Episode
import com.nuvora.core.data.sources.ImportProgress
import com.nuvora.core.data.sources.ProviderLogin
import com.nuvora.core.data.sources.XtreamClient
import com.nuvora.core.data.sources.ImportResult
import com.nuvora.core.data.sources.SourceCatalog
import com.nuvora.core.data.sources.SourceInfo
import com.nuvora.core.data.sources.SourcesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Série aberta, com a lista de episódios para escolher. */
@Immutable
data class SeriesEpisodes(val itemId: String, val title: String, val episodes: List<Episode>)

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
 * "Cérebro" do app, compartilhado por celular e TV: junta as listas da pessoa (ou a demonstração
 * da versão de teste) com os favoritos e o histórico salvos no aparelho.
 * Sobrevive à rotação da tela; as telas só leem o estado e chamam as ações.
 */
class NuvoraViewModel(application: Application) : AndroidViewModel(application) {
    private val library = LibraryRepository(application)
    private val settings = SettingsRepository(application)
    private val sourcesRepo = SourcesRepository(application, CryptoBox(application))
    private val demoOn = MutableStateFlow(false)
    private val catalog = MutableStateFlow<SourceCatalog?>(null)
    private val sourcesReady = MutableStateFlow(false)

    /** Preferência de legendas (Ajustes). */
    val subtitleMode: StateFlow<SubtitleMode> = settings.subtitleMode

    fun setSubtitleMode(mode: SubtitleMode) = settings.setSubtitleMode(mode)

    /** "Letra maior" (Ajustes). */
    val largeText: StateFlow<Boolean> = settings.largeText

    fun setLargeText(on: Boolean) = settings.setLargeText(on)

    /** Listas cadastradas (Ajustes > Fontes de conteúdo). */
    val sources: StateFlow<List<SourceInfo>> = sourcesRepo.sources

    private val _episodes = MutableStateFlow<SeriesEpisodes?>(null)

    /** Série aberta esperando a pessoa escolher o episódio (null = nenhuma). */
    val episodes: StateFlow<SeriesEpisodes?> = _episodes.asStateFlow()

    private val _playing = MutableStateFlow<PlaybackRequest?>(null)

    /** O vídeo aberto no player agora (null = nenhum). */
    val playing: StateFlow<PlaybackRequest?> = _playing.asStateFlow()

    val state: StateFlow<AppUiState> = combine(
        combine(library.loaded, sourcesReady) { libraryLoaded, ready -> libraryLoaded && ready },
        demoOn,
        library.favorites,
        library.history,
        catalog,
    ) { loaded, demo, favorites, history, sourceCatalog -> buildState(loaded, demo, favorites, history, sourceCatalog) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch { library.load() }
        viewModelScope.launch {
            sourcesRepo.load()
            // Sempre que as listas mudam, refaz a amostra da Home (fora da tela, sem travar a rolagem).
            sourcesRepo.sources.collectLatest { list ->
                catalog.value = if (list.isEmpty()) null else sourcesRepo.loadCatalog()
                sourcesReady.value = true
            }
        }
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

    // ---- Listas ----

    suspend fun addLinkSource(name: String, url: String, onProgress: (ImportProgress) -> Unit): ImportResult =
        sourcesRepo.addLink(name, url, onProgress)

    suspend fun addFileSource(name: String, uri: Uri, onProgress: (ImportProgress) -> Unit): ImportResult =
        sourcesRepo.addFile(name, uri, onProgress)

    suspend fun addProviderSource(name: String, login: ProviderLogin, onProgress: (ImportProgress) -> Unit): ImportResult =
        sourcesRepo.addProvider(name, login, onProgress)

    suspend fun refreshSource(id: Long): ImportResult = sourcesRepo.refresh(id)

    fun removeSource(id: Long) {
        viewModelScope.launch { sourcesRepo.remove(id) }
    }

    // ---- Favoritos e histórico ----

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            val known = DemoContent.items.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
                ?: DemoContent.hero.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
                ?: catalog.value?.items?.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
                ?: sourcesRepo.item(id)?.let { it.title to it.imageUrl }
                ?: library.favorites.value.firstOrNull { it.id == id }?.let { it.title to it.imageUrl }
                ?: return@launch
            library.toggleFavorite(id, known.first, known.second)
        }
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

    // ---- Player ----

    /** Abre o player para este item; se não houver vídeo para ele, chama [onUnavailable]. */
    fun play(id: String, onUnavailable: () -> Unit) {
        viewModelScope.launch {
            // Episódio salvo no histórico ("f1-5-e401"): busca a série e toca direto aquele episódio.
            val episodeId = id.substringAfter("-e", missingDelimiterValue = "").takeIf { id.startsWith(SourcesRepository.ID_PREFIX) && it.isNotEmpty() }
            if (episodeId != null) {
                val seriesItem = id.substringBeforeLast("-e")
                val series = sourcesRepo.playback(seriesItem)
                val episode = sourcesRepo.episodes(seriesItem).firstOrNull { it.id == episodeId }
                if (series == null || episode == null) onUnavailable() else playEpisode(SeriesEpisodes(seriesItem, series.title, emptyList()), episode)
                return@launch
            }
            val request = DemoContent.playbackFor(id) ?: sourcesRepo.playback(id)
            when {
                request == null -> onUnavailable()
                request.url.startsWith(XtreamClient.SERIES_PREFIX) -> {
                    val list = sourcesRepo.episodes(id)
                    if (list.isEmpty()) onUnavailable() else _episodes.value = SeriesEpisodes(id, request.title, list)
                }
                else -> _playing.value = request
            }
        }
    }

    fun playEpisode(series: SeriesEpisodes, episode: Episode) {
        _episodes.value = null
        _playing.value = PlaybackRequest(
            id = "${series.itemId}-e${episode.id}",
            title = "${series.title} · ${episode.label}",
            subtitle = null,
            url = episode.url,
            mimeType = null,
            imageUrl = null,
        )
    }

    fun closeEpisodes() {
        _episodes.value = null
    }

    fun closePlayer() {
        _playing.value = null
    }

    /** De onde continuar: a posição salva, se a pessoa parou no meio; senão, do início. */
    fun resumePositionFor(id: String): Long =
        library.history.value.firstOrNull { it.id == id }?.takeIf(WatchProgress::isUnfinished)?.positionMs ?: 0L

    /** Chamado pelo player a cada 10 s e ao sair. Ao vivo (sem duração) não entra no histórico. */
    fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            library.saveProgress(
                listOf(HistoryEntry(request.id, request.title, request.imageUrl, positionMs, durationMs, System.currentTimeMillis())),
            )
        }
    }

    /** Busca na demonstração ou no banco das listas (nunca na lista inteira em memória). */
    suspend fun search(query: String): List<CardItem> = when {
        query.isBlank() -> emptyList()
        demoOn.value -> DemoContent.items.filter { it.title.contains(query.trim(), ignoreCase = true) }
        else -> sourcesRepo.search(query)
    }

    private fun buildState(
        loaded: Boolean,
        demo: Boolean,
        favorites: List<FavoriteEntry>,
        history: List<HistoryEntry>,
        sourceCatalog: SourceCatalog?,
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
        val source: Triple<List<CardItem>, List<String>, List<HeroItem>> = when {
            demo -> Triple(DemoContent.items, DemoContent.categories, DemoContent.hero)
            sourceCatalog != null -> Triple(sourceCatalog.items, sourceCatalog.categories, sourceCatalog.hero)
            else -> Triple(emptyList(), emptyList(), emptyList())
        }
        val (catalogItems, categories, hero) = source
        val home = when {
            !loaded -> HomeUiState.Loading
            demo || sourceCatalog != null -> HomeUiState.Content(
                HomeContent(
                    hero = hero,
                    continueWatching = continueCards,
                    categories = categories,
                    recent = catalogItems,
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
            catalog = catalogItems,
            categories = categories,
        )
    }
}
