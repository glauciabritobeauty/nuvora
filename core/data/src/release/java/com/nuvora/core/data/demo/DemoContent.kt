package com.nuvora.core.data.demo

import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HomeContent

/** Versão final: sem demonstração. */
object DemoContent {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun home(favorites: Set<String>): HomeContent? = null

    @Suppress("UNUSED_PARAMETER")
    fun search(query: String): List<CardItem> = emptyList()
}
