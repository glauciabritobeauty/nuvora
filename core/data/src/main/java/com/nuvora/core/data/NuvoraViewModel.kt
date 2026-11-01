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
import com.nuvora.core.data.sources.CategoryRef
import com.nuvora.core.data.sources.HubData
import com.nuvora.core.data.sources.LoadProgress
import com.nuvora.core.data.sources.SectionKind
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    /** Servidor (DNS) do último login que deu certo (null = nenhum ainda). */
    val savedServer: StateFlow<String?> = sourcesRepo.savedServer

    /** Andamento do carregamento em segundo plano (null = nada carregando). */
    val loading: StateFlow<LoadProgress?> = sourcesRepo.loading

    private val _hub = MutableStateFlow<HubData?>(null)

    /** Tela de escolha: conta, TV ao vivo / Filmes / Séries e contagens (null = ainda lendo). */
    val hub: StateFlow<HubData?> = _hub.asStateFlow()

    private var background: Job? = null

    /** Muda toda vez que chegam itens novos. */
    val changes: StateFlow<Long> = sourcesRepo.changes

    /** Botão Atualizar da tela de escolha: tenta de novo o que falhou ou lê as listas outra vez. */
    fun refreshAll() {
        if (sourcesRepo.loading.value?.failed == true) {
            retryLoading()
            return
        }
        viewModelScope.launch { sources.value.forEach { refreshSource(it.id) } }
    }

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
        // Vídeo tocando: o carregamento em segundo plano pausa (a internet fica toda para o vídeo).
        viewModelScope.launch { _playing.collect { sourcesRepo.videoPlaying = it != null } }
        // Cronômetro: quanto tempo do app aberto até a tela de escolha pronta.
        val opened = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            hub.first { it != null }
            sourcesRepo.notePerformance("Abrir o app até a tela de escolha: ${SystemClock.elapsedRealtime() - opened} ms")
        }
        viewModelScope.launch {
            sourcesRepo.load()
            // Sempre que as listas mudam, refaz a amostra da Home (fora da tela, sem travar a rolagem).
            loadInBackground()
            // Sempre que as listas mudam, refaz a amostra da Home (fora da tela, sem travar a rolagem).
            sourcesRepo.sources.collectLatest { list ->
                if (list.isEmpty()) {
                    catalog.value = null
                } else if (catalog.value == null) {
                    // Libera as telas na hora; a amostra da Home (só usada em partes antigas) vem logo depois.
                    catalog.value = SourceCatalog(emptyList(), emptyList(), emptyList())
                }
                sourcesReady.value = true
                if (list.isNotEmpty()) catalog.value = sourcesRepo.loadCatalog()
            }
        }
        viewModelScope.launch {
            // A tela de escolha acompanha cada lote que chega (no máximo 2 vezes por segundo).
            combine(sourcesRepo.sources, sourcesRepo.changes) { _, _ -> }.collectLatest {
                _hub.value = sourcesRepo.hub()
                delay(500)
            }
        }
    }

    /** Carrega em segundo plano o que falta das listas de provedor (sem travar nada). */
    private fun loadInBackground() {
        if (background?.isActive == true) return
        background = viewModelScope.launch { sourcesRepo.loadPending() }
    }

    // Memória do que as telas de cada parte já mostraram: ao voltar de um vídeo, a lista aparece na hora.
    private val browseMemory = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> remembered(key: String, version: Long): T? = browseMemory[key]?.takeIf { it.first == version }?.second as T?

    /** O que a tela de uma parte mostrou por último (para aparecer na hora ao voltar). */
    fun cachedCategories(kind: SectionKind): List<CategoryRef>? = browseMemory["c/${kind.key}"]?.second as? List<CategoryRef>

    fun cachedItems(kind: SectionKind, key: String): List<CardItem>? = browseMemory["i/${kind.key}/$key"]?.second as? List<CardItem>

    /** Categorias de uma parte (TV ao vivo, Filmes, Séries). */
    suspend fun categories(kind: SectionKind): List<CategoryRef> {
        val version = changes.value
        remembered<List<CategoryRef>>("c/${kind.key}", version)?.let { return it }
        val mark = SystemClock.elapsedRealtime()
        return sourcesRepo.categories(kind).also {
            browseMemory["c/${kind.key}"] = version to it
            sourcesRepo.notePerformance("${kind.label}: ${it.size} categorias em ${SystemClock.elapsedRealtime() - mark} ms")
        }
    }

    /** Itens de uma categoria (busca na hora se ainda não chegaram). */
    suspend fun categoryItems(ref: CategoryRef): Result<List<CardItem>> {
        val key = "i/${ref.kind.key}/${ref.sourceId}/${ref.id}"
        if (ref.loaded) remembered<List<CardItem>>(key, 0L)?.let { return Result.success(it) }
        val mark = SystemClock.elapsedRealtime()
        return sourcesRepo.categoryItems(ref)
            .onSuccess { if (it.isNotEmpty()) browseMemory[key] = 0L to it }
            .also { result ->
                val how = if (ref.loaded) "já estava no aparelho" else "buscou no provedor"
                val outcome = result.fold({ "${it.size} itens" }, { "falhou (${it.javaClass.simpleName})" })
                sourcesRepo.notePerformance("${ref.kind.label} › ${ref.name}: ${SystemClock.elapsedRealtime() - mark} ms, $outcome, $how")
            }
    }

    /** Todos os itens de uma parte que já chegaram. */
    suspend fun sectionItems(kind: SectionKind): List<CardItem> {
        val key = "i/${kind.key}/\u0000todos"
        val version = changes.value
        remembered<List<CardItem>>(key, version)?.let { return it }
        val mark = SystemClock.elapsedRealtime()
        return sourcesRepo.sectionItems(kind).also {
            browseMemory[key] = version to it
            sourcesRepo.notePerformance("${kind.label} › Todos: ${it.size} itens em ${SystemClock.elapsedRealtime() - mark} ms")
        }
    }

    /** Último relatório de conexão (sem senha), para Ajustes. */
    val lastReport: StateFlow<String?> = sourcesRepo.lastReport

    /** Busca dentro de uma parte. */
    suspend fun searchIn(kind: SectionKind, text: String): List<CardItem> = sourcesRepo.searchIn(kind, text)

    /** Favoritos desta parte. */
    suspend fun favoritesIn(kind: SectionKind): List<CardItem> =
        sourcesRepo.favoritesIn(kind, library.favorites.value.map { it.id })

    /** Tenta de novo o carregamento em segundo plano (botão Atualizar quando algo falhou). */
    fun retryLoading() {
        background?.cancel()
        background = null
        loadInBackground()
    }

    /** "Sair da conta": tira todas as listas do aparelho (o servidor salvo continua, para entrar de novo fácil). */
    fun logout(onDone: () -> Unit = {}) {
        browseMemory.clear()
        viewModelScope.launch {
            background?.cancelAndJoin()
            background = null
            sourcesRepo.sources.value.forEach { sourcesRepo.remove(it.id) }
            onDone()
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
        sourcesRepo.addProvider(name, login, onProgress).also { if (it is ImportResult.Success) loadInBackground() }

    suspend fun refreshSource(id: Long): ImportResult {
        browseMemory.clear()
        background?.cancelAndJoin()
        background = null
        return sourcesRepo.refresh(id).also { loadInBackground() }
    }

    fun removeSource(id: Long) {
        browseMemory.clear()
        viewModelScope.launch {
            background?.cancelAndJoin()
            background = null
            sourcesRepo.remove(id)
            loadInBackground()
        }
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

    /** O player avisa quando o vídeo apareceu (vai para o cronômetro do relatório). */
    fun noteFirstFrame(request: PlaybackRequest, millis: Long) {
        sourcesRepo.notePerformance("Vídeo \"${request.title.take(40)}\" começou em $millis ms")
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
