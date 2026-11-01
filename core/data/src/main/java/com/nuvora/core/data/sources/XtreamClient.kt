package com.nuvora.core.data.sources

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.runtime.Immutable
import com.nuvora.source.api.M3uEntry
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dados de acesso no formato "Provedor / Usuário / Senha" (padrão de mercado conhecido como Xtream). */
@Immutable
data class ProviderLogin(val server: String, val user: String, val password: String)

/** Um episódio de série, pronto para tocar. */
@Immutable
data class Episode(val id: String, val season: Int, val number: Int, val title: String, val url: String) {
    val label: String get() = "T$season E$number · $title"
}

/**
 * Se a pessoa colar o link completo que recebeu (get.php ou player_api.php com usuário e senha),
 * separa Provedor, Usuário e Senha sozinho. Devolve null se o texto não tiver esses dados.
 */
fun parseProviderLink(text: String): ProviderLogin? {
    val trimmed = text.trim()
    if (!trimmed.contains("username=", ignoreCase = true)) return null
    val uri = runCatching { java.net.URI(if (trimmed.contains("://")) trimmed else "http://$trimmed") }.getOrNull() ?: return null
    val query = (uri.rawQuery ?: return null).split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=', "").lowercase()
        if (key.isEmpty()) null else key to java.net.URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
    }.toMap()
    val user = query["username"]?.takeIf { it.isNotBlank() } ?: return null
    val password = query["password"]?.takeIf { it.isNotBlank() } ?: return null
    val host = uri.host ?: return null
    val port = if (uri.port != -1) ":${uri.port}" else ""
    return ProviderLogin("${uri.scheme ?: "http"}://$host$port", user, password)
}

/** "exemplo.com:8080/" ou "http://exemplo.com:8080/get.php?..." → "http://exemplo.com:8080". */
internal fun normalizeServer(raw: String): String {
    var text = raw.trim()
    if (!text.contains("://")) text = "http://$text"
    val url = URL(text)
    val port = if (url.port != -1) ":${url.port}" else ""
    return "${url.protocol}://${url.host}$port"
}

/** Como entrar no provedor, descoberto pelo [XtreamClient.connect]. */
internal sealed interface ProviderAccess {
    /** O provedor fala o padrão completo: categorias, canais, filmes e séries em pedidos separados. */
    data object Api : ProviderAccess

    /** O provedor só entrega a lista completa num endereço (plano B). */
    data class FullList(val url: String, val variant: String) : ProviderAccess
}

/** O que o provedor respondeu no login (só o que importa; usuário e senha ecoados são ignorados). */
internal class LoginInfo(val auth: Int?, val status: String?, val expDate: Long?, val formats: List<String>, val maxConnections: Int?)

/**
 * Conversa com o provedor no padrão Provedor/Usuário/Senha: confere o acesso e busca canais, filmes e séries
 * em pedidos separados (bem mais leve e confiável do que uma lista gigante de uma vez).
 * Cada resposta é lida aos poucos ([JsonReader]), sem guardar tudo na memória.
 * Cada pedido e resposta vai para o relatório de conexão ([report]), sem usuário nem senha.
 */
