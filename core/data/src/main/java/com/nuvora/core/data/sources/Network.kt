package com.nuvora.core.data.sources

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Como o NUVORA se apresenta para provedores e servidores de vídeo: como um navegador comum.
 * Muitos provedores recusam aplicativos que não conhecem; com isso, a lista e o vídeo são entregues normalmente.
 */
const val NUVORA_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** Resposta de um pedido pequeno (login, teste): código, tipo e só o começo do conteúdo. */
internal class HttpReply(
    val code: Int,
    val contentType: String?,
    val body: ByteArray,
    /** A resposta era maior que o pedaço guardado. */
    val truncated: Boolean,
    /** Endereços para onde o provedor mandou o pedido (redirecionamentos), em ordem. */
    val redirects: List<String>,
    val millis: Long,
) {
    val text: String get() = String(body, Charsets.UTF_8)
}

/**
 * Faz um pedido pequeno e devolve a resposta mesmo quando é um erro (400, 404...), para o relatório de conexão.
 * Segue redirecionamentos (inclusive http → https) e, se o certificado do https for inválido, tenta pelo http.
 * [form]: se informado, o pedido vai como POST de formulário.
 */
internal fun httpProbe(address: String, form: String? = null, maxBytes: Int = 64 * 1024): HttpReply {
    val started = SystemClock.elapsedRealtime()
    val redirects = ArrayList<String>()
    var current = address
    var hops = 0
    while (true) {
        val connection = (URL(current).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", NUVORA_USER_AGENT)
            if (form != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
        }
        try {
            val code = try {
                if (form != null) connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                connection.responseCode
            } catch (e: javax.net.ssl.SSLException) {
                if (!current.startsWith("https://", ignoreCase = true)) throw e
                current = "http://" + current.substring("https://".length)
                redirects += current
                continue
            }
            if (code in 300..399 && hops < 5) {
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    current = URL(URL(current), location).toString()
                    redirects += current
                    hops++
                    continue
                }
            }
            val stream: InputStream? = if (code >= 400) connection.errorStream else runCatching { connection.inputStream }.getOrNull()
            val (body, truncated) = stream?.use { it.readUpTo(maxBytes) } ?: (ByteArray(0) to false)
            return HttpReply(code, connection.contentType, body, truncated, redirects, SystemClock.elapsedRealtime() - started)
        } finally {
            connection.disconnect()
        }
    }
}

private fun InputStream.readUpTo(max: Int): Pair<ByteArray, Boolean> {
    val buffer = ByteArray(max)
    var filled = 0
    while (filled < max) {
        val read = read(buffer, filled, max - filled)
        if (read < 0) return buffer.copyOf(filled) to false
        filled += read
    }
    return buffer to (read() >= 0)
}

/** Erro de rede com o tipo de problema já decidido (para o relatório). */
internal fun IOException.isNetworkFailure() =
    this is java.net.UnknownHostException || this is java.net.ConnectException || this is java.net.SocketTimeoutException ||
        this is java.net.NoRouteToHostException
