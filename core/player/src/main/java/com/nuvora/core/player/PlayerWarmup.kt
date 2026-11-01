package com.nuvora.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.model.SubtitleMode

/**
 * Vídeo preparado antes da hora: enquanto a pessoa lê os detalhes de um filme, o player já conecta
 * e guarda o começo do vídeo (sem tocar). Ao tocar em Assistir, o player da tela usa este, já pronto.
 * Só um de cada vez; sair dos detalhes sem assistir libera tudo (não gasta mais internet).
 */
object PlayerWarmup {
    private var warm: Pair<String, ExoPlayer>? = null

    /** Começa a preparar o vídeo (troca o que estava preparado antes, se era outro). */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun prepare(context: Context, request: PlaybackRequest, startPositionMs: Long, subtitleMode: SubtitleMode) {
        if (warm?.first == request.url) return
        release()
        warm = request.url to createPlayer(context.applicationContext, request, startPositionMs, subtitleMode, autoplay = false)
    }

    /** Entrega o player preparado para este vídeo (ou null, se não houver) e esquece dele. */
    internal fun take(url: String): ExoPlayer? = warm?.takeIf { it.first == url }?.second.also { if (it != null) warm = null }

    /** Desiste do vídeo preparado (a pessoa saiu dos detalhes sem assistir). */
    fun release() {
        warm?.second?.release()
        warm = null
    }
}
