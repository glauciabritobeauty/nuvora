package com.nuvora.core.data.sources

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import com.nuvora.core.data.diagnosticsEnabled
import com.nuvora.core.data.model.CardItem
import com.nuvora.core.data.model.HeroItem
import com.nuvora.core.data.model.PlaybackRequest
import com.nuvora.core.data.security.CryptoBox
import com.nuvora.source.api.M3uEntry
import com.nuvora.source.api.M3uSummary
import com.nuvora.source.api.parseM3u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Uma lista cadastrada. */
@Immutable
data class SourceInfo(
    val id: Long,
    val name: String,
    val kind: SourceKind,
    val itemCount: Int,
    val updatedAt: Long,
)

enum class SourceKind(val key: String, val label: String) {
    Link("link", "Link da lista"),
    File("arquivo", "Arquivo do aparelho"),
    Provider("servidor", "Provedor, usuário e senha"),
}

/** Por que uma lista não pôde ser lida, em linguagem de gente. */
enum class ImportFailure(val message: String) {
    NoInternet("Sem internet. Verifique o Wi-Fi e tente de novo."),
    NotFound("Não encontramos a lista nesse endereço. Confira o link."),
    Denied("O provedor recusou o acesso. Confira se a assinatura está ativa."),
    NotAList("Esse endereço não é uma lista de canais (.m3u). Confira o link."),
    Empty("A lista está vazia: não tem nenhum canal, filme ou série."),
    ServerError("O provedor está com problema agora. Tente de novo mais tarde."),
    ConnectionDropped("A conexão com o provedor caiu no meio da lista, mesmo tentando de novo. Tente mais tarde; se continuar, fale com o seu fornecedor."),
    InvalidCertificate("O certificado de segurança desse provedor é inválido. Fale com o seu fornecedor."),
    Limited("O provedor limitou o acesso agora (muitas conexões ao mesmo tempo ou assinatura vencida). Tente mais tarde ou fale com o seu fornecedor."),
    FileUnreadable("Não foi possível abrir esse arquivo. Escolha a lista de novo."),
    WrongLogin("Usuário ou senha incorretos. Confira e tente de novo."),
    Expired("A sua assinatura venceu. Fale com o seu fornecedor."),
    NotAServer("Esse endereço não respondeu como um provedor. Confira o campo Provedor."),
    Other("Não foi possível ler a lista. Tente de novo."),
}

/** Andamento: primeiro baixa a lista inteira, depois organiza no aparelho. */
sealed interface ImportProgress {
    data class Downloading(val bytes: Long) : ImportProgress
    data class Reading(val items: Int) : ImportProgress
    data class Step(val text: String) : ImportProgress
}

sealed interface ImportResult {
    data class Success(val itemCount: Int) : ImportResult
    /** [detail]: código curto para o suporte (ex.: "HTTP 456"), mostrado pequeno embaixo da mensagem. Nunca contém senha. */
    data class Failure(val reason: ImportFailure, val detail: String? = null) : ImportResult
}

/** O que a Home mostra de todas as listas juntas (só uma amostra por categoria, nunca a lista inteira). */
@Immutable
data class SourceCatalog(
    val hero: List<HeroItem>,
    val categories: List<String>,
    val items: List<CardItem>,
)

