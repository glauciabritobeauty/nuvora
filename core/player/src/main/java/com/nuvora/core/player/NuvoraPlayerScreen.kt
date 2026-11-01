package com.nuvora.core.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.model.SubtitleMode
import com.nuvora.designsystem.components.DialogAction
import com.nuvora.designsystem.components.MessageState
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraDialog
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val SAVE_EVERY_MS = 10_000L

/**
 * Tela do player. Existe só enquanto a pessoa está assistindo: ao sair, o player é liberado
 * (vídeo, áudio e memória) e a posição é salva para "Continuar assistindo".
 *
 * Celular: tela cheia deitada; tocar na tela mostra/esconde os controles.
 * TV: OK mostra os controles; setas ← → voltam/avançam 10 s; Voltar esconde os controles e depois sai.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NuvoraPlayerScreen(
    request: PlaybackRequest,
    startPositionMs: Long,
    subtitleMode: SubtitleMode,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    /** Avisado quando o 1º quadro do vídeo aparece (tempo desde que o player abriu, em ms). */
    onFirstFrame: (millis: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val isTv = NuvoraTheme.isTv
    val saveProgress by rememberUpdatedState(onSaveProgress)
    val firstFrame by rememberUpdatedState(onFirstFrame)

    val openedAt = remember(request.id) { SystemClock.elapsedRealtime() }
    val player = remember(request.id) { createPlayer(context, request, startPositionMs, subtitleMode) }
    val diag = remember { PlayerDiagnostics(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(startPositionMs) }
    val failure = remember { mutableStateOf<PlaybackFailure?>(null) }
    var cues by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var picker by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (player.duration > 0) saveProgress(player.currentPosition, player.duration)
    }

    fun retry() {
        diag.log("retomou")
        failure.value = null
        player.prepare()
        player.play()
    }

    // Ouve o player e, ao sair da tela, salva a posição e libera tudo.
    DisposableEffect(player) {
        var checkedStart = false
        var triedHttp = false
        var subtitlesDecided = false
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                isPlaying = p.isPlaying
                buffering = p.playbackState == Player.STATE_BUFFERING
                ended = p.playbackState == Player.STATE_ENDED
                if (p.duration > 0) duration = p.duration
                position = p.currentPosition
                // Se a posição salva já passou do fim deste vídeo, começa do início.
                if (!checkedStart && p.playbackState == Player.STATE_READY) {
                    checkedStart = true
                    if (p.duration > 0 && p.currentPosition >= p.duration * 95 / 100) p.seekTo(0)
                }
                if (ended && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    controlsVisible = true
                    save()
                    diag.log("fim")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Certificado inválido no https: tenta o mesmo vídeo pelo http, do mesmo ponto, uma vez.
                val url = request.url
                if (!triedHttp && error.hasSslCause() && url.startsWith("https://", ignoreCase = true)) {
                    triedHttp = true
                    diag.log("certificado inválido no vídeo; tentando http")
                    player.setMediaItem(
                        androidx.media3.common.MediaItem.Builder().setUri("http://" + url.substring("https://".length))
                            .apply { request.mimeType?.let(::setMimeType) }.build(),
                        player.currentPosition,
                    )
                    player.prepare()
                    player.play()
                    return
                }
                failure.value = error.toFailure()
                diag.log("falha=${failure.value}")
                controlsVisible = false
            }

            override fun onRenderedFirstFrame() {
                diag.log("medida primeiro quadro ${SystemClock.elapsedRealtime() - openedAt} ms")
                firstFrame(SystemClock.elapsedRealtime() - openedAt)
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
                if (!subtitlesDecided && newTracks.audioReady()) {
                    subtitlesDecided = true
                    diag.log("legenda inicial=${applyInitialSubtitles(player, newTracks, subtitleMode)} (${subtitleMode.label})")
                }
            }
        }
        player.addListener(listener)
        diag.log("aberto id=${request.id}")
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
            diag.log("fechado (player liberado)")
        }
    }

    // Saiu do app (tela apagou, outro app): pausa e salva. Não toca escondido.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                save()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Internet voltou depois de uma queda: tenta de novo sozinho, do mesmo ponto.
    DisposableEffect(player) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post { if (failure.value == PlaybackFailure.Network) retry() }
            }
        }
        val networkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { connectivity?.registerNetworkCallback(networkRequest, callback) }
        onDispose { runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }

    // Tela cheia, tela sempre acesa e, no celular, deitada.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        val previousOrientation = activity?.requestedOrientation
        if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    // Só na versão de teste: anota a posição a cada 3 s, para o teste automático conferir que o vídeo anda.
    LaunchedEffect(player, isPlaying) {
        while (isPlaying && diag.enabled) {
            diag.log("posicao=${player.currentPosition / 1000}")
            delay(3_000)
        }
    }

    // Guarda a posição de tempos em tempos (se o app for fechado à força, perde no máximo 10 s).
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            delay(SAVE_EVERY_MS)
            save()
        }
    }

    // Atualiza o relógio só enquanto os controles estão na tela (economia).
    LaunchedEffect(player, controlsVisible) {
        while (controlsVisible) {
            position = player.currentPosition
            delay(500)
        }
    }

    // Esconde os controles depois de alguns segundos sem mexer, se o vídeo estiver tocando.
    LaunchedEffect(controlsVisible, lastInteraction, isPlaying, picker) {
        if (controlsVisible && isPlaying && picker == null) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, failure.value) {
        delay(50) // deixa os controles entrarem na tela antes de pedir o foco
        runCatching { if (controlsVisible && failure.value == null) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    fun interact() {
        lastInteraction = System.nanoTime()
        controlsVisible = true
    }

    fun togglePlay() {
        when {
            ended -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> {
                player.pause()
                diag.log("pausou")
            }
            else -> {
                player.play()
                diag.log("tocou")
            }
        }
        interact()
    }

    BackHandler {
        diag.log("voltar (controles=$controlsVisible)")
        if (isTv && controlsVisible && failure.value == null && !ended) controlsVisible = false else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    lastInteraction = System.nanoTime()
                    diag.log("tecla ${it.key} (controles=$controlsVisible)")
                }
                false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Controles na tela, mas o foco ficou no fundo: leva o foco para os controles (nunca "tecla morta").
                if (controlsVisible) {
                    if (!rootFocused || event.key == Key.Back || event.key == Key.Escape) return@onKeyEvent false
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) togglePlay() else runCatching { playFocus.requestFocus() }
                    return@onKeyEvent true
                }
                when (event.key) {
                    // Voltar segue para o BackHandler (sair do vídeo); não pode virar "mostrar controles".
                    Key.Back, Key.Escape -> false
                    Key.DirectionLeft -> { player.seekBack(); diag.log("voltou 10s"); interact(); true }
                    Key.DirectionRight -> { player.seekForward(); diag.log("avancou 10s"); interact(); true }
                    Key.MediaPlayPause -> { togglePlay(); true }
                    else -> { interact(); true }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (controlsVisible) controlsVisible = false else interact() })
            },
    ) {
        val presentation = rememberPresentationState(player)
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .align(Alignment.Center)
                .resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )

        if (cues.isNotBlank()) {
            NuvoraText(
                text = cues,
                style = NuvoraTheme.typography.body,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 110.dp else 40.dp, start = 48.dp, end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        if (buffering && failure.value == null) {
            LoadingSpinner(Modifier.align(Alignment.Center))
        }

        failure.value?.let { kind ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val (title, message) = when (kind) {
                        PlaybackFailure.Network ->
                            "A conexão caiu" to "Assim que a internet voltar, o vídeo continua de onde parou."
                        PlaybackFailure.NotAvailable ->
                            "Vídeo indisponível" to "A fonte não entregou este vídeo agora. Tente de novo mais tarde."
                        PlaybackFailure.Unsupported ->
                            "Este aparelho não toca este vídeo" to "O formato não é compatível com este aparelho."
                        PlaybackFailure.Other ->
                            "Não foi possível tocar" to "Algo deu errado. Tente de novo."
                    }
                    MessageState(
                        icon = if (kind == PlaybackFailure.Network) NuvoraIcons.NoConnection else NuvoraIcons.Refresh,
                        title = title,
                        message = message,
                        actionLabel = if (kind == PlaybackFailure.Unsupported) null else "Tentar de novo",
                        actionIcon = NuvoraIcons.Refresh,
                        onAction = ::retry,
                    )
                    NuvoraButton(text = "Sair do vídeo", style = NuvoraButtonStyle.Secondary, onClick = onClose)
                }
            }
        }

        if (controlsVisible && failure.value == null) {
            Controls(
                request = request,
                isPlaying = isPlaying && !ended,
                position = position,
                duration = duration,
                playFocus = playFocus,
                hasAudioChoice = audioOptions(tracks).size > 1,
                onClose = onClose,
                onTogglePlay = ::togglePlay,
                onSeekBack = { player.seekBack(); interact() },
                onSeekForward = { player.seekForward(); interact() },
                onSeekTo = { player.seekTo(it); position = it; interact() },
                onInteract = ::interact,
                onAudio = {
                    diag.log("opcoes audio: " + audioOptions(tracks).joinToString(" | ") { it.label })
                    picker = "audio"
                },
                onSubtitles = {
                    picker = if (tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }) "legendas" else "sem-legenda"
                },
                onVolume = { up ->
                    val audio = context.getSystemService(AudioManager::class.java)
                    audio?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    interact()
                },
            )
        }
    }

    when (picker) {
        "audio" -> TrackPicker("Áudio", audioOptions(tracks), player, { diag.log("audio=$it") }) { picker = null }
        "legendas" -> TrackPicker("Legendas", subtitleOptions(tracks, player), player, { diag.log("legenda=$it") }) { picker = null }
        "sem-legenda" -> NuvoraDialog(
            title = "Legendas",
            message = "Este vídeo não tem legenda disponível na fonte.",
            onDismiss = { picker = null },
            actions = listOf(DialogAction("Fechar", {})),
        )
    }
}

