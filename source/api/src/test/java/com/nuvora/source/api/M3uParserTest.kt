package com.nuvora.source.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class M3uParserTest {
    private fun parse(text: String): Pair<M3uSummary, List<M3uEntry>> {
        val items = mutableListOf<M3uEntry>()
        val summary = parseM3u(StringReader(text)) { items += it }
        return summary to items
    }

    @Test
    fun `le titulo, logo, categoria e link`() {
        val (summary, items) = parse(
            "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"x\" tvg-logo=\"https://img/logo.png\" group-title=\"Filmes\",Meu Filme\n" +
                "https://servidor/filme.mp4\n",
        )
        assertTrue(summary.looksLikeM3u)
        assertEquals(1, summary.entries)
        assertEquals(M3uEntry("Meu Filme", "https://servidor/filme.mp4", "https://img/logo.png", "Filmes"), items[0])
    }

    @Test
    fun `aceita marca BOM, linhas do Windows e linhas em branco`() {
        val (summary, items) = parse("﻿#EXTM3U\r\n\r\n#EXTINF:-1,Canal 1\r\nhttp://a/1.ts\r\n")
        assertTrue(summary.looksLikeM3u)
        assertEquals("Canal 1", items.single().title)
        assertEquals("http://a/1.ts", items.single().url)
    }

    @Test
    fun `usa EXTGRP quando nao ha group-title`() {
        val (_, items) = parse("#EXTM3U\n#EXTINF:-1,Canal\n#EXTGRP:Notícias\nhttp://a/1.m3u8\n")
        assertEquals("Notícias", items.single().group)
    }

    @Test
    fun `sem titulo usa tvg-name, e sem nada da um nome simples`() {
        val (_, items) = parse("#EXTM3U\n#EXTINF:-1 tvg-name=\"Nome TVG\",\nhttp://a/1\n#EXTINF:-1,\nhttp://a/2\n")
        assertEquals("Nome TVG", items[0].title)
        assertEquals("Item 2", items[1].title)
    }

    @Test
    fun `ignora linhas que nao sao links e logo invalido`() {
        val (summary, items) = parse("#EXTM3U\n#EXTINF:-1 tvg-logo=\"logo.png\",A\nnao-e-link\n#EXTINF:-1,B\nhttps://ok/b\n")
        assertEquals(1, summary.entries)
        assertEquals("B", items.single().title)
        assertNull(items.single().logo)
    }

    @Test
    fun `pagina comum nao e lista`() {
        val (summary, _) = parse("<html><body>Página não encontrada</body></html>")
        assertFalse(summary.looksLikeM3u)
        assertEquals(0, summary.entries)
    }

    @Test
    fun `lista grande e lida sem guardar tudo`() {
        val text = buildString {
            append("#EXTM3U\n")
            repeat(20_000) { append("#EXTINF:-1 group-title=\"G${it % 50}\",Item $it\nhttp://s/$it.ts\n") }
        }
        var count = 0
        val summary = parseM3u(StringReader(text)) { count++ }
        assertEquals(20_000, summary.entries)
        assertEquals(20_000, count)
    }
}
