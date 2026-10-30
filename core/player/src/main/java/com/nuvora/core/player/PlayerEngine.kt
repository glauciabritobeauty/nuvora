package com.nuvora.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.nuvora.core.data.model.PlaybackRequest
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L

/**
 * Cria o player já pensando em aparelho modesto:
 * guarda no máximo 30 s de vídeo à frente (menos memória), pausa sozinho se o fone for desconectado
 * e respeita outros apps de áudio.
 */
@UnstableApi
internal fun createPlayer(context: Context, request: PlaybackRequest, startPositionMs: Long): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
        .build()
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage("pt")
                .setPreferredTextLanguage("pt")
                .build()
            val item = MediaItem.Builder().setUri(request.url).apply { request.mimeType?.let(::setMimeType) }.build()
            setMediaItem(item, startPositionMs)
            prepare()
            playWhenReady = true
        }
}

/** Tipos de falha, cada um com uma mensagem amigável na tela. */
internal enum class PlaybackFailure { Network, NotAvailable, Unsupported, Other }

internal fun PlaybackException.toFailure(): PlaybackFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> PlaybackFailure.Network
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    -> PlaybackFailure.NotAvailable
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
    -> PlaybackFailure.Unsupported
    else -> PlaybackFailure.Other
}

/** Uma opção de áudio ou legenda para a pessoa escolher. */
internal data class TrackOption(val label: String, val selected: Boolean, val apply: (Player) -> Unit)

/**
 * O mesmo idioma pode vir em várias qualidades (ex.: "Espanhol · estéreo" duas vezes).
 * Mostra cada um uma vez só; fica marcado como atual se qualquer uma das versões estiver tocando.
 */
private fun List<TrackOption>.withoutRepeats(): List<TrackOption> =
    groupBy { it.label }.values.map { same -> same.first().copy(selected = same.any { it.selected }) }

private val ptBR = Locale("pt", "BR")

private fun languageName(code: String?): String? =
    code?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(ptBR) }
        ?.replaceFirstChar { it.titlecase(ptBR) }

@UnstableApi
internal fun audioOptions(tracks: Tracks): List<TrackOption> = buildList<TrackOption> {
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val channels = when (format.channelCount) {
                1 -> "mono"
                2 -> "estéreo"
                6 -> "5.1"
                8 -> "7.1"
                Format.NO_VALUE, 0 -> null
                else -> "${format.channelCount} canais"
            }
            val name = format.label ?: languageName(format.language) ?: "Áudio ${size + 1}"
            add(
                TrackOption(
                    label = listOfNotNull(name, channels).joinToString(" · "),
                    selected = group.isTrackSelected(i),
                    apply = { player ->
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                    },
                ),
            )
        }
    }
}.withoutRepeats()

@UnstableApi
internal fun subtitleOptions(tracks: Tracks, player: Player): List<TrackOption> {
    val disabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = textGroups.any { it.isSelected }
    return buildList<TrackOption> {
        add(
            TrackOption("Desligadas", selected = disabled || !anySelected) { p ->
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            },
        )
        textGroups.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                add(
                    TrackOption(
                        label = format.label ?: languageName(format.language) ?: "Legenda ${size}",
                        selected = !disabled && group.isTrackSelected(i),
                        apply = { p ->
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                        },
                    ),
                )
            }
        }
    }.withoutRepeats()
}

/** 75000 → "1:15"; 3725000 → "1:02:05". */
internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
