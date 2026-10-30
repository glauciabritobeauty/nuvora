package com.nuvora.core.data.demo

import com.nuvora.core.data.library.HistoryEntry
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem

/**
 * Demonstração do layout, SÓ na versão de teste (debug).
 * Todos os itens são identificados como exemplo, não têm capa e usam ids começando com [ID_PREFIX].
 * A versão final usa o arquivo de src/release, que não tem demonstração.
 */
object DemoContent {
    const val AVAILABLE = true
    const val ID_PREFIX = "demo-"

    val categories = listOf("Filmes", "Séries", "Ao vivo", "Documentários")

    val items: List<CardItem> = List(16) { i ->
        val category = categories[i % categories.size]
        CardItem(id = "${ID_PREFIX}${i + 1}", title = "Exemplo ${i + 1}", imageUrl = null, category = category, caption = category)
    }

    val hero: List<HeroItem> = List(3) { i ->
        HeroItem(
            id = "${ID_PREFIX}destaque-${i + 1}",
            title = "Destaque de exemplo ${i + 1}",
            subtitle = "Demonstração · sem conteúdo real",
            imageUrl = null,
        )
    }

    /**
     * Histórico de exemplo gravado no aparelho quando a demonstração é ligada,
     * para dar para testar "Continuar assistindo" e a exclusão antes de existir o player.
     * Os 4 primeiros estão pela metade; os outros, terminados.
     */
    fun historySeed(now: Long): List<HistoryEntry> {
        val minute = 60_000L
        val positions = listOf(31, 15, 43, 6, 50, 50, 50, 50, 50)
        return positions.mapIndexed { i, position ->
            val item = items[i]
            HistoryEntry(
                id = item.id,
                title = item.title,
                imageUrl = null,
                positionMs = position * minute,
                durationMs = 50 * minute,
                updatedAt = now - i * minute,
            )
        }
    }
}
