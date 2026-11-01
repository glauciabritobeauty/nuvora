package com.nuvora.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.nuvora.core.data.sources.NUVORA_USER_AGENT
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.model.SubtitleMode
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L

/**
 * Cria o player já pensando em aparelho modesto:
 * guarda no máximo 30 s de vídeo à frente (menos memória), pausa sozinho se o fone for desconectado
 * e respeita outros apps de áudio.
 */
@UnstableApi
internal fun createPlayer(
    context: Context,
    request: PlaybackRequest,
    startPositionMs: Long,
    subtitleMode: SubtitleMode,
): ExoPlayer {
    // Começa a tocar com 1 s de vídeo guardado (antes 1,5 s); depois de uma travada, 2,5 s para não travar de novo.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_000, 2_500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    // Provedores costumam redirecionar de http para https e recusar apps desconhecidos.
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(NUVORA_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)
        .setKeepPostFor302Redirects(true)
    // Canais .ts: aceita começar num quadro que não é o "inicial" (muitos provedores demoram a mandar um),
    // em vez de esperar segundos pelo próximo.
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        .setConstantBitrateSeekingEnabled(true)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors))
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
            // Português do Brasil primeiro; se não houver, qualquer português.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("pt-BR", "pt")
                .setPreferredTextLanguages("pt-BR", "pt")
                .apply {
                    when (subtitleMode) {
                        SubtitleMode.Off -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        SubtitleMode.Always -> setPreferredTextRoleFlags(C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or C.ROLE_FLAG_CAPTION)
                            .setSelectUndeterminedTextLanguage(true)
                        SubtitleMode.Auto -> Unit
                    }
                }
                .build()
            val item = MediaItem.Builder().setUri(request.url).apply { (request.mimeType ?: guessMimeType(request.url))?.let(::setMimeType) }.build()
            setMediaItem(item, startPositionMs)
            prepare()
            playWhenReady = true
        }
}

/**
 * Tipo do vídeo pelo endereço: o player já sabe como abrir, sem "farejar" o começo do arquivo.
 * Sem certeza, deixa em branco (o player descobre sozinho, como antes).
 */
internal fun guessMimeType(url: String): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
        path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
        path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
        path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
        else -> null
    }
}

/** O erro veio de um certificado de segurança (https) inválido? */
internal fun Throwable.hasSslCause(): Boolean =
    generateSequence(this) { it.cause }.take(10).any { it is javax.net.ssl.SSLException }

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

private fun Tracks.selectedFormat(type: Int): Format? =
    groups.firstOrNull { it.type == type && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }

private fun disableText(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

/** Quando os idiomas do vídeo ficam conhecidos: o áudio já foi escolhido? */
internal fun Tracks.audioReady(): Boolean = groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }

/**
 * Decide a legenda do começo do vídeo pela preferência da pessoa (uma vez por vídeo).
 * Automático: some se o áudio já está em português. Sempre: liga alguma, se o vídeo tiver.
 * Devolve uma descrição curta do resultado (usada nas anotações da versão de teste).
 */
@UnstableApi
internal fun applyInitialSubtitles(player: Player, tracks: Tracks, mode: SubtitleMode): String {
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    fun current() = tracks.selectedFormat(C.TRACK_TYPE_TEXT)?.let { it.label ?: languageName(it.language) } ?: "nenhuma"
    return when (mode) {
        SubtitleMode.Off -> "desligada"
        SubtitleMode.Auto -> {
            val audioLanguage = tracks.selectedFormat(C.TRACK_TYPE_AUDIO)?.language.orEmpty()
            val textLanguage = tracks.selectedFormat(C.TRACK_TYPE_TEXT)?.language.orEmpty()
            when {
                audioLanguage.startsWith("pt") -> {
                    disableText(player)
                    "desligada (áudio já em português)"
                }
                // Legenda que o vídeo liga sozinho em outro idioma (ex.: inglês) não ajuda: só português no automático.
                !textLanguage.startsWith("pt") -> {
                    disableText(player)
                    "nenhuma"
                }
                else -> current()
            }
        }
        SubtitleMode.Always -> {
            val group = textGroups.firstOrNull { g -> (0 until g.length).any(g::isTrackSupported) }
            if (textGroups.none { it.isSelected } && group != null) {
                val index = (0 until group.length).first(group::isTrackSupported)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
                group.getTrackFormat(index).let { it.label ?: languageName(it.language) } ?: "ligada"
            } else {
                current()
            }
        }
    }
}
