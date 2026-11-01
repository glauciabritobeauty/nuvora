package com.nuvora.feature.sources

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nuvora.core.data.SeriesEpisodes
import com.nuvora.core.data.sources.ConnectionReport
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
import com.nuvora.designsystem.components.NuvoraLogo
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
    /** Último relatório de conexão (texto, sem senha), para ver e copiar a qualquer hora. */
    lastReport: String? = null,
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
        lastReport?.let { SavedReport(it) }
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
    data class Done(val count: Int, val report: ConnectionReport?) : SaveStatus
    data class Failed(val message: String, val detail: String?, val report: ConnectionReport?) : SaveStatus
}

/**
 * "Ver relatório de conexão": cada passo do login, o que o provedor respondeu e a conclusão,
 * com botão para copiar e mandar ao suporte. Usuário e senha nunca aparecem (vêm como •••).
 */
@Composable
private fun ReportPanel(report: ConnectionReport, buttonFocus: FocusRequester? = null) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    NuvoraButton(
        text = if (open) "Esconder relatório de conexão" else "Ver relatório de conexão",
        icon = NuvoraIcons.File,
        style = NuvoraButtonStyle.Secondary,
        onClick = { open = !open },
        modifier = if (buttonFocus != null) Modifier.focusRequester(buttonFocus) else Modifier,
    )
    if (!open) return
    Card {
        NuvoraText("Relatório de conexão", NuvoraTheme.typography.label)
        NuvoraText(
            "Usuário e senha não aparecem aqui: estão trocados por •••.",
            NuvoraTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        report.steps.forEachIndexed { index, step ->
            // Na TV cada passo recebe o foco do controle, para dar para rolar o relatório inteiro com as setas.
            StepFocus(isTv) {
                Column(Modifier.padding(if (isTv) 6.dp else 0.dp)) {
                    NuvoraText(
                        "${index + 1}. ${step.title}: ${if (step.ok) "ok" else "falhou"}",
                        NuvoraTheme.typography.caption,
                        color = if (step.ok) colors.accent else colors.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    step.request?.let { NuvoraText("Pedido: $it", NuvoraTheme.typography.caption, color = colors.textSecondary) }
                    NuvoraText("Resposta: ${step.result}", NuvoraTheme.typography.caption, color = colors.textSecondary)
                }
            }
        }
        StepFocus(isTv) {
            Column(Modifier.padding(if (isTv) 6.dp else 0.dp)) {
                report.problem?.let {
                    NuvoraText("Tipo do problema: ${it.label}", NuvoraTheme.typography.caption, color = colors.textPrimary, modifier = Modifier.padding(top = 10.dp))
                }
                NuvoraText("Conclusão: ${report.conclusion}", NuvoraTheme.typography.caption, color = colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Box(Modifier.padding(top = 12.dp)) {
            NuvoraButton(
                text = if (copied) "Relatório copiado" else "Copiar relatório",
                icon = if (copied) NuvoraIcons.Check else NuvoraIcons.Link,
                style = NuvoraButtonStyle.Secondary,
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Relatório de conexão NUVORA", report.asText()))
                    copied = true
                },
            )
        }
        if (copied) {
            NuvoraText(
                "Agora é só colar na conversa com o suporte (por exemplo, no WhatsApp).",
                NuvoraTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Jeitos de entrar: o principal (usuário e senha) e os alternativos, discretos. */
private enum class EntryMode { Login, Link, File }

/**
 * Tela de entrada (boas-vindas) e "Adicionar lista" de Ajustes.
 * O principal vem direto: usuário, senha e ENTRAR. O servidor (DNS) é uma opção que abre quando precisa
 * (e fica salvo depois do 1º login que dá certo). Link e arquivo M3U ficam numa linha discreta embaixo.
 * Em telas deitadas (TV, celular deitado, tablet) a marca fica à esquerda e o formulário à direita.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddSourceScreen(
    onSaveLink: suspend (name: String, url: String, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onSaveFile: suspend (name: String, uri: Uri, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onSaveProvider: suspend (name: String, login: ProviderLogin, onProgress: (ImportProgress) -> Unit) -> ImportResult,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    /** Primeira vez no app: sem o botão "Voltar". */
    welcome: Boolean = false,
    /** Servidor (DNS) do último login que deu certo, para não pedir de novo. */
    savedServer: String? = null,
    /** Algo a mais embaixo de tudo (ex.: botão de demonstração na versão de teste). */
    extraContent: @Composable () -> Unit = {},
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(EntryMode.Login) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(savedServer.orEmpty()) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    var fileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<SaveStatus>(SaveStatus.Idle) }
    var formError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    val userFocus = remember { FocusRequester() }
    val serverFocus = remember { FocusRequester() }
    val linkFocus = remember { FocusRequester() }
    val resultFocus = remember { FocusRequester() }
    val resultView = remember { BringIntoViewRequester() }
    // O servidor salvo pode chegar depois da tela abrir.
    LaunchedEffect(savedServer) { if (server.isBlank() && savedServer != null) server = savedServer }
    // Na TV o foco do controle já começa no primeiro campo de cada jeito de entrar.
    LaunchedEffect(mode) {
        if (isTv) runCatching { (if (mode == EntryMode.Login) userFocus else linkFocus).requestFocus() }
    }
    // O campo do servidor recebe o foco assim que aparece (tocou em "Usar outro servidor" ou faltou preenchê-lo).
    LaunchedEffect(showServer) {
        if (showServer) {
            kotlinx.coroutines.delay(50)
            runCatching { serverFocus.requestFocus() }
        }
    }
    // Quando o resultado chega, ele aparece na tela (e na TV o foco vai para o botão logo abaixo da mensagem).
    LaunchedEffect(status) {
        if (status is SaveStatus.Done || status is SaveStatus.Failed) {
            runCatching { resultView.bringIntoView() }
            if (isTv) runCatching { resultFocus.requestFocus() }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileUri = uri.toString()
            fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Arquivo escolhido"
            formError = null
        }
    }

    // Voltar: de link/arquivo volta para o login; do login sai (nas boas-vindas, sai do app normalmente).
    BackHandler(enabled = mode != EntryMode.Login || !welcome) {
        if (mode != EntryMode.Login && status !is SaveStatus.Done) {
            mode = EntryMode.Login
            formError = null
            status = SaveStatus.Idle
        } else {
            onBack()
        }
    }

    fun submit() {
        if (status == SaveStatus.Working) return
        // Colou o link completo (com usuário e senha dentro) em qualquer campo? Separa tudo sozinho.
        if (mode == EntryMode.Login) {
            (parseProviderLink(user) ?: parseProviderLink(server))?.let { parsed ->
                server = parsed.server
                user = parsed.user
                password = parsed.password
                showServer = true
            }
        }
        formError = when (mode) {
            EntryMode.Login -> when {
                user.isBlank() -> "Digite o seu usuário."
                password.isBlank() -> "Digite a sua senha."
                server.isBlank() -> {
                    showServer = true
                    "Falta o servidor (DNS): é o endereço que o seu fornecedor mandou junto com o usuário e a senha."
                }
                else -> null
            }
            EntryMode.Link -> if (!link.trim().startsWith("http", ignoreCase = true)) "Cole o link da lista (começa com http)." else null
            EntryMode.File -> if (fileUri == null) "Escolha o arquivo da lista." else null
        }
        if (formError != null) return
        status = SaveStatus.Working
        progress = null
        scope.launch {
            val onProgress: (ImportProgress) -> Unit = { progress = it }
            val result = when (mode) {
                EntryMode.Login -> onSaveProvider("", ProviderLogin(server, user, password), onProgress)
                EntryMode.Link -> onSaveLink(linkName(link), link, onProgress)
                EntryMode.File -> onSaveFile(fileName.orEmpty().substringBeforeLast('.'), Uri.parse(fileUri), onProgress)
            }
            // Usuário e senha ou link: vai direto para a tela de escolha (o resto chega por trás).
            if (result is ImportResult.Success && mode != EntryMode.File) {
                onFinished()
                return@launch
            }
            status = when (result) {
                is ImportResult.Success -> SaveStatus.Done(result.itemCount, result.report)
                is ImportResult.Failure -> SaveStatus.Failed(result.reason.message, result.detail, result.report)
            }
        }
    }

    val form: @Composable () -> Unit = {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 12.dp)) {
            if (!welcome) {
                NuvoraButton(text = "Voltar", icon = NuvoraIcons.ChevronLeft, style = NuvoraButtonStyle.Secondary, onClick = onBack)
            }
            val done = status as? SaveStatus.Done
            if (done != null) {
                NuvoraText("Pronto! ${formatCount(done.count)} itens encontrados.", NuvoraTheme.typography.title, color = colors.accent)
                NuvoraText("A sua lista já aparece no Início.", NuvoraTheme.typography.body, color = colors.textSecondary)
                EnterButton("Ir para o Início", onClick = onFinished, modifier = Modifier.focusRequester(resultFocus))
                done.report?.let { ReportPanel(it) }
                return@Column
            }
            when (mode) {
                EntryMode.Login -> {
                    NuvoraText("Entre com seu usuário e senha", NuvoraTheme.typography.title)
                    NuvoraText("Os dados que o seu fornecedor mandou.", NuvoraTheme.typography.body, color = colors.textSecondary)
                    NuvoraTextField(
                        value = user,
                        onValueChange = { user = it; formError = null },
                        label = "Usuário",
                        placeholder = "Digite seu usuário",
                        exact = true,
                        labelInside = true,
                        focusRequester = userFocus,
                        imeAction = ImeAction.Next,
                    )
                    NuvoraTextField(
                        value = password,
                        onValueChange = { password = it; formError = null },
                        label = "Senha",
                        placeholder = "Digite sua senha",
                        secret = true,
                        revealToggle = true,
                        labelInside = true,
                        imeAction = if (showServer) ImeAction.Next else ImeAction.Go,
                        onImeAction = ::submit,
                    )
                    if (showServer) {
                        NuvoraTextField(
                            value = server,
                            onValueChange = { server = it; formError = null },
                            label = "Servidor (DNS)",
                            placeholder = "http://exemplo.com:8080",
                            keyboardType = KeyboardType.Uri,
                            exact = true,
                            labelInside = true,
                            focusRequester = serverFocus,
                            imeAction = ImeAction.Go,
                            onImeAction = ::submit,
                        )
                        NuvoraText(
                            "Fica salvo: da próxima vez, só usuário e senha.",
                            NuvoraTheme.typography.caption,
                            color = colors.textSecondary,
                        )
                    } else {
                        TextLink(
                            if (server.isBlank()) "+ Usar outro servidor (DNS)" else "Servidor: ${serverLabel(server)} · Trocar",
                            Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            showServer = true
                        }
                    }
                }
                EntryMode.Link -> {
                    NuvoraText("Entrar com o link da lista", NuvoraTheme.typography.title)
                    NuvoraText("Um endereço que começa com http (lista M3U).", NuvoraTheme.typography.body, color = colors.textSecondary)
                    NuvoraTextField(
                        value = link,
                        onValueChange = { link = it; formError = null },
                        label = "Link da lista",
                        placeholder = "http://...",
                        keyboardType = KeyboardType.Uri,
                        exact = true,
                        labelInside = true,
                        focusRequester = linkFocus,
                        imeAction = ImeAction.Go,
                        onImeAction = ::submit,
                    )
                }
                EntryMode.File -> {
                    NuvoraText("Entrar com um arquivo M3U", NuvoraTheme.typography.title)
                    NuvoraText("Um arquivo .m3u salvo no celular.", NuvoraTheme.typography.body, color = colors.textSecondary)
                    NuvoraButton(
                        text = fileName ?: "Escolher arquivo",
                        icon = NuvoraIcons.File,
                        style = NuvoraButtonStyle.Secondary,
                        onClick = { pickFile.launch(arrayOf("*/*")) },
                    )
                }
            }
            formError?.let { NuvoraText(it, NuvoraTheme.typography.body, color = colors.error) }
            (status as? SaveStatus.Failed)?.let {
                NuvoraText(it.message, NuvoraTheme.typography.body, color = colors.error, modifier = Modifier.bringIntoViewRequester(resultView))
                it.report?.problem?.let { problem ->
                    NuvoraText("Tipo do problema: ${problem.label}", NuvoraTheme.typography.caption, color = colors.textPrimary)
                }
                it.detail?.let { detail ->
                    NuvoraText("Detalhe técnico: $detail", NuvoraTheme.typography.caption, color = colors.textSecondary)
                }
                it.report?.let { report -> ReportPanel(report, resultFocus) }
            }
            EnterButton(if (status == SaveStatus.Working) "ENTRANDO..." else "ENTRAR", onClick = ::submit)
            if (status == SaveStatus.Working) {
                NuvoraText(
                    when (val p = progress) {
                        null -> "Conectando... listas grandes podem levar alguns minutos."
                        is ImportProgress.Downloading -> "Baixando a lista... ${formatMegabytes(p.bytes)}"
                        is ImportProgress.Reading -> "Organizando a lista... já li ${formatCount(p.items)} itens."
                        is ImportProgress.Step -> p.text
                    },
                    NuvoraTheme.typography.body,
                    color = colors.textSecondary,
                )
            }
            NuvoraText(
                "Ao entrar, você confirma que tem direito de acesso a este conteúdo. O NUVORA é só um reprodutor: não vem com nenhum conteúdo.",
                NuvoraTheme.typography.caption,
                color = colors.textSecondary,
            )
            // Outras formas de acesso: discretas, sem disputar o destaque com o login.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvoraText("Outras formas:", NuvoraTheme.typography.caption, color = colors.textSecondary)
                if (mode != EntryMode.Login) TextLink("Usuário e senha") { mode = EntryMode.Login; formError = null; status = SaveStatus.Idle }
                if (mode != EntryMode.Link) TextLink("Link da lista") { mode = EntryMode.Link; formError = null; status = SaveStatus.Idle }
                // TVs normalmente não têm onde escolher arquivos.
                if (!isTv && mode != EntryMode.File) TextLink("Arquivo M3U") { mode = EntryMode.File; formError = null; status = SaveStatus.Idle }
            }
            extraContent()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 700.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                BrandPanel(Modifier.weight(0.46f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
                Box(
                    Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (isTv) 56.dp else 32.dp, vertical = if (isTv) 40.dp else 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { form() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                NuvoraLogo(Modifier.padding(top = 12.dp, bottom = 4.dp))
                form()
            }
        }
    }
}

/** Lado esquerdo das telas deitadas: a marca NUVORA com a frase de apresentação. */
@Composable
private fun BrandPanel(modifier: Modifier) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    Column(
        modifier.padding(horizontal = if (isTv) 64.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        NuvoraLogo()
        NuvoraText("Seus canais, filmes e séries num só lugar.", NuvoraTheme.typography.section)
        if (isTv) {
            NuvoraText("Use as setas do controle e OK para digitar.", NuvoraTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

/** Botão principal largo (ENTRAR / Ir para o Início), com o mesmo acabamento dos botões NUVORA. */
@Composable
private fun EnterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    NuvoraClickable(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = text },
        containerColor = colors.primary,
        focusedContainerColor = colors.primary,
        focusedBorderColor = colors.textPrimary,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = if (isTv) 16.dp else 14.dp), contentAlignment = Alignment.Center) {
            NuvoraText(text, NuvoraTheme.typography.section, color = colors.onPrimary)
        }
    }
}

/** Texto em ciano que funciona como botão (opções secundárias). */
@Composable
private fun TextLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    NuvoraClickable(onClick = onClick, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        NuvoraText(
            text,
            NuvoraTheme.typography.label,
            color = NuvoraTheme.colors.accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** "http://exemplo.com:8080" → "exemplo.com:8080" (só para mostrar). */
private fun serverLabel(server: String) = server.substringAfter("://").trimEnd('/')

/** Nome automático da lista por link: o endereço do site. */
private fun linkName(link: String) =
    runCatching { java.net.URI(link.trim()).host }.getOrNull()?.removePrefix("www.").orEmpty()

/** Relatório de conexão guardado (Ajustes): ver e copiar para mandar ao suporte. */
@Composable
private fun SavedReport(text: String) {
    val colors = NuvoraTheme.colors
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    NuvoraButton(
        text = if (open) "Esconder relatório de conexão" else "Ver relatório de conexão",
        icon = NuvoraIcons.File,
        style = NuvoraButtonStyle.Secondary,
        onClick = { open = !open },
    )
    if (!open) return
    Card {
        // Na TV cada linha recebe o foco do controle, para dar para rolar o relatório inteiro.
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            StepFocus(NuvoraTheme.isTv) {
                NuvoraText(line, NuvoraTheme.typography.caption, color = colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Box(Modifier.padding(top = 12.dp)) {
            NuvoraButton(
                text = if (copied) "Relatório copiado" else "Copiar relatório",
                icon = if (copied) NuvoraIcons.Check else NuvoraIcons.Link,
                style = NuvoraButtonStyle.Secondary,
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Relatório de conexão NUVORA", text))
                    copied = true
                },
            )
        }
    }
}

/** Na TV, deixa um trecho de texto receber o foco do controle (só para rolar a tela até ele). */
@Composable
private fun StepFocus(isTv: Boolean, content: @Composable () -> Unit) {
    if (!isTv) return content()
    NuvoraClickable(onClick = {}, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { content() }
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