private class SourcesDatabase(context: Context) : SQLiteOpenHelper(context, "fontes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE fontes (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, tipo TEXT NOT NULL, " +
                "endereco_cifrado TEXT NOT NULL, itens INTEGER NOT NULL, atualizado_em INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE itens (fonte_id INTEGER NOT NULL, posicao INTEGER NOT NULL, titulo TEXT NOT NULL, " +
                "logo TEXT, grupo TEXT, url_cifrada TEXT NOT NULL, PRIMARY KEY (fonte_id, posicao))",
        )
        db.execSQL("CREATE INDEX itens_grupo ON itens (grupo)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * Listas de conteúdo da pessoa (M3U por link ou arquivo).
 *
 * A lista é lida aos poucos e gravada direto no banco do aparelho, sem ficar inteira na memória
 * (listas de IPTV podem ter dezenas de milhares de itens). Endereços — que muitas vezes têm usuário
 * e senha dentro — ficam cifrados pelo [CryptoBox].
 */
class SourcesRepository(context: Context, private val crypto: CryptoBox) {
    private val appContext = context.applicationContext
    private val database = SourcesDatabase(appContext)
    private val debug = diagnosticsEnabled(appContext)

    private val _sources = MutableStateFlow<List<SourceInfo>>(emptyList())
    val sources: StateFlow<List<SourceInfo>> = _sources.asStateFlow()

    suspend fun load() = io { refreshSources() }

    suspend fun addLink(name: String, url: String, onProgress: (ImportProgress) -> Unit = {}): ImportResult = io {
        importFrom(sourceId = null, name = name, kind = SourceKind.Link, address = url.trim(), onProgress = onProgress) { emit ->
            m3u(downloadToCache(url.trim(), onProgress), emit)
        }
    }

    /** Provedor / Usuário / Senha: confere o acesso e busca canais, filmes e séries. */
    suspend fun addProvider(name: String, login: ProviderLogin, onProgress: (ImportProgress) -> Unit = {}): ImportResult = io {
        val clean = runCatching { login.copy(server = normalizeServer(login.server)) }.getOrElse {
            return@io ImportResult.Failure(ImportFailure.NotAServer, "endereço inválido")
        }
        importFrom(sourceId = null, name = name, kind = SourceKind.Provider, address = encodeLogin(clean), onProgress = onProgress) { emit ->
            importProvider(clean, onProgress, emit)
        }
    }

    /** Episódios de uma série de provedor (buscados na hora, só quando a pessoa abre a série). */
    suspend fun episodes(itemId: String): List<Episode> = io {
        val (sourceId, position) = parseId(itemId) ?: return@io emptyList()
        val (_, kind, address) = sourceAddress(sourceId) ?: return@io emptyList()
        if (kind != SourceKind.Provider) return@io emptyList()
        val seriesId = database.readableDatabase.rawQuery(
            "SELECT url_cifrada FROM itens WHERE fonte_id = ? AND posicao = ?",
            arrayOf(sourceId.toString(), position.toString()),
        ).use { c -> if (c.moveToFirst()) crypto.decrypt(c.getString(0)) else null }
            ?.removePrefix(XtreamClient.SERIES_PREFIX) ?: return@io emptyList()
        runCatching { XtreamClient(decodeLogin(address)) { url -> downloadToCache(url) {} }.episodes(seriesId) }
            .getOrDefault(emptyList())
            .also { tempFile().delete() }
    }

    suspend fun addFile(name: String, uri: Uri, onProgress: (ImportProgress) -> Unit = {}): ImportResult = io {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        importFrom(sourceId = null, name = name, kind = SourceKind.File, address = uri.toString(), onProgress = onProgress) { emit ->
            m3u(openFile(uri), emit)
        }
    }

    /** Lê a lista de novo (o fornecedor pode ter mudado). Se der errado, a versão antiga continua valendo. */
    suspend fun refresh(id: Long): ImportResult = io {
        val (name, kind, address) = sourceAddress(id) ?: return@io ImportResult.Failure(ImportFailure.Other)
        importFrom(sourceId = id, name = name, kind = kind, address = address) { emit ->
            when (kind) {
                SourceKind.Link -> m3u(downloadToCache(address) {}, emit)
                SourceKind.File -> m3u(openFile(Uri.parse(address)), emit)
                SourceKind.Provider -> importProvider(decodeLogin(address), {}, emit)
            }
        }
    }

    suspend fun remove(id: Long) = io {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("itens", "fonte_id = ?", arrayOf(id.toString()))
            db.delete("fontes", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshSources()
    }

    /** Amostra para a Home: alguns itens de cada uma das primeiras categorias. */
    suspend fun loadCatalog(perCategory: Int = 40, maxCategories: Int = 30): SourceCatalog = io {
        val started = SystemClock.elapsedRealtime()
        val db = database.readableDatabase
        val categories = db.rawQuery(
            "SELECT grupo FROM itens WHERE grupo IS NOT NULL GROUP BY grupo ORDER BY MIN(fonte_id * 10000000 + posicao) LIMIT ?",
            arrayOf(maxCategories.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val items = ArrayList<CardItem>()
        categories.forEach { group -> items += query("WHERE grupo = ? ORDER BY fonte_id, posicao LIMIT ?", group, perCategory.toString()) }
        if (items.isEmpty()) items += query("ORDER BY fonte_id, posicao LIMIT ?", perCategory.toString())
        if (debug) Log.i("NuvoraFontes", "medida home ${items.size} cartoes ${SystemClock.elapsedRealtime() - started} ms")
        SourceCatalog(
            hero = items.take(3).map { HeroItem(it.id, it.title, it.category ?: "Da sua lista", null) },
            categories = categories,
            items = items,
        )
    }

    suspend fun search(text: String, limit: Int = 100): List<CardItem> = io {
        val escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (escaped.isEmpty()) emptyList() else query("WHERE titulo LIKE ? ESCAPE '\\' ORDER BY fonte_id, posicao LIMIT ?", "%$escaped%", limit.toString())
    }

    suspend fun item(id: String): CardItem? = io {
        val (source, position) = parseId(id) ?: return@io null
        query("WHERE fonte_id = ? AND posicao = ?", source.toString(), position.toString()).firstOrNull()
    }

    /** O que tocar para um item: o endereço é decifrado só agora, só para este item. */
    suspend fun playback(id: String): PlaybackRequest? = io {
        val (source, position) = parseId(id) ?: return@io null
        database.readableDatabase.rawQuery(
            "SELECT i.titulo, i.logo, i.grupo, i.url_cifrada, f.nome FROM itens i JOIN fontes f ON f.id = i.fonte_id " +
                "WHERE i.fonte_id = ? AND i.posicao = ?",
            arrayOf(source.toString(), position.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            PlaybackRequest(
                id = id,
                title = c.getString(0),
                subtitle = listOfNotNull(c.getString(2), c.getString(4)).joinToString(" · "),
                url = crypto.decrypt(c.getString(3)),
                mimeType = null,
                imageUrl = c.getString(1),
            )
        }
    }

    private fun query(where: String, vararg args: String): List<CardItem> =
        database.readableDatabase.rawQuery("SELECT fonte_id, posicao, titulo, logo, grupo FROM itens $where", args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CardItem(
                            id = "$ID_PREFIX${c.getLong(0)}-${c.getLong(1)}",
                            title = c.getString(2),
                            imageUrl = c.getString(3),
                            category = c.getString(4),
                            caption = c.getString(4),
                        ),
                    )
                }
            }
        }

    private fun importFrom(
        sourceId: Long?,
        name: String,
        kind: SourceKind,
        address: String,
        onProgress: (ImportProgress) -> Unit = {},
        produce: (emit: (M3uEntry) -> Unit) -> M3uSummary,
    ): ImportResult {
        val started = SystemClock.elapsedRealtime()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val id = sourceId ?: db.insert(
                "fontes",
                null,
                ContentValues().apply {
                    put("nome", name.trim().ifEmpty { "Minha lista" })
                    put("tipo", kind.key)
                    put("endereco_cifrado", crypto.encrypt(address))
                    put("itens", 0)
                    put("atualizado_em", System.currentTimeMillis())
                },
            )
            if (sourceId != null) db.delete("itens", "fonte_id = ?", arrayOf(sourceId.toString()))
            val insert = db.compileStatement(
                "INSERT INTO itens (fonte_id, posicao, titulo, logo, grupo, url_cifrada) VALUES (?, ?, ?, ?, ?, ?)",
            )
            var position = 0L
            val summary = produce { entry ->
                insert.clearBindings()
                insert.bindLong(1, id)
                insert.bindLong(2, position++)
                insert.bindString(3, entry.title)
                entry.logo?.let { insert.bindString(4, it) }
                entry.group?.let { insert.bindString(5, it) }
                insert.bindString(6, crypto.encrypt(entry.url))
                insert.executeInsert()
                if (position % 2_000 == 0L) onProgress(ImportProgress.Reading(position.toInt()))
            }
            if (summary.entries == 0) {
                return ImportResult.Failure(if (summary.looksLikeM3u) ImportFailure.Empty else ImportFailure.NotAList)
            }
            db.update(
                "fontes",
                ContentValues().apply {
                    put("itens", summary.entries)
                    put("atualizado_em", System.currentTimeMillis())
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            db.setTransactionSuccessful()
            if (debug) Log.i("NuvoraFontes", "importou ${summary.entries} itens em ${SystemClock.elapsedRealtime() - started} ms")
            return ImportResult.Success(summary.entries)
        } catch (failure: ImportException) {
            return ImportResult.Failure(failure.reason, failure.detail)
        } catch (failure: XtreamException) {
            return ImportResult.Failure(failure.reason, failure.detail)
        } catch (e: IOException) {
            return ImportResult.Failure(e.toFailure(kind), e.javaClass.simpleName)
        } catch (e: SecurityException) {
            return ImportResult.Failure(ImportFailure.FileUnreadable, e.javaClass.simpleName)
        } catch (e: RuntimeException) {
            // Nunca derruba o app por causa de uma lista estranha: explica e deixa tentar de novo.
            return ImportResult.Failure(ImportFailure.Other, e.javaClass.simpleName)
        } finally {
            db.endTransaction()
            tempFile().delete()
            refreshSources()
        }
    }

    private fun tempFile() = java.io.File(appContext.cacheDir, "lista-baixando.m3u")

    private fun m3u(input: InputStream, emit: (M3uEntry) -> Unit): M3uSummary =
        input.use { parseM3u(InputStreamReader(it, Charsets.UTF_8), emit) }

    private fun importProvider(login: ProviderLogin, onProgress: (ImportProgress) -> Unit, emit: (M3uEntry) -> Unit): M3uSummary {
        val client = XtreamClient(login) { url -> downloadToCache(url) {} }
        onProgress(ImportProgress.Step("Conferindo usuário e senha..."))
        var mark = SystemClock.elapsedRealtime()
        client.checkLogin()
        if (debug) Log.i("NuvoraFontes", "medida login ${SystemClock.elapsedRealtime() - mark} ms")
        mark = SystemClock.elapsedRealtime()
        val count = client.importAll({ onProgress(ImportProgress.Step(it)) }, emit)
        if (debug) Log.i("NuvoraFontes", "medida conteudo $count itens ${SystemClock.elapsedRealtime() - mark} ms")
        return M3uSummary(looksLikeM3u = true, entries = count)
    }

    // Provedor/Usuário/Senha guardados juntos (e cifrados) no campo de endereço.
    private fun encodeLogin(login: ProviderLogin) =
        org.json.JSONObject().put("s", login.server).put("u", login.user).put("p", login.password).toString()

    private fun decodeLogin(text: String) =
        org.json.JSONObject(text).let { ProviderLogin(it.getString("s"), it.getString("u"), it.getString("p")) }

    /**
     * Baixa a lista inteira para um arquivo temporário antes de organizar: assim a conexão com o provedor
     * termina rápido (alguns cortam conexões longas). Se a conexão cair no meio, tenta de novo sozinho (até 3 vezes).
     */
    private fun downloadToCache(address: String, onProgress: (ImportProgress) -> Unit): InputStream {
        val file = tempFile()
        var lastError: IOException? = null
        for (attempt in 1..3) {
            try {
                openUrl(address).use { input ->
                    file.outputStream().buffered(256 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                            if (total - lastReport >= 512 * 1024) {
                                lastReport = total
                                onProgress(ImportProgress.Downloading(total))
                            }
                        }
                    }
                }
                if (debug && attempt > 1) Log.i("NuvoraFontes", "lista baixada na tentativa $attempt")
                if (debug) Log.i("NuvoraFontes", "medida baixou ${file.length() / 1024} KB")
                return file.inputStream()
            } catch (definitive: ImportException) {
                throw definitive // respostas claras do provedor (404, 403, limite...) não adiantam repetir
            } catch (e: IOException) {
                lastError = e
                if (debug) Log.i("NuvoraFontes", "conexão caiu na tentativa $attempt: ${e.javaClass.simpleName}")
                Thread.sleep(2_000L * attempt)
            }
        }
        throw lastError ?: IOException("falha ao baixar")
    }

    private fun openUrl(address: String): InputStream {
        var current = address
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                // Alguns provedores levam bastante tempo para montar listas grandes antes de começar a enviar.
                readTimeout = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", NUVORA_USER_AGENT)
            }
            val code = try {
                connection.responseCode
            } catch (e: javax.net.ssl.SSLException) {
                // Certificado inválido no https: tenta o mesmo endereço pelo http (muitos provedores oferecem os dois).
                connection.disconnect()
                if (!current.startsWith("https://", ignoreCase = true)) throw e
                if (debug) Log.i("NuvoraFontes", "certificado inválido; tentando http")
                current = "http://" + current.substring("https://".length)
                return@repeat
            }
            when {
                code in 200..299 -> return connection.inputStream
                code in 300..399 -> {
                    // Segue redirecionamentos, inclusive de http para https (o Android não faz isso sozinho).
                    current = URL(URL(current), connection.getHeaderField("Location") ?: throw ImportException(ImportFailure.NotFound, "HTTP $code")).toString()
                    connection.disconnect()
                }
                code == 401 || code == 403 -> throw ImportException(ImportFailure.Denied, "HTTP $code")
                code == 404 || code == 410 -> throw ImportException(ImportFailure.NotFound, "HTTP $code")
                // Códigos que provedores usam para limite de conexões, bloqueio temporário ou assinatura vencida.
                code == 429 || code == 456 || code == 458 || code == 509 || code == 884 ->
                    throw ImportException(ImportFailure.Limited, "HTTP $code")
                code >= 500 -> throw ImportException(ImportFailure.ServerError, "HTTP $code")
                else -> throw ImportException(ImportFailure.Other, "HTTP $code")
            }
        }
        throw ImportException(ImportFailure.NotFound, "muitos redirecionamentos")
    }

    private fun openFile(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri) ?: throw ImportException(ImportFailure.FileUnreadable)

    private fun IOException.toFailure(kind: SourceKind): ImportFailure = when (this) {
        is UnknownHostException -> if (hasInternet()) ImportFailure.NotFound else ImportFailure.NoInternet
        is ConnectException, is SocketTimeoutException -> if (hasInternet()) ImportFailure.ServerError else ImportFailure.NoInternet
        is FileNotFoundException -> if (kind == SourceKind.File) ImportFailure.FileUnreadable else ImportFailure.NotFound
        is javax.net.ssl.SSLException -> ImportFailure.InvalidCertificate
        is java.net.SocketException, is java.io.EOFException, is java.net.ProtocolException ->
            if (hasInternet()) ImportFailure.ConnectionDropped else ImportFailure.NoInternet
        else -> ImportFailure.Other
    }

    private fun hasInternet(): Boolean {
        val connectivity = appContext.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun sourceAddress(id: Long): Triple<String, SourceKind, String>? =
        database.readableDatabase.rawQuery("SELECT nome, tipo, endereco_cifrado FROM fontes WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return@use null
            Triple(c.getString(0), SourceKind.entries.first { it.key == c.getString(1) }, crypto.decrypt(c.getString(2)))
        }

    private fun refreshSources() {
        _sources.value = database.readableDatabase.rawQuery(
            "SELECT id, nome, tipo, itens, atualizado_em FROM fontes WHERE itens > 0 ORDER BY id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(SourceInfo(c.getLong(0), c.getString(1), SourceKind.entries.first { it.key == c.getString(2) }, c.getInt(3), c.getLong(4)))
                }
            }
        }
    }

    private class ImportException(val reason: ImportFailure, val detail: String? = null) : IOException(reason.name)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        /** Ids dos itens de lista: "f{lista}-{posição}". */
        const val ID_PREFIX = "f"

        fun parseId(id: String): Pair<Long, Long>? {
            if (!id.startsWith(ID_PREFIX)) return null
            val parts = id.removePrefix(ID_PREFIX).split('-')
            return parts.getOrNull(0)?.toLongOrNull()?.let { s -> parts.getOrNull(1)?.toLongOrNull()?.let { p -> s to p } }
        }
    }
}