internal class XtreamClient(
    private val login: ProviderLogin,
    private val report: ReportBuilder? = null,
    private val hasInternet: () -> Boolean = { true },
    /** Baixa um endereço (com novas tentativas, http automático etc.) e devolve o conteúdo. */
    private val fetch: (String) -> InputStream,
) {
    private val credentials = "username=${enc(login.user)}&password=${enc(login.password)}"
    private val base = "${login.server}/player_api.php?$credentials"
    /** Formato dos canais ao vivo que o provedor aceita (m3u8 ou ts), descoberto no login. */
    var liveFormat = "m3u8"

    /** Dados da conta do último login que deu certo (vencimento, telas). */
    var account: LoginInfo? = null
        private set

    /**
     * Confere usuário e senha e descobre como este provedor entrega o conteúdo:
     * 1) pelo jeito padrão (player_api); 2) se recusar o formato do pedido, pelo mesmo jeito em POST;
     * 3) plano B: a lista completa pelo link (get.php) com o mesmo usuário e senha.
     * Lança [XtreamException] com o motivo e o tipo de problema quando nenhum caminho funciona.
     */
    fun connect(): ProviderAccess {
        val replies = ArrayList<HttpReply>()
        val first = probe("Login pelo jeito padrão (player_api)", base)
        replies += first
        loginFrom(first)?.let { return it }
        if (first.code == 400 || first.code == 405 || first.code == 422) {
            val post = probe("Login pelo jeito alternativo (mesmo pedido, enviado como formulário)", "${login.server}/player_api.php", form = credentials)
            replies += post
            loginFrom(post)?.let { return it }
        }
        for (variant in LIST_VARIANTS) {
            val url = fullListUrl(variant)
            val reply = probe("Plano B: lista completa pelo link (get.php)", url, maxBytes = 4 * 1024)
            replies += reply
            if (reply.code in 200..299 && reply.isM3u()) {
                report?.loginOk = true
                report?.add(
                    "Recursos do provedor",
                    null,
                    "o jeito padrão não funcionou, mas a lista completa pelo link sim (usuário e senha aceitos). " +
                        "Consultas separadas por categoria não estão disponíveis neste provedor.",
                    true,
                )
                return ProviderAccess.FullList(url, variant)
            }
            if (reply.code.isLimit()) fail(ImportFailure.Limited, ProblemKind.ServerResponse, "HTTP ${reply.code}", "O provedor limitou o acesso (código ${reply.code}).")
        }
        throw conclude(replies)
    }

    /** Decide o motivo quando nenhum caminho funcionou, olhando todas as respostas juntas. */
    private fun conclude(replies: List<HttpReply>): XtreamException {
        val codes = replies.map { it.code }
        val api = replies.first().code
        val list = replies.last().code
        return when {
            codes.any { it >= 500 } -> exception(ImportFailure.ServerError, ProblemKind.ServerResponse, "HTTP ${codes.first { it >= 500 }}",
                "O provedor respondeu com erro interno (código ${codes.first { it >= 500 }}). Costuma ser passageiro.")
            // Recusou o pedido padrão E não achou lista para esse usuário: o mais provável é usuário/senha não reconhecidos.
            (api in setOf(400, 401, 403, 422)) && list in setOf(401, 403, 404) -> exception(ImportFailure.NotRecognized, ProblemKind.Authentication,
                "player_api HTTP $api · get.php HTTP $list",
                "O provedor recusou os dois jeitos de entrar com esse usuário e senha. O mais provável é usuário ou senha " +
                    "diferente do cadastrado no provedor (ou acesso desativado). O formato dos pedidos é o mesmo que funciona em outros provedores.")
            api == 404 && list == 404 -> exception(ImportFailure.NotAServer, ProblemKind.ServerResponse, "HTTP 404",
                "Nesse endereço não existe provedor no padrão Provedor/Usuário/Senha. Confira o campo Provedor.")
            codes.all { it in 200..299 } -> exception(ImportFailure.NotAServer, ProblemKind.ServerResponse, "resposta inesperada",
                "O endereço respondeu, mas não como um provedor (veio outro tipo de conteúdo).")
            else -> exception(ImportFailure.RequestRejected, ProblemKind.RequestFormat, "player_api HTTP $api · get.php HTTP $list",
                "O provedor recusou todos os formatos de pedido conhecidos.")
        }
    }

    /** Lê a resposta do login. Devolve [ProviderAccess.Api] se entrou, null se é para tentar outro caminho. */
    private fun loginFrom(reply: HttpReply): ProviderAccess? {
        if (reply.code.isLimit()) fail(ImportFailure.Limited, ProblemKind.ServerResponse, "HTTP ${reply.code}", "O provedor limitou o acesso (código ${reply.code}).")
        if (reply.code !in 200..299) return null
        val info = parseLogin(reply.text) ?: return null
        if (info.auth == null) return null
        if (info.auth != 1) {
            fail(ImportFailure.WrongLogin, ProblemKind.Authentication, "auth=0",
                "O provedor respondeu no padrão e disse que o usuário ou a senha não conferem (auth=0).")
        }
        when (info.status?.lowercase()) {
            "expired" -> fail(ImportFailure.Expired, ProblemKind.Authentication, info.expDate?.let { "venceu em ${formatDate(it)}" } ?: "Expired",
                "Usuário e senha certos, mas a assinatura está vencida.")
            "banned", "disabled" -> fail(ImportFailure.Denied, ProblemKind.Authentication, info.status,
                "Usuário e senha certos, mas o acesso está bloqueado (${info.status}).")
        }
        if (info.formats.isNotEmpty() && "m3u8" !in info.formats && "ts" in info.formats) liveFormat = "ts"
        account = info
        report?.loginOk = true
        return ProviderAccess.Api
    }

    private fun probe(title: String, url: String, form: String? = null, maxBytes: Int = 64 * 1024): HttpReply {
        val reply = try {
            httpProbe(url, form, maxBytes)
        } catch (e: java.io.IOException) {
            val offline = !hasInternet()
            report?.add(title, url, "sem resposta: ${e.javaClass.simpleName}" + if (offline) " (aparelho sem internet)" else "", false)
            when {
                offline -> fail(ImportFailure.NoInternet, ProblemKind.Network, e.javaClass.simpleName, "O aparelho está sem internet.")
                e is java.net.UnknownHostException -> fail(ImportFailure.ServerNotFound, ProblemKind.Network, "endereço não existe",
                    "O endereço do campo Provedor não foi encontrado na internet (confira se digitou certo).")
                e.isNetworkFailure() -> fail(ImportFailure.ServerError, ProblemKind.Network, e.javaClass.simpleName,
                    "O servidor do provedor não respondeu (desligado, fora do ar ou bloqueando a conexão).")
                e is javax.net.ssl.SSLException -> fail(ImportFailure.InvalidCertificate, ProblemKind.Network, e.javaClass.simpleName,
                    "O certificado de segurança do provedor é inválido.")
                else -> fail(ImportFailure.ConnectionDropped, ProblemKind.Network, e.javaClass.simpleName, "A conexão caiu no meio da resposta.")
            }
        }
        val ok = reply.code in 200..299 && (parseLogin(reply.text)?.auth == 1 || reply.isM3u())
        report?.add(title, url, describe(reply), ok)
        return reply
    }

    /** Resumo da resposta para o relatório: código, tipo, tamanho, tempo e o formato do conteúdo (sem dados pessoais). */
    private fun describe(reply: HttpReply): String = buildString {
        append("HTTP ${reply.code}")
        reply.contentType?.let { append(" · ${it.substringBefore(';').trim()}") }
        append(" · ${reply.body.size}${if (reply.truncated) "+" else ""} bytes · ${reply.millis} ms")
        if (reply.redirects.isNotEmpty()) {
            append(" · redirecionado para ${reply.redirects.joinToString(" → ") { report?.maskUrl(it) ?: MASK }}")
        }
        append(" · conteúdo: ${shape(reply)}")
    }

    private fun shape(reply: HttpReply): String {
        val text = reply.text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            text.isEmpty() -> "vazio"
            reply.isM3u() -> "lista M3U"
            text.startsWith("{") || text.startsWith("[") -> runCatching { summarizeJson(text) }.getOrDefault("JSON incompleto ou inválido")
            text.startsWith("<") -> "página HTML (não é resposta de provedor)"
            else -> "texto " + (report?.serverText(text.take(80)) ?: "")
        }
    }

    /** "objeto {user_info{auth=1, status=Active}, server_info{url=..}}" — só campos que não são pessoais. */
    private fun summarizeJson(text: String): String {
        val json = JsonReader(java.io.StringReader(text))
        json.isLenient = true
        if (json.peek() == JsonToken.BEGIN_ARRAY) {
            var count = 0
            json.beginArray()
            while (json.hasNext()) {
                json.skipValue()
                count++
            }
            return "lista JSON com $count itens"
        }
        val parts = ArrayList<String>()
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            when {
                (name == "user_info" || name == "server_info") && json.peek() == JsonToken.BEGIN_OBJECT -> {
                    val safe = if (name == "user_info") USER_INFO_SAFE else SERVER_INFO_SAFE
                    val fields = ArrayList<String>()
                    json.beginObject()
                    while (json.hasNext()) {
                        val field = json.nextName()
                        val value = json.nextLooseString()
                        if (field in safe) fields += "$field=${if (field == "exp_date") value?.toLongOrNull()?.let { formatDate(it) } ?: value else value}"
                    }
                    json.endObject()
                    parts += "$name{${fields.joinToString(", ")}}"
                }
                name in setOf("error", "message", "msg", "status") -> parts += "$name=" + (json.nextLooseString()?.let { report?.serverText(it) } ?: "(vazio)")
                else -> {
                    json.skipValue()
                    parts += name
                }
            }
        }
        return "objeto JSON {${parts.joinToString(", ")}}"
    }

    private fun parseLogin(text: String): LoginInfo? = runCatching {
        var auth: Int? = null
        var status: String? = null
        var expDate: Long? = null
        var maxConnections: Int? = null
        val formats = mutableListOf<String>()
        val json = JsonReader(java.io.StringReader(text))
        if (json.peek() != JsonToken.BEGIN_OBJECT) return@runCatching null
        json.beginObject()
        while (json.hasNext()) {
            if (json.nextName() != "user_info" || json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue()
                continue
            }
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "auth" -> auth = json.nextLooseString()?.let { it.toIntOrNull() ?: if (it == "true") 1 else if (it == "false") 0 else null }
                    "status" -> status = json.nextLooseString()
                    "exp_date" -> expDate = json.nextLooseString()?.toLongOrNull()
                    "max_connections" -> maxConnections = json.nextLooseString()?.toIntOrNull()
                    "allowed_output_formats" -> if (json.peek() == JsonToken.BEGIN_ARRAY) {
                        json.beginArray()
                        while (json.hasNext()) json.nextLooseString()?.let(formats::add)
                        json.endArray()
                    } else {
                        json.skipValue()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }
        LoginInfo(auth, status, expDate, formats, maxConnections)
    }.getOrNull()

    private fun HttpReply.isM3u() = text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("#EXTM3U", ignoreCase = true)

    private fun Int.isLimit() = this == 429 || this == 456 || this == 458 || this == 509 || this == 884

    private fun exception(reason: ImportFailure, kind: ProblemKind, detail: String?, conclusion: String): XtreamException {
        report?.problem = kind
        report?.conclusion = conclusion
        return XtreamException(reason, detail, kind)
    }

    private fun fail(reason: ImportFailure, kind: ProblemKind, detail: String?, conclusion: String): Nothing =
        throw exception(reason, kind, detail, conclusion)

    /** Categorias de uma parte (pedido pequeno: só nomes). Anotado no relatório. */
    fun categories(kind: SectionKind): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        val url = "$base&action=get_${kind.key}_categories"
        recorded("${kind.label}: categorias", url, { "${list.size} categorias" }) {
            read(url) { json ->
                if (json.peek() != JsonToken.BEGIN_ARRAY) {
                    json.skipValue()
                    return@read
                }
                json.beginArray()
                while (json.hasNext()) {
                    val fields = json.readFlatObject()
                    val id = fields["category_id"] ?: continue
                    list += id to (fields["category_name"]?.trim()?.ifBlank { null } ?: "Outros")
                }
                json.endArray()
            }
        }
        return list
    }

    /**
     * Itens de uma parte: só de uma categoria ([categoryId]) ou todos (null).
     * Cada item vem com a categoria que o provedor diz que ele tem (para conferir se o filtro funcionou).
     */
    fun streams(kind: SectionKind, categoryId: String?, emit: (categoryId: String?, entry: M3uEntry) -> Unit) {
        val action = when (kind) {
            SectionKind.Live -> "get_live_streams"
            SectionKind.Movies -> "get_vod_streams"
            SectionKind.Series -> "get_series"
        }
        val url = "$base&action=$action" + (categoryId?.let { "&category_id=${enc(it)}" } ?: "")
        read(url) { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) {
                json.skipValue()
                return@read
            }
            json.beginArray()
            while (json.hasNext()) {
                val fields = json.readFlatObject()
                val name = fields["name"]?.ifBlank { null } ?: continue
                val url = when (kind) {
                    SectionKind.Live -> "${login.server}/live/${login.user}/${login.password}/${fields["stream_id"] ?: continue}.$liveFormat"
                    SectionKind.Movies -> {
                        val ext = fields["container_extension"]?.ifBlank { null } ?: "mp4"
                        "${login.server}/movie/${login.user}/${login.password}/${fields["stream_id"] ?: continue}.$ext"
                    }
                    SectionKind.Series -> "$SERIES_PREFIX${fields["series_id"] ?: continue}"
                }
                emit(
                    fields["category_id"],
                    M3uEntry(
                        title = name,
                        url = url,
                        logo = (fields["stream_icon"] ?: fields["cover"])?.takeIf { it.startsWith("http") },
                        group = null,
                        rating = fields["rating"]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it) },
                    ),
                )
            }
            json.endArray()
        }
    }

    /** Endereço da lista completa (plano B), com o mesmo usuário e senha. */
    fun fullListUrl(variant: String) = "${login.server}/get.php?$credentials$variant"

    fun episodes(seriesId: String): List<Episode> {
        val list = ArrayList<Episode>()
        read("$base&action=get_series_info&series_id=${enc(seriesId)}") { json ->
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue()
                return@read
            }
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() != "episodes" || json.peek() != JsonToken.BEGIN_OBJECT) {
                    json.skipValue()
                    continue
                }
                json.beginObject()
                while (json.hasNext()) {
                    val seasonKey = json.nextName().toIntOrNull() ?: 0
                    if (json.peek() != JsonToken.BEGIN_ARRAY) {
                        json.skipValue()
                        continue
                    }
                    json.beginArray()
                    while (json.hasNext()) {
                        val fields = json.readFlatObject()
                        val id = fields["id"] ?: continue
                        val season = fields["season"]?.toIntOrNull() ?: seasonKey
                        val number = fields["episode_num"]?.toIntOrNull() ?: (list.count { it.season == season } + 1)
                        val ext = fields["container_extension"]?.ifBlank { null } ?: "mp4"
                        list += Episode(
                            id = id,
                            season = season,
                            number = number,
                            title = fields["title"]?.ifBlank { null } ?: "Episódio $number",
                            url = "${login.server}/series/${login.user}/${login.password}/$id.$ext",
                        )
                    }
                    json.endArray()
                }
                json.endObject()
            }
            json.endObject()
        }
        return list.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /** Faz o pedido e anota no relatório o resultado (ou a falha, que conta como erro de carregamento da lista). */
    internal fun recorded(title: String, url: String, summary: () -> String, block: () -> Unit) {
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            block()
            report?.add(title, url, "${summary()} em ${android.os.SystemClock.elapsedRealtime() - started} ms", true)
        } catch (e: Exception) {
            val detail = (e as? DetailedFailure)?.detail ?: e.javaClass.simpleName
            report?.add(title, url, "falhou depois de ${android.os.SystemClock.elapsedRealtime() - started} ms: $detail (${summary()} lidos até aqui)", false)
            throw e
        }
    }

    private fun read(url: String, block: (JsonReader) -> Unit) {
        fetch(url).use { input -> JsonReader(InputStreamReader(input, Charsets.UTF_8)).use(block) }
    }

    companion object {
        /** Marca interna: o item é uma série (os episódios são buscados quando a pessoa abre). */
        const val SERIES_PREFIX = "xtream-series:"

        /** Jeitos de pedir a lista completa (plano B), na ordem em que são tentados. */
        val LIST_VARIANTS = listOf("&type=m3u_plus&output=hls", "&type=m3u_plus")

        private fun enc(text: String) = Uri.encode(text)

        private fun formatDate(epochSeconds: Long) =
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(epochSeconds * 1000))
    }
}

/** Falha com um código curto para o suporte (ex.: "HTTP 404"); nunca contém senha. */
internal interface DetailedFailure {
    val detail: String?
}

internal class XtreamException(
    val reason: ImportFailure,
    override val detail: String?,
    val kind: ProblemKind? = null,
) : java.io.IOException(reason.name), DetailedFailure

private val USER_INFO_SAFE = setOf("auth", "status", "exp_date", "is_trial", "active_cons", "max_connections")
private val SERVER_INFO_SAFE = setOf("url", "port", "https_port", "server_protocol", "timezone")

/** Lê qualquer valor simples como texto (provedores misturam números e textos nos mesmos campos). */
private fun JsonReader.nextLooseString(): String? = when (peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    JsonToken.NULL -> {
        nextNull()
        null
    }
    else -> {
        skipValue()
        null
    }
}

/** Lê um objeto guardando só os campos simples (ignora listas e objetos dentro dele). */
private fun JsonReader.readFlatObject(): Map<String, String> {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return emptyMap()
    }
    val map = HashMap<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        nextLooseString()?.let { map[name] = it }
    }
    endObject()
    return map
}
