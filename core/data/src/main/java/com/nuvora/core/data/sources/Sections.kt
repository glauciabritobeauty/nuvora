package com.nuvora.core.data.sources

import androidx.compose.runtime.Immutable

/** As três partes do conteúdo, como a pessoa escolhe na tela de escolha. */
enum class SectionKind(val key: String, val label: String, val unit: String, internal val groupPrefix: String) {
    Live("live", "TV ao vivo", "canais", "TV"),
    Movies("vod", "Filmes", "filmes", "Filmes"),
    Series("series", "Séries", "séries", "Séries"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Live

        /**
         * De que parte é um item de lista M3U: pelo endereço (padrão dos provedores: /movie/ e /series/)
         * ou pela extensão de arquivo de filme. O resto é canal ao vivo.
         */
        internal fun guess(url: String, group: String?): SectionKind {
            group?.let { g -> entries.firstOrNull { g.startsWith(it.groupPrefix + " · ") }?.let { return it } }
            val path = url.substringBefore('?').lowercase()
            return when {
                "/series/" in path || path.startsWith(XtreamClient.SERIES_PREFIX) -> Series
                "/movie/" in path || path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") -> Movies
                else -> Live
            }
        }
    }
}

/** Uma categoria de uma lista (ex.: "Esportes" da TV ao vivo). [loaded] = os itens já estão no aparelho. */
@Immutable
data class CategoryRef(
    val sourceId: Long,
    val kind: SectionKind,
    val id: String,
    val name: String,
    val loaded: Boolean,
    val itemCount: Int,
)

/** Resumo de uma parte para os blocos da tela de escolha. */
@Immutable
data class SectionSummary(val kind: SectionKind, val categories: Int, val items: Int)

/** Dados da conta que o provedor mandou no login (para o topo da tela de escolha). */
@Immutable
data class AccountInfo(val sourceName: String, val user: String?, val expiresAt: Long?, val screens: Int?)

/**
 * Andamento do carregamento em segundo plano.
 * [totalCategories] = 0 quando o provedor só entrega a lista inteira (aí só dá para contar os itens).
 */
@Immutable
data class LoadProgress(
    val doneCategories: Int,
    val totalCategories: Int,
    val items: Int,
    val failed: Boolean = false,
)

/** O que o provedor sabe de um filme ou série (tela de detalhes). Campo vazio = o provedor não mandou. */
@Immutable
data class ItemInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val cover: String? = null,
    val backdrop: String? = null,
)

/** Tudo o que a tela de escolha mostra. */
@Immutable
data class HubData(
    val account: AccountInfo?,
    val sections: List<SectionSummary>,
    val totalItems: Int,
    /** Todas as listas terminaram de carregar. */
    val complete: Boolean,
)
