package com.nuvora.core.data.model

import androidx.compose.runtime.Immutable

/**
 * O que o player precisa para tocar um item. O endereço vem sempre da fonte (ou dos vídeos de teste);
 * o player não sabe de onde veio.
 */
@Immutable
data class PlaybackRequest(
    val id: String,
    val title: String,
    val subtitle: String?,
    val url: String,
    /** "application/x-mpegURL" (HLS), "application/dash+xml" (DASH), "video/mp4"... ou null para descobrir sozinho. */
    val mimeType: String?,
    val imageUrl: String?,
)
