package com.nuvora.feature.mobile.demo

import com.nuvora.feature.mobile.home.CardItem
import com.nuvora.feature.mobile.home.HomeContent

/** Versão final: sem demonstração. */
internal object DemoContent {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun home(favorites: Set<String>): HomeContent? = null

    @Suppress("UNUSED_PARAMETER")
    fun search(query: String): List<CardItem> = emptyList()
}
