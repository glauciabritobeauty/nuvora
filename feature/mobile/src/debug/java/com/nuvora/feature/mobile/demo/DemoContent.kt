package com.nuvora.feature.mobile.demo

import com.nuvora.feature.mobile.home.CardItem
import com.nuvora.feature.mobile.home.HeroItem
import com.nuvora.feature.mobile.home.HomeContent

/**
 * Demonstração do layout, SÓ na versão de teste (debug).
 * Todos os itens são identificados como exemplo e não têm capa.
 * A versão final usa o arquivo de src/release, que não tem demonstração.
 */
internal object DemoContent {
    const val AVAILABLE = true

    private val categories = listOf("Filmes", "Séries", "Ao vivo", "Documentários")

    private val items = List(16) { i ->
        val category = categories[i % categories.size]
        CardItem(id = "demo-${i + 1}", title = "Exemplo ${i + 1}", imageUrl = null, category = category, caption = category)
    }

    private val hero = List(3) { i ->
        HeroItem(
            id = "demo-destaque-${i + 1}",
            title = "Destaque de exemplo ${i + 1}",
            subtitle = "Demonstração · sem conteúdo real",
            imageUrl = null,
        )
    }

    fun home(favorites: Set<String>): HomeContent? {
        val heroCards = hero.map { CardItem(it.id, it.title, it.imageUrl) }
        return HomeContent(
            hero = hero,
            continueWatching = items.take(4).mapIndexed { i, item ->
                item.copy(
                    progress = listOf(0.62f, 0.3f, 0.85f, 0.12f)[i],
                    caption = "Faltam ${listOf(19, 35, 7, 44)[i]} min",
                )
            },
            categories = categories,
            recent = items,
            favorites = (heroCards + items).filter { it.id in favorites },
            history = items.drop(4).take(5),
        )
    }

    fun search(query: String): List<CardItem> =
        if (query.isBlank()) emptyList() else items.filter { it.title.contains(query.trim(), ignoreCase = true) }
}
