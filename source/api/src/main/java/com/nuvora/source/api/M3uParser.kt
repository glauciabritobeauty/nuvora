package com.nuvora.source.api

import java.io.BufferedReader
import java.io.Reader

/** Um item de uma lista M3U: canal, filme ou episódio. */
data class M3uEntry(
    val title: String,
    val url: String,
    val logo: String?,
    val group: String?,
    /** Nota do filme/série (ex.: "7.5"), quando o provedor manda. */
    val rating: String? = null,
)

/** Resultado da leitura: se o arquivo parece mesmo uma lista e quantos itens válidos tinha. */
data class M3uSummary(val looksLikeM3u: Boolean, val entries: Int)

/**
 * Lê uma lista M3U/M3U8 (o formato mais comum de listas de canais) linha por linha, sem carregar o arquivo
 * inteiro na memória: cada item encontrado é entregue em [onEntry] e pode ser gravado na hora.
 *
 * Entende `#EXTINF` com `tvg-logo`, `group-title` e `tvg-name`, `#EXTGRP`, linhas em branco, comentários,
 * marcas de início de arquivo (BOM) e quebras de linha do Windows.
 */
fun parseM3u(reader: Reader, onEntry: (M3uEntry) -> Unit): M3uSummary {
    val lines = if (reader is BufferedReader) reader else BufferedReader(reader, 64 * 1024)
    var looksLikeM3u = false
    var count = 0
    var title: String? = null
    var logo: String? = null
    var group: String? = null
    var first = true
    while (true) {
        var line = lines.readLine() ?: break
        if (first) {
            line = line.removePrefix("\uFEFF")
            first = false
        }
        line = line.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#EXTM3U", ignoreCase = true) -> looksLikeM3u = true
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                looksLikeM3u = true
                val attributes = line.substringBefore(',', missingDelimiterValue = line)
                title = line.substringAfter(',', missingDelimiterValue = "").trim()
                    .ifEmpty { attribute(attributes, "tvg-name") }
                logo = attribute(attributes, "tvg-logo")
                group = attribute(attributes, "group-title")
            }
            line.startsWith("#EXTGRP:", ignoreCase = true) -> group = group ?: line.substringAfter(':').trim().ifEmpty { null }
            line.startsWith("#") -> Unit
            else -> {
                if (isPlayableUrl(line)) {
                    onEntry(
                        M3uEntry(
                            title = title?.takeIf { it.isNotBlank() } ?: "Item ${count + 1}",
                            url = line,
                            logo = logo?.takeIf { isPlayableUrl(it) },
                            group = group?.takeIf { it.isNotBlank() },
                        ),
                    )
                    count++
                }
                title = null
                logo = null
                group = null
            }
        }
    }
    return M3uSummary(looksLikeM3u, count)
}

private val attributeCache = HashMap<String, Regex>()

private fun attribute(text: String, name: String): String? {
    val regex = attributeCache.getOrPut(name) { Regex("""$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE) }
    return regex.find(text)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
}

private fun isPlayableUrl(text: String): Boolean {
    val lower = text.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://") || lower.startsWith("rtmp://")
}
