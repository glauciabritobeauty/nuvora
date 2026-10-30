package com.nuvora.core.data.demo

import com.nuvora.core.data.library.HistoryEntry
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.PlaybackRequest

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

    /**
     * Vídeos públicos de teste para desenvolvedores (autorizados pela Glaucia em 31/10/2026).
     * HLS da Apple (10 min, com legenda), DASH do Google/Shaka ("Angel One", vários idiomas de áudio e legenda)
     * e um MP4 de 10 segundos ("Big Buck Bunny", licença aberta).
     */
    private val testStreams = listOf(
        Triple(
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
            "application/x-mpegURL",
            "Vídeo de teste · HLS com legenda",
        ),
        Triple(
            "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd",
            "application/dash+xml",
            "Vídeo de teste · DASH com vários idiomas de áudio e legenda",
        ),
        Triple(
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4",
            "video/mp4",
            "Vídeo de teste · MP4 de 10 segundos",
        ),
    )

    /** Exemplo 1, 4, 7... e os destaques tocam o HLS; Exemplo 2, 5... o DASH; Exemplo 3, 6... o MP4. */
    fun playbackFor(id: String): PlaybackRequest? {
        val title = items.firstOrNull { it.id == id }?.title ?: hero.firstOrNull { it.id == id }?.title ?: return null
        val index = id.removePrefix(ID_PREFIX).toIntOrNull()?.minus(1) ?: 0
        val (url, mime, label) = testStreams[index % testStreams.size]
        return PlaybackRequest(id = id, title = title, subtitle = label, url = url, mimeType = mime, imageUrl = null)
    }
}
