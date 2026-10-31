package com.nuvora.feature.sources

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.SeriesEpisodes
import com.nuvora.core.data.sources.Episode
import com.nuvora.core.data.sources.ImportProgress
import com.nuvora.core.data.sources.ProviderLogin
import com.nuvora.core.data.sources.parseProviderLink
import com.nuvora.core.data.sources.ImportResult
import com.nuvora.core.data.sources.SourceInfo
import com.nuvora.core.data.sources.SourceKind
import com.nuvora.designsystem.components.DialogAction
import com.nuvora.designsystem.components.NuvoraButton
import com.nuvora.designsystem.components.NuvoraButtonStyle
import com.nuvora.designsystem.components.NuvoraClickable
import com.nuvora.designsystem.components.NuvoraDialog
import com.nuvora.designsystem.components.NuvoraIcon
import com.nuvora.designsystem.components.NuvoraText
import com.nuvora.designsystem.components.NuvoraTextField
import com.nuvora.designsystem.icons.NuvoraIcons
import com.nuvora.designsystem.theme.NuvoraTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ptBR = Locale("pt", "BR")

internal fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(ptBR).format(count)

internal fun formatMegabytes(bytes: Long): String =
    NumberFormat.getNumberInstance(ptBR).apply { maximumFractionDigits = 1 }.format(bytes / 1_048_576.0) + " MB"

/** Bloco "Fontes de conteúdo" de Ajustes: listas cadastradas, atualizar, remover e adicionar. */
@Composable
fun SourcesSection(
    sources: List<SourceInfo>,
    onAdd: () -> Unit,
    onRefresh: suspend (Long) -> ImportResult,
    onRemove: (Long) -> Unit,
) {
    val colors = NuvoraTheme.colors
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<Long?>(null) }
    var messages by remember { mutableStateOf(mapOf<Long, String>()) }
    var confirmRemove by remember { mutableStateOf<SourceInfo?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBR) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NuvoraText("Fontes de conteúdo", NuvoraTheme.typography.section)
        if (sources.isEmpty()) {
            NuvoraText(
                "Nenhuma lista ainda. Adicione o link ou o arquivo que você recebeu do seu fornecedor.",
                NuvoraTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        sources.forEach { source ->
            Card {
                NuvoraText(source.name, NuvoraTheme.typography.label)
                NuvoraText(
                    "${formatCount(source.itemCount)} itens · ${source.kind.label} · atualizada em ${dateFormat.format(Date(source.updatedAt))}",
                    NuvoraTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                messages[source.id]?.let { NuvoraText(it, NuvoraTheme.typography.caption, color = colors.accent, modifier = Modifier.padding(top = 6.dp)) }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NuvoraButton(
                        text = if (busyId == source.id) "Atualizando..." else "Atualizar",
                        icon = NuvoraIcons.Refresh,
                        style = NuvoraButtonStyle.Secondary,
                        onClick = {
                            if (busyId != null) return@NuvoraButton
                            busyId = source.id
                            scope.launch {
                                val message = when (val result = onRefresh(source.id)) {
                                    is ImportResult.Success -> "Lista atualizada: ${formatCount(result.itemCount)} itens."
                                    is ImportResult.Failure -> result.reason.message + " A versão anterior continua valendo." +
                                        (result.detail?.let { " (detalhe técnico: $it)" } ?: "")
                                }
                                messages = messages + (source.id to message)
                                busyId = null
                            }
                        },
                    )
                    NuvoraButton(text = "Remover", style = NuvoraButtonStyle.Secondary, onClick = { confirmRemove = source })
                }
            }
        }
        NuvoraButton(text = "Adicionar lista", icon = NuvoraIcons.Plus, onClick = onAdd)
    }

    confirmRemove?.let { source ->
        NuvoraDialog(
            title = "Remover \"${source.name}\"?",
            message = "A lista sai deste aparelho. Dá para adicionar de novo depois.",
            onDismiss = { confirmRemove = null },
            actions = listOf(
                DialogAction("Remover", { onRemove(source.id) }, NuvoraButtonStyle.Primary),
                DialogAction("Cancelar", {}),
            ),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = NuvoraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(16.dp),
    ) { content() }
}

private sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Working : SaveStatus
    data class Done(val count: Int) : SaveStatus
    data class Failed(val message: String, val detail: String?) : SaveStatus
}

