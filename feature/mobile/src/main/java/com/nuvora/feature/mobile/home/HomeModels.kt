package com.nuvora.feature.mobile.home

import androidx.compose.runtime.Immutable

/** Item pronto para virar cartão. Os dados reais virão da fonte conectada (Etapa 7). */
@Immutable
data class CardItem(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val category: String? = null,
    val caption: String? = null,
    val progress: Float? = null,
)

@Immutable
data class HeroItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
)

@Immutable
data class HomeContent(
    val hero: List<HeroItem>,
    val continueWatching: List<CardItem>,
    val categories: List<String>,
    val recent: List<CardItem>,
    val favorites: List<CardItem>,
    val history: List<CardItem>,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object NoSource : HomeUiState
    data class Error(val noConnection: Boolean) : HomeUiState
    data class Content(val content: HomeContent) : HomeUiState
}

/** Seções que têm "Ver tudo". */
enum class HomeSection(val title: String) {
    ContinueWatching("Continuar assistindo"),
    Recent("Adicionados recentemente"),
    Favorites("Favoritos"),
    History("Histórico"),
}
