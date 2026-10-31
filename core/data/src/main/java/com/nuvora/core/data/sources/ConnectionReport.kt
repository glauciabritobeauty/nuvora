package com.nuvora.core.data.sources

import androidx.compose.runtime.Immutable
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Onde está o problema, separado de forma clara (cada um tem uma solução diferente). */
enum class ProblemKind(val label: String) {
    Network("Rede: o aparelho não conseguiu chegar até o provedor"),
    Authentication("Autenticação: o provedor não aceitou o usuário e a senha"),
    RequestFormat("Formato do pedido: o provedor não aceitou o jeito do pedido"),
    ServerResponse("Resposta do servidor: o provedor respondeu algo diferente do esperado"),
    ListLoading("Carregamento da lista: o login deu certo, mas a lista não carregou"),
}

@Immutable
data class ReportStep(val title: String, val request: String?, val result: String, val ok: Boolean)

/**
 * Relatório de conexão: cada pedido feito ao provedor, o que ele respondeu e a conclusão.
 * Nunca contém usuário, senha, tokens nem códigos de acesso (são trocados por •••).
 */
@Immutable
data class ConnectionReport(
    val header: String,
    val steps: List<ReportStep>,
    val problem: ProblemKind?,
    val conclusion: String,
) {
    fun asText(): String = buildString {
        appendLine("Relatório de conexão do NUVORA")
        appendLine(header)
        appendLine("(usuário, senha e códigos de acesso aparecem como $MASK)")
        steps.forEachIndexed { index, step ->
            appendLine()
            appendLine("${index + 1}. ${step.title}: ${if (step.ok) "ok" else "falhou"}")
            step.request?.let { appendLine("   pedido: $it") }
            appendLine("   resposta: ${step.result}")
        }
        appendLine()
        problem?.let { appendLine("Tipo do problema: ${it.label}") }
        append("Conclusão: $conclusion")
    }
}

internal const val MASK = "•••"

/** Partes do endereço que podem aparecer no relatório (o resto é escondido). */
private val SAFE_KEYS = setOf("action", "type", "output", "category_id", "series_id", "vod_id", "stream_id")

/** Monta o relatório e garante que usuário e senha nunca apareçam nele. */
internal class ReportBuilder(user: String, password: String, private val header: String) {
    private val steps = ArrayList<ReportStep>()
    private val secrets: List<String> = listOf(user, password)
        .flatMap { listOf(it, URLEncoder.encode(it, "UTF-8"), URLEncoder.encode(it, "UTF-8").replace("+", "%20")) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }

    /** Com usuário ou senha muito curtos, textos livres do servidor não entram (a troca por ••• não seria segura). */
    private val shortSecret = listOf(user, password).any { it.isNotEmpty() && it.length < 3 }

    var problem: ProblemKind? = null
    var conclusion: String = ""
    /** O provedor aceitou usuário e senha (qualquer falha depois disso é de carregamento da lista). */
    var loginOk = false

    fun add(title: String, address: String?, result: String, ok: Boolean) {
        steps += ReportStep(title, address?.let(::maskUrl), clean(result), ok)
    }

    fun build() = ConnectionReport(header, steps.toList(), problem, clean(conclusion))

    /** Texto escrito pelo servidor (ex.: mensagem de erro): limpo e curto. */
    fun serverText(text: String): String =
        if (shortSecret) "(texto do servidor oculto por segurança)" else "\"${clean(text.replace(Regex("\\s+"), " ").trim()).take(120)}\""

    /** Endereço com usuário, senha e qualquer parâmetro desconhecido (tokens) trocados por •••. */
    fun maskUrl(address: String): String {
        val uri = runCatching { URI(address) }.getOrNull() ?: return "(endereço oculto)"
        val path = (uri.rawPath ?: "").split('/').joinToString("/") { segment -> if (isSecret(decode(segment))) MASK else segment }
        val query = uri.rawQuery?.split('&')?.joinToString("&") { pair ->
            val key = pair.substringBefore('=')
            val value = decode(pair.substringAfter('=', ""))
            if (key.lowercase() in SAFE_KEYS && !isSecret(value)) pair else "$key=$MASK"
        }
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return clean("${uri.scheme}://${uri.host}$port$path" + (query?.let { "?$it" } ?: ""))
    }

    fun clean(text: String): String {
        if (secrets.isEmpty()) return text
        var out = text
        secrets.filter { it.length >= 3 }.forEach { out = out.replace(it, MASK) }
        return out
    }

    private fun isSecret(text: String) = text.isNotEmpty() && secrets.any { it == text }

    private fun decode(text: String) = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
}