/**
 * Tela "Adicionar lista": link ou arquivo, nome, confirmação de direito de acesso e "Testar e salvar".
 * Na TV só aparece o link (TVs normalmente não têm onde escolher arquivos).
 */
@Composable
fun AddSourceScreen(
    onSaveLink: suspend (name: String, url: String, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onSaveFile: suspend (name: String, uri: Uri, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onSaveProvider: suspend (name: String, login: ProviderLogin, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = NuvoraTheme.colors
    val dimens = NuvoraTheme.dimens
    val isTv = NuvoraTheme.isTv
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf<SourceKind?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var fileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<SaveStatus>(SaveStatus.Idle) }
    var formError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    val firstField = remember { FocusRequester() }
    val firstChoice = remember { FocusRequester() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileUri = uri.toString()
            fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Arquivo escolhido"
            if (name.isBlank()) name = fileName.orEmpty().substringBeforeLast('.')
        }
    }

    BackHandler {
        if (kind != null && status !is SaveStatus.Done) kind = null else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 32.dp else dimens.screenPaddingH, vertical = dimens.screenPaddingV),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NuvoraButton(text = "Voltar", icon = NuvoraIcons.ChevronLeft, style = NuvoraButtonStyle.Secondary, onClick = onBack)
            NuvoraText("Adicionar lista", NuvoraTheme.typography.title)
        }
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val current = status) {
                is SaveStatus.Done -> {
                    NuvoraText("Pronto! ${formatCount(current.count)} itens encontrados.", NuvoraTheme.typography.title, color = colors.accent)
                    NuvoraText("A sua lista já aparece no Início.", NuvoraTheme.typography.body, color = colors.textSecondary)
                    NuvoraButton(text = "Ir para o Início", icon = NuvoraIcons.Home, onClick = onFinished)
                    return@Column
                }
                else -> Unit
            }
            if (kind == null) {
                NuvoraText("Como você recebeu a sua lista?", NuvoraTheme.typography.section)
                ChoiceButton(
                    NuvoraIcons.User,
                    "Provedor, usuário e senha",
                    "Os dados de acesso que o fornecedor mandou",
                    Modifier.focusRequester(firstChoice),
                ) { kind = SourceKind.Provider }
                // Na TV o foco do controle já começa na primeira opção.
                LaunchedEffect(Unit) { if (isTv) runCatching { firstChoice.requestFocus() } }
                ChoiceButton(NuvoraIcons.Link, "Link da lista", "Um endereço que começa com http") { kind = SourceKind.Link }
                // TVs normalmente não têm onde escolher arquivos.
                if (!isTv) {
                    ChoiceButton(NuvoraIcons.File, "Arquivo do aparelho", "Um arquivo .m3u salvo no celular") { kind = SourceKind.File }
                }
                return@Column
            }
            NuvoraTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nome da lista",
                placeholder = "Minha lista",
                tvFocusRequester = firstField,
            )
            // Na TV o foco do controle já começa no primeiro campo.
            LaunchedEffect(kind) { if (isTv) runCatching { firstField.requestFocus() } }
            if (kind == SourceKind.Provider) {
                NuvoraTextField(
                    value = server,
                    onValueChange = { server = it; formError = null },
                    label = "Provedor",
                    placeholder = "http://exemplo.com:8080",
                    keyboardType = KeyboardType.Uri,
                )
                NuvoraTextField(value = user, onValueChange = { user = it; formError = null }, label = "Usuário", placeholder = "Usuário")
                NuvoraTextField(
                    value = password,
                    onValueChange = { password = it; formError = null },
                    label = "Senha",
                    placeholder = "Senha",
                    secret = true,
                )
                NuvoraText(
                    "Dica: se você recebeu um link com usuário e senha dentro, pode colar ele inteiro no campo Provedor.",
                    NuvoraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            } else if (kind == SourceKind.Link) {
                NuvoraTextField(
                    value = link,
                    onValueChange = { link = it; formError = null },
                    label = "Link da lista",
                    placeholder = "http://...",
                    keyboardType = KeyboardType.Uri,
                )
            } else {
                NuvoraButton(
                    text = fileName ?: "Escolher arquivo",
                    icon = NuvoraIcons.File,
                    style = NuvoraButtonStyle.Secondary,
                    onClick = { pickFile.launch(arrayOf("*/*")) },
                )
            }
            RightsCheck(agreed) { agreed = !agreed; formError = null }
            formError?.let { NuvoraText(it, NuvoraTheme.typography.body, color = colors.error) }
            (status as? SaveStatus.Failed)?.let {
                NuvoraText(it.message, NuvoraTheme.typography.body, color = colors.error)
                it.detail?.let { detail ->
                    NuvoraText("Detalhe técnico: $detail", NuvoraTheme.typography.caption, color = colors.textSecondary)
                }
            }
            if (status == SaveStatus.Working) {
                NuvoraText(
                    when (val p = progress) {
                        null -> "Conectando ao provedor... listas grandes podem levar alguns minutos."
                        is ImportProgress.Downloading -> "Baixando a lista... ${formatMegabytes(p.bytes)}"
                        is ImportProgress.Reading -> "Organizando a lista... já li ${formatCount(p.items)} itens."
                        is ImportProgress.Step -> p.text
                    },
                    NuvoraTheme.typography.body,
                    color = colors.textSecondary,
                )
            } else {
                NuvoraButton(
                    text = "Testar e salvar",
                    icon = NuvoraIcons.Check,
                    onClick = {
                        // Colou o link completo no campo Provedor? Separa Provedor, Usuário e Senha sozinho.
                        if (kind == SourceKind.Provider) {
                            parseProviderLink(server)?.let { parsed ->
                                server = parsed.server
                                user = parsed.user
                                password = parsed.password
                            }
                        }
                        formError = when {
                            kind == SourceKind.Link && !link.trim().startsWith("http", ignoreCase = true) -> "Cole o link da lista (começa com http)."
                            kind == SourceKind.Provider && server.isBlank() -> "Preencha o Provedor (o endereço que o fornecedor mandou)."
                            kind == SourceKind.Provider && (user.isBlank() || password.isBlank()) -> "Preencha o usuário e a senha."
                            kind == SourceKind.File && fileUri == null -> "Escolha o arquivo da lista."
                            !agreed -> "Marque a confirmação de direito de acesso."
                            else -> null
                        }
                        if (formError != null) return@NuvoraButton
                        status = SaveStatus.Working
                        progress = null
                        scope.launch {
                            val onProgress: (ImportProgress) -> Unit = { progress = it }
                            val result = when (kind) {
                                SourceKind.Link -> onSaveLink(name, link, onProgress)
                                SourceKind.Provider -> onSaveProvider(name, ProviderLogin(server, user.trim(), password), onProgress)
                                else -> onSaveFile(name, Uri.parse(fileUri), onProgress)
                            }
                            status = when (result) {
                                is ImportResult.Success -> SaveStatus.Done(result.itemCount)
                                is ImportResult.Failure -> SaveStatus.Failed(result.reason.message, result.detail)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = NuvoraTheme.colors
    NuvoraClickable(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
        focusedContainerColor = colors.surfaceRaised,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NuvoraIcon(icon, tint = colors.accent)
            Column {
                NuvoraText(title, NuvoraTheme.typography.label)
                NuvoraText(hint, NuvoraTheme.typography.caption, color = colors.textSecondary)
            }
        }
    }
}

/** Caixa de confirmação: "Tenho direito de acesso a este conteúdo." */
@Composable
private fun RightsCheck(checked: Boolean, onToggle: () -> Unit) {
    val colors = NuvoraTheme.colors
    val label = "Tenho direito de acesso a este conteúdo."
    NuvoraClickable(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = if (checked) "marcado" else "não marcado"
            },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(if (checked) colors.primary else colors.surface, RoundedCornerShape(8.dp))
                    .border(2.dp, if (checked) colors.primary else colors.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) NuvoraIcon(NuvoraIcons.Check, tint = colors.onPrimary, size = 18.dp)
            }
            Column {
                NuvoraText(label, NuvoraTheme.typography.label)
                NuvoraText(
                    "O NUVORA é só um reprodutor: não vem com nenhum conteúdo. Use apenas listas que você tem direito de assistir.",
                    NuvoraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Janela para escolher o episódio de uma série. */
@Composable
fun EpisodesDialog(series: SeriesEpisodes, onPick: (Episode) -> Unit, onDismiss: () -> Unit) {
    NuvoraDialog(
        title = series.title,
        message = "Escolha o episódio",
        onDismiss = onDismiss,
        actions = series.episodes.map { episode -> DialogAction(episode.label, { onPick(episode) }) } + DialogAction("Fechar", {}),
    )
}
