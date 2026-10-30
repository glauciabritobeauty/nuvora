package com.nuvora.core.data.demo

import com.nuvora.core.data.library.HistoryEntry
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.PlaybackRequest

/** Versão final: sem demonstração. */
object DemoContent {
    const val AVAILABLE = false
    const val ID_PREFIX = "demo-"

    val categories: List<String> = emptyList()
    val items: List<CardItem> = emptyList()
    val hero: List<HeroItem> = emptyList()

    @Suppress("UNUSED_PARAMETER")
    fun historySeed(now: Long): List<HistoryEntry> = emptyList()

    @Suppress("UNUSED_PARAMETER")
    fun playbackFor(id: String): PlaybackRequest? = null
}