@Composable
private fun TrackPicker(
    title: String,
    options: List<TrackOption>,
    player: Player,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NuvoraDialog(
        title = title,
        onDismiss = onDismiss,
        actions = options.map { option ->
            DialogAction(
                label = if (option.selected) "${option.label} (atual)" else option.label,
                onClick = {
                    option.apply(player)
                    onPicked(option.label)
                },
                style = if (option.selected) NuvoraButtonStyle.Primary else NuvoraButtonStyle.Secondary,
            )
        } + DialogAction("Fechar", {}),
    )
}

@Composable
private fun Controls(
    request: PlaybackRequest,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playFocus: FocusRequester,
    hasAudioChoice: Boolean,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onVolume: (up: Boolean) -> Unit,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val edge = if (isTv) 48.dp else 20.dp
    Box(Modifier.fillMaxSize()) {
        // Faixas escuras em cima e embaixo só para dar leitura aos controles (degradê simples, sem desfoque).
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = edge, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerButton(NuvoraIcons.ChevronLeft, "Sair do vídeo", onClose, text = "Sair")
            Column {
                NuvoraText(request.title, NuvoraTheme.typography.section, maxLines = 1)
                request.subtitle?.let { NuvoraText(it, NuvoraTheme.typography.caption, color = colors.textSecondary, maxLines = 1) }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            PlayerButton(NuvoraIcons.Rewind, "Voltar 10 segundos", onSeekBack, text = "10 s")
            NuvoraButton(
                text = null,
                icon = if (isPlaying) NuvoraIcons.Pause else NuvoraIcons.Play,
                contentDescription = if (isPlaying) "Pausar" else "Tocar",
                onClick = onTogglePlay,
                modifier = Modifier.focusRequester(playFocus).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
            )
            PlayerButton(NuvoraIcons.Forward, "Avançar 10 segundos", onSeekForward, text = "10 s")
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = edge, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SeekBar(position = position, duration = duration, onSeekTo = onSeekTo, onInteract = onInteract)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NuvoraText(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
                    style = NuvoraTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.semantics { contentDescription = "Tempo atual ${formatTime(position)}" },
                )
                Spacer(Modifier.weight(1f))
                if (hasAudioChoice) {
                    PlayerButton(NuvoraIcons.Audio, "Áudio", onAudio, text = "Áudio")
                }
                // Sempre visível: quem precisa de legenda sabe na hora se o vídeo tem ou não.
                PlayerButton(NuvoraIcons.Subtitles, "Legendas", onSubtitles, text = "Legendas")
                PlayerButton(NuvoraIcons.VolumeDown, "Diminuir volume", { onVolume(false) }, text = "Volume")
                PlayerButton(NuvoraIcons.VolumeUp, "Aumentar volume", { onVolume(true) }, text = "Volume")
            }
        }
    }
}

