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
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val user = uri.getQueryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
    val password = uri.getQueryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
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

/**
 * Conversa com o provedor no padrão Provedor/Usuário/Senha: confere o acesso e busca canais, filmes e séries
 * em pedidos separados (bem mais leve e confiável do que uma lista gigante de uma vez).
 * Cada resposta é lida aos poucos ([JsonReader]), sem guardar tudo na memória.
 */
internal class XtreamClient(
    private val login: ProviderLogin,
    /** Baixa um endereço (com novas tentativas, http automático etc.) e devolve o conteúdo. */
    private val fetch: (String) -> InputStream,
) {
    private val base = "${login.server}/player_api.php?username=${enc(login.user)}&password=${enc(login.password)}"
    private var liveFormat = "m3u8"

    /** Confere usuário e senha; lança [XtreamException] com o motivo se não der. */
    fun checkLogin() {
        var auth: Int? = null
        var status: String? = null
        var expDate: Long? = null
        val formats = mutableListOf<String>()
        read(base) { json ->
            if (json.peek() != JsonToken.BEGIN_OBJECT) throw XtreamException(ImportFailure.NotAServer, "resposta não é JSON")
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() != "user_info" || json.peek() != JsonToken.BEGIN_OBJECT) {
                    json.skipValue()
                    continue
                }
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "auth" -> auth = json.nextLooseString()?.toIntOrNull()
                        "status" -> status = json.nextLooseString()
                        "exp_date" -> expDate = json.nextLooseString()?.toLongOrNull()
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
            json.endObject()
        }
        if (auth == null) throw XtreamException(ImportFailure.NotAServer, "sem user_info")
        if (auth != 1) throw XtreamException(ImportFailure.WrongLogin, "auth=0")
        when (status?.lowercase()) {
            "expired" -> throw XtreamException(ImportFailure.Expired, expDate?.let { "venceu em ${formatDate(it)}" } ?: "Expired")
            "banned", "disabled" -> throw XtreamException(ImportFailure.Denied, status)
        }
        if (formats.isNotEmpty() && "m3u8" !in formats && "ts" in formats) liveFormat = "ts"
    }

    /** Busca canais, filmes e séries, entregando cada item em [emit]. Devolve quantos itens foram entregues. */
    fun importAll(onStep: (String) -> Unit, emit: (M3uEntry) -> Unit): Int {
        var count = 0
        val countingEmit: (M3uEntry) -> Unit = { emit(it); count++ }
        onStep("Buscando canais...")
        readItems("live", "TV", countingEmit)
        onStep("Buscando filmes...")
        readItems("vod", "Filmes", countingEmit)
        onStep("Buscando séries...")
        readItems("series", "Séries", countingEmit)
        return count
    }

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

    private fun readItems(kind: String, label: String, emit: (M3uEntry) -> Unit) {
        val categories = HashMap<String, String>()
        read("$base&action=get_${kind}_categories") { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) {
                json.skipValue()
                return@read
            }
            json.beginArray()
            while (json.hasNext()) {
                val fields = json.readFlatObject()
                val id = fields["category_id"] ?: continue
                categories[id] = fields["category_name"]?.ifBlank { null } ?: "Outros"
            }
            json.endArray()
        }
        val action = when (kind) {
            "live" -> "get_live_streams"
            "vod" -> "get_vod_streams"
            else -> "get_series"
        }
        read("$base&action=$action") { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) {
                json.skipValue()
                return@read
            }
            json.beginArray()
            while (json.hasNext()) {
                val fields = json.readFlatObject()
                val name = fields["name"]?.ifBlank { null } ?: continue
                val url = when (kind) {
                    "live" -> "${login.server}/live/${login.user}/${login.password}/${fields["stream_id"] ?: continue}.$liveFormat"
                    "vod" -> {
                        val ext = fields["container_extension"]?.ifBlank { null } ?: "mp4"
                        "${login.server}/movie/${login.user}/${login.password}/${fields["stream_id"] ?: continue}.$ext"
                    }
                    else -> "$SERIES_PREFIX${fields["series_id"] ?: continue}"
                }
                val category = categories[fields["category_id"]] ?: "Outros"
                emit(
                    M3uEntry(
                        title = name,
                        url = url,
                        logo = (fields["stream_icon"] ?: fields["cover"])?.takeIf { it.startsWith("http") },
                        group = "$label · $category",
                    ),
                )
            }
            json.endArray()
        }
    }

    private fun read(url: String, block: (JsonReader) -> Unit) {
        fetch(url).use { input -> JsonReader(InputStreamReader(input, Charsets.UTF_8)).use(block) }
    }

    companion object {
        /** Marca interna: o item é uma série (os episódios são buscados quando a pessoa abre). */
        const val SERIES_PREFIX = "xtream-series:"

        private fun enc(text: String) = Uri.encode(text)

        private fun formatDate(epochSeconds: Long) =
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(epochSeconds * 1000))
    }
}

internal class XtreamException(val reason: ImportFailure, val detail: String?) : java.io.IOException(reason.name)

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