/**
 * Botão do player com fundo escuro translúcido: fica legível sobre qualquer imagem do vídeo.
 * Ícone com palavra ([text]) para ninguém precisar adivinhar; [label] é a frase completa para o leitor de tela.
 */
@Composable
private fun PlayerButton(icon: ImageVector, label: String, onClick: () -> Unit, text: String? = null) {
    val colors = NuvoraTheme.colors
    NuvoraClickable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        containerColor = Color.Black.copy(alpha = 0.5f),
        focusedContainerColor = colors.surfaceRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (text != null) 16.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NuvoraIcon(icon, tint = Color.White, size = 20.dp)
            if (text != null) NuvoraText(text, NuvoraTheme.typography.label, color = Color.White, maxLines = 1)
        }
    }
}

/**
 * Barra de progresso. Celular: tocar ou arrastar para ir a um ponto.
 * TV: com a barra em foco, ← e → voltam/avançam 10 s.
 */
@Composable
private fun SeekBar(position: Long, duration: Long, onSeekTo: (Long) -> Unit, onInteract: () -> Unit) {
    val colors = NuvoraTheme.colors
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { contentDescription = "Barra de progresso" }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || duration <= 0) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekTo((position - 10_000).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { onSeekTo((position + 10_000).coerceAtMost(duration)); true }
                    else -> false
                }
            }
            .pointerInput(duration) {
                detectTapGestures { offset -> if (duration > 0) onSeekTo((offset.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures { change, _ ->
                    onInteract()
                    if (duration > 0) onSeekTo((change.position.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (focused) 6.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(trackHeight).background(Color.White.copy(alpha = 0.25f), CircleShape))
        Box(Modifier.fillMaxWidth(fraction).height(trackHeight).background(colors.accent, CircleShape))
        val thumb = if (focused) 18.dp else 14.dp
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * fraction)
                .size(thumb)
                .background(if (focused) colors.textPrimary else colors.accent, CircleShape),
        )
    }
}

/** Indicador de carregamento: um arco girando, desenhado só enquanto o vídeo carrega. */
@Composable
private fun LoadingSpinner(modifier: Modifier = Modifier) {
    val accent = NuvoraTheme.colors.accent
    val rotation = rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rotation",
    )
    Canvas(
        modifier
            .size(56.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .semantics { contentDescription = "Carregando o vídeo" },
    ) {
        drawArc(accent, startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
