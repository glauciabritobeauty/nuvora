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
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** false = ainda carregando em segundo plano (a tela de escolha já funciona com o que chegou). */
    val complete: Boolean = true,
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
    ServerNotFound("Não encontramos o servidor do provedor. Confira o endereço no campo Provedor."),
    NotRecognized("O provedor não reconheceu esse usuário e senha. Confira letra por letra (maiúsculas e minúsculas contam) ou peça ao seu fornecedor para conferir se o acesso está ativo."),
    RequestRejected("O provedor não aceitou o pedido do NUVORA. Copie o relatório de conexão e mande para o suporte."),
    Other("Não foi possível ler a lista. Tente de novo."),
}

/** Andamento: primeiro baixa a lista inteira, depois organiza no aparelho. */
sealed interface ImportProgress {
    data class Downloading(val bytes: Long) : ImportProgress
    data class Reading(val items: Int) : ImportProgress
    data class Step(val text: String) : ImportProgress
}

sealed interface ImportResult {
    /** [report]: relatório de conexão (só no Provedor/Usuário/Senha), sem usuário nem senha. */
    val report: ConnectionReport? get() = null

    data class Success(val itemCount: Int, override val report: ConnectionReport? = null) : ImportResult
    /** [detail]: código curto para o suporte (ex.: "HTTP 456"), mostrado pequeno embaixo da mensagem. Nunca contém senha. */
    data class Failure(val reason: ImportFailure, val detail: String? = null, override val report: ConnectionReport? = null) : ImportResult
}

/** O que a Home mostra de todas as listas juntas (só uma amostra por categoria, nunca a lista inteira). */
@Immutable
data class SourceCatalog(
    val hero: List<HeroItem>,
    val categories: List<String>,
    val items: List<CardItem>,
)

private class SourcesDatabase(context: Context) : SQLiteOpenHelper(context, "fontes.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE fontes (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, tipo TEXT NOT NULL, " +
                "endereco_cifrado TEXT NOT NULL, itens INTEGER NOT NULL, atualizado_em INTEGER NOT NULL, " +
                "completa INTEGER NOT NULL DEFAULT 1, conta TEXT)",
        )
        db.execSQL(
            "CREATE TABLE itens (fonte_id INTEGER NOT NULL, posicao INTEGER NOT NULL, titulo TEXT NOT NULL, " +
                "logo TEXT, grupo TEXT, url_cifrada TEXT NOT NULL, tipo TEXT, nota TEXT, PRIMARY KEY (fonte_id, posicao))",
        )
        db.execSQL("CREATE INDEX itens_grupo ON itens (grupo)")
        createSections(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Listas que já estavam no aparelho continuam valendo: ganham a parte (TV/Filmes/Séries) e as categorias.
            db.execSQL("ALTER TABLE fontes ADD COLUMN completa INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE fontes ADD COLUMN conta TEXT")
            db.execSQL("ALTER TABLE itens ADD COLUMN tipo TEXT")
            db.execSQL("ALTER TABLE itens ADD COLUMN nota TEXT")
            db.execSQL("UPDATE itens SET grupo = 'Outros' WHERE grupo IS NULL")
            db.execSQL(
                "UPDATE itens SET tipo = CASE WHEN grupo LIKE 'Filmes · %' THEN 'vod' " +
                    "WHEN grupo LIKE 'Séries · %' THEN 'series' ELSE 'live' END",
            )
            createSections(db)
            db.execSQL(REBUILD_SECTIONS + " GROUP BY fonte_id, tipo, grupo")
        }
    }

    private fun createSections(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE categorias (fonte_id INTEGER NOT NULL, tipo TEXT NOT NULL, cat_id TEXT NOT NULL, nome TEXT NOT NULL, " +
                "grupo TEXT NOT NULL, posicao INTEGER NOT NULL, carregada INTEGER NOT NULL, itens INTEGER NOT NULL, " +
                "PRIMARY KEY (fonte_id, tipo, cat_id))",
        )
        db.execSQL("CREATE INDEX itens_parte ON itens (fonte_id, tipo, grupo)")
    }

    companion object {
        /** Monta as categorias a partir dos itens já gravados (listas por link/arquivo e plano B). */
        const val REBUILD_SECTIONS =
            "INSERT OR REPLACE INTO categorias (fonte_id, tipo, cat_id, nome, grupo, posicao, carregada, itens) " +
                "SELECT fonte_id, tipo, grupo, CASE WHEN instr(grupo, ' · ') > 0 THEN substr(grupo, instr(grupo, ' · ') + 3) ELSE grupo END, " +
                "grupo, MIN(posicao), 1, COUNT(*) FROM itens"
    }
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

    private val prefs = appContext.getSharedPreferences("fontes", Context.MODE_PRIVATE)
    private val _savedServer = MutableStateFlow<String?>(null)

    /** Servidor (DNS) do último login que deu certo: na próxima vez, basta usuário e senha. Guardado cifrado. */
    val savedServer: StateFlow<String?> = _savedServer.asStateFlow()

    private val _loading = MutableStateFlow<LoadProgress?>(null)

    /** Andamento do carregamento em segundo plano (null = nada carregando). */
    val loading: StateFlow<LoadProgress?> = _loading.asStateFlow()

    private val _changes = MutableStateFlow(0L)

    /** Muda toda vez que chegam itens novos (a tela de escolha e as categorias se atualizam). */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Uma categoria por vez: a que a pessoa abriu passa na frente do carregamento em segundo plano. */
    private val categoryLock = Mutex()

    suspend fun load() = io {
        refreshSources()
        _savedServer.value = prefs.getString("servidor_cifrado", null)?.let { runCatching { crypto.decrypt(it) }.getOrNull() }
    }

    suspend fun addLink(name: String, url: String, onProgress: (ImportProgress) -> Unit = {}): ImportResult = io {
        importFrom(sourceId = null, name = name, kind = SourceKind.Link, address = url.trim(), onProgress = onProgress) { emit ->
            m3u(downloadToCache(url.trim(), onProgress), emit)
        }
    }

    /** Provedor / Usuário / Senha: confere o acesso e busca canais, filmes e séries. */
    suspend fun addProvider(name: String, login: ProviderLogin, onProgress: (ImportProgress) -> Unit = {}): ImportResult = io {
        // Teclados de celular costumam pôr um espaço no fim: usuário e senha com espaço nunca conferem.
        val typed = login.copy(user = login.user.trim(), password = login.password.trim())
        val report = ReportBuilder(typed.user, typed.password, reportHeader())
        val clean = runCatching { typed.copy(server = normalizeServer(typed.server)) }.getOrElse {
            report.add("Endereço do provedor", null, "não é um endereço de internet válido", false)
            report.problem = ProblemKind.RequestFormat
            report.conclusion = "O texto do campo Provedor não é um endereço (ex.: http://exemplo.com:8080)."
            return@io ImportResult.Failure(ImportFailure.NotAServer, "endereço inválido", report.build())
        }
        report.add("Endereço do provedor", clean.server, "entendido como ${report.maskUrl(clean.server)}", true)
        val providerName = name.trim().ifEmpty { runCatching { java.net.URI(clean.server).host }.getOrNull().orEmpty() }
        connectProvider(sourceId = null, name = providerName, login = clean, report = report, onProgress = onProgress).also { result ->
            if (result is ImportResult.Success) {
                prefs.edit().putString("servidor_cifrado", crypto.encrypt(clean.server)).apply()
                _savedServer.value = clean.server
            }
        }
    }

    private fun reportHeader(): String {
        val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull()
        val tv = appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return "NUVORA ${version ?: "?"} · Android ${android.os.Build.VERSION.RELEASE} · ${if (tv) "TV" else "celular/tablet"} · " +
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
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
        if (kind == SourceKind.Provider) {
            val login = decodeLogin(address)
            return@io connectProvider(id, name, login, ReportBuilder(login.user, login.password, reportHeader())) {}
        }
        importFrom(sourceId = id, name = name, kind = kind, address = address) { emit ->
            when (kind) {
                SourceKind.Link -> m3u(downloadToCache(address) {}, emit)
                else -> m3u(openFile(Uri.parse(address)), emit)
            }
        }
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) { categoryLock.withLock { removeLocked(id) } }

    private fun removeLocked(id: Long) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("itens", "fonte_id = ?", arrayOf(id.toString()))
            db.delete("categorias", "fonte_id = ?", arrayOf(id.toString()))
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
        database.readableDatabase.rawQuery("SELECT fonte_id, posicao, titulo, logo, grupo, nota FROM itens $where", args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CardItem(
                            id = "$ID_PREFIX${c.getLong(0)}-${c.getLong(1)}",
                            title = c.getString(2),
                            imageUrl = c.getString(3),
                            category = c.getString(4),
                            caption = c.getString(4),
                            rating = c.getString(5),
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
        report: ReportBuilder? = null,
        produce: (emit: (M3uEntry) -> Unit) -> M3uSummary,
    ): ImportResult {
        val result = importInto(sourceId, name, kind, address, onProgress, produce)
        return if (report == null) result else finishReport(report, result)
    }

    private fun importInto(
        sourceId: Long?,
        name: String,
        kind: SourceKind,
        address: String,
        onProgress: (ImportProgress) -> Unit,
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
            if (sourceId != null) {
                db.delete("itens", "fonte_id = ?", arrayOf(sourceId.toString()))
                db.delete("categorias", "fonte_id = ?", arrayOf(sourceId.toString()))
            }
            val insert = itemInsert(db)
            var position = 0L
            val summary = produce { entry ->
                insertItem(insert, id, position++, entry, SectionKind.guess(entry.url, entry.group))
                if (position % 2_000 == 0L) onProgress(ImportProgress.Reading(position.toInt()))
            }
            if (summary.entries == 0) {
                return ImportResult.Failure(if (summary.looksLikeM3u) ImportFailure.Empty else ImportFailure.NotAList)
            }
            db.execSQL(SourcesDatabase.REBUILD_SECTIONS + " WHERE fonte_id = ? GROUP BY fonte_id, tipo, grupo", arrayOf<Any>(id))
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
        } catch (e: Exception) {
            return failureOf(e, kind)
        } finally {
            db.endTransaction()
            refreshSources()
        }
    }

    /** Explica qualquer falha em linguagem de gente (nunca derruba o app por causa de uma lista estranha). */
    private fun failureOf(e: Exception, kind: SourceKind): ImportResult.Failure = when (e) {
        is ImportException -> ImportResult.Failure(e.reason, e.detail)
        is XtreamException -> ImportResult.Failure(e.reason, e.detail)
        is IOException -> ImportResult.Failure(e.toFailure(kind), e.javaClass.simpleName)
        is SecurityException -> ImportResult.Failure(ImportFailure.FileUnreadable, e.javaClass.simpleName)
        else -> ImportResult.Failure(ImportFailure.Other, e.javaClass.simpleName)
    }

    private fun itemInsert(db: SQLiteDatabase) = db.compileStatement(
        "INSERT INTO itens (fonte_id, posicao, titulo, logo, grupo, url_cifrada, tipo, nota) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    )

    private fun insertItem(insert: android.database.sqlite.SQLiteStatement, sourceId: Long, position: Long, entry: M3uEntry, kind: SectionKind) {
        insert.clearBindings()
        insert.bindLong(1, sourceId)
        insert.bindLong(2, position)
        insert.bindString(3, entry.title)
        entry.logo?.let { insert.bindString(4, it) }
        insert.bindString(5, entry.group?.trim()?.ifEmpty { null } ?: "Outros")
        insert.bindString(6, crypto.encrypt(entry.url))
        insert.bindString(7, kind.key)
        entry.rating?.let { insert.bindString(8, it) }
        insert.executeInsert()
    }

    private fun m3u(input: InputStream, emit: (M3uEntry) -> Unit): M3uSummary =
        input.use { parseM3u(InputStreamReader(it, Charsets.UTF_8), emit) }

    /**
     * Provedor: confere usuário e senha e guarda só as categorias (pedidos pequenos, poucos segundos).
     * Os canais, filmes e séries chegam depois: a categoria que a pessoa abrir vem na hora, e o resto
     * em segundo plano ([loadPending]). Se o provedor só entregar a lista completa, ela é lida aos poucos.
     */
    private fun connectProvider(
        sourceId: Long?,
        name: String,
        login: ProviderLogin,
        report: ReportBuilder,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult {
        val started = SystemClock.elapsedRealtime()
        val client = XtreamClient(login, report, ::hasInternet) { url -> downloadToCache(url) {} }
        val id: Long
        val categoryCount: Int
        val api: Boolean
        try {
            onProgress(ImportProgress.Step("Conferindo usuário e senha..."))
            val access = client.connect()
            if (debug) Log.i(TAG, "medida login ${SystemClock.elapsedRealtime() - started} ms")
            val account = JSONObject().put("fmt", client.liveFormat)
            client.account?.let { info ->
                info.expDate?.let { account.put("exp", it) }
                info.maxConnections?.let { account.put("max", it) }
            }
            val sections = LinkedHashMap<SectionKind, List<Pair<String, String>>>()
            api = access == ProviderAccess.Api
            if (access is ProviderAccess.FullList) {
                account.put("modo", "lista").put("variante", access.variant)
            } else {
                account.put("modo", "api")
                onProgress(ImportProgress.Step("Buscando as categorias..."))
                SectionKind.entries.forEach { sections[it] = client.categories(it) }
                if (sections.values.all { it.isEmpty() }) throw ImportException(ImportFailure.Empty)
            }
            categoryCount = sections.values.sumOf { it.size }
            id = saveProvider(sourceId, name, login, account, sections)
        } catch (e: Exception) {
            return finishReport(report, failureOf(e, SourceKind.Provider))
        }
        if (api) {
            // A 1ª categoria da TV já vem agora (abre na hora) e o relatório diz se o provedor aceita pedir por categoria.
            firstCategory(id)?.let { (kind, categoryId) ->
                val url = "player_api.php?action=get_${kind.key}_streams&category_id=$categoryId"
                val mark = SystemClock.elapsedRealtime()
                val text = runCatching { loadCategoryLocked(id, kind, categoryId, client) }.fold(
                    onSuccess = { load ->
                        if (load.filtered) {
                            "aceita: a 1ª categoria trouxe ${load.items} itens em ${SystemClock.elapsedRealtime() - mark} ms (carrega por categoria)"
                        } else {
                            "não filtra: devolveu todos os ${load.items} itens (lê a parte inteira de uma vez, em segundo plano)"
                        }
                    },
                    onFailure = { "não respondeu: ${it.javaClass.simpleName} (as categorias carregam depois)" },
                )
                report.add("Recursos do provedor: pedir canais de uma categoria só", null, "$url → $text", text.startsWith("aceita"))
            }
        }
        refreshSources()
        _changes.value++
        if (debug) Log.i(TAG, "medida entrada $categoryCount categorias ${SystemClock.elapsedRealtime() - started} ms")
        report.conclusion = if (api) {
            "Tudo certo: entrou e encontrou $categoryCount categorias. Os canais, filmes e séries chegam aos poucos, sem esperar."
        } else {
            "Tudo certo: usuário e senha aceitos. Este provedor só entrega a lista completa, que está sendo lida aos poucos."
        }
        return finishReport(report, ImportResult.Success(categoryCount))
    }

    /** Guarda o provedor (cifrado) e as categorias, ainda sem itens. Devolve o número da lista. */
    private fun saveProvider(
        sourceId: Long?,
        name: String,
        login: ProviderLogin,
        account: JSONObject,
        sections: Map<SectionKind, List<Pair<String, String>>>,
    ): Long {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("nome", name.trim().ifEmpty { "Minha lista" })
                put("tipo", SourceKind.Provider.key)
                put("endereco_cifrado", crypto.encrypt(encodeLogin(login)))
                put("itens", 0)
                put("atualizado_em", System.currentTimeMillis())
                put("completa", 0)
                put("conta", account.toString())
            }
            val id = if (sourceId == null) {
                db.insert("fontes", null, values)
            } else {
                db.delete("itens", "fonte_id = ?", arrayOf(sourceId.toString()))
                db.delete("categorias", "fonte_id = ?", arrayOf(sourceId.toString()))
                db.update("fontes", values, "id = ?", arrayOf(sourceId.toString()))
                sourceId
            }
            sections.forEach { (kind, list) ->
                list.forEachIndexed { index, (categoryId, categoryName) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categorias (fonte_id, tipo, cat_id, nome, grupo, posicao, carregada, itens) VALUES (?, ?, ?, ?, ?, ?, 0, 0)",
                        arrayOf<Any>(id, kind.key, categoryId, categoryName, "${kind.groupPrefix} · $categoryName", index),
                    )
                }
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** Fecha o relatório de conexão (conclusão e tipo do problema) e o anexa ao resultado. */
    private fun finishReport(report: ReportBuilder, result: ImportResult): ImportResult {
        if (result is ImportResult.Success) {
            report.problem = null
            if (report.conclusion.isEmpty()) report.conclusion = "Tudo certo: ${result.itemCount} itens."
        } else if (report.problem == null) {
            report.problem = if (report.loginOk) ProblemKind.ListLoading else when ((result as ImportResult.Failure).reason) {
                ImportFailure.NoInternet, ImportFailure.ConnectionDropped, ImportFailure.InvalidCertificate, ImportFailure.ServerNotFound -> ProblemKind.Network
                else -> ProblemKind.ServerResponse
            }
            report.conclusion = (result as ImportResult.Failure).reason.message
        }
        val built = report.build()
        if (debug) Log.i(TAG, "relatorio de conexao:\n${built.asText()}")
        return when (result) {
            is ImportResult.Success -> result.copy(report = built)
            is ImportResult.Failure -> result.copy(report = built)
        }
    }

    // ---- Tela de escolha, categorias e carregamento aos poucos ----

    /** O que a tela de escolha mostra: conta, as três partes e se tudo já chegou. Só contagens (rápido). */
    suspend fun hub(): HubData = io {
        val db = database.readableDatabase
        val sections = SectionKind.entries.map { kind ->
            db.rawQuery(
                "SELECT COUNT(*), IFNULL(SUM(itens), 0) FROM categorias WHERE tipo = ? AND NOT (carregada = 1 AND itens = 0)",
                arrayOf(kind.key),
            ).use { c -> c.moveToFirst(); SectionSummary(kind, c.getInt(0), c.getInt(1)) }
        }
        val total = db.rawQuery("SELECT COUNT(*) FROM itens", null).use { c -> c.moveToFirst(); c.getInt(0) }
        val complete = db.rawQuery("SELECT COUNT(*) FROM fontes WHERE completa = 0", null).use { c -> c.moveToFirst(); c.getInt(0) == 0 }
        val account = db.rawQuery(
            "SELECT nome, tipo, endereco_cifrado, conta FROM fontes ORDER BY CASE tipo WHEN 'servidor' THEN 0 ELSE 1 END, id LIMIT 1",
            null,
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            val provider = c.getString(1) == SourceKind.Provider.key
            val info = runCatching { JSONObject(c.getString(3) ?: "{}") }.getOrDefault(JSONObject())
            AccountInfo(
                sourceName = c.getString(0),
                user = if (provider) runCatching { decodeLogin(crypto.decrypt(c.getString(2))).user }.getOrNull() else null,
                expiresAt = info.optLong("exp", 0L).takeIf { it > 0 },
                screens = info.optInt("max", 0).takeIf { it > 0 },
            )
        }
        HubData(account, sections, total, complete)
    }

    /** Todos os itens de uma parte que já estão no aparelho ("Todos os canais"). */
    suspend fun sectionItems(kind: SectionKind, limit: Int = 5_000): List<CardItem> = io {
        query("WHERE tipo = ? ORDER BY fonte_id, posicao LIMIT ?", kind.key, limit.toString())
    }

    /** Busca só dentro de uma parte (a busca do topo da tela de TV ao vivo, Filmes ou Séries). */
    suspend fun searchIn(kind: SectionKind, text: String, limit: Int = 200): List<CardItem> = io {
        val escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (escaped.isEmpty()) emptyList()
        else query("WHERE tipo = ? AND titulo LIKE ? ESCAPE '\\' ORDER BY fonte_id, posicao LIMIT ?", kind.key, "%$escaped%", limit.toString())
    }

    /** Dos favoritos ([ids]), os que são desta parte. */
    suspend fun favoritesIn(kind: SectionKind, ids: Collection<String>): List<CardItem> = io {
        ids.mapNotNull { id ->
            val (source, position) = parseId(id) ?: return@mapNotNull null
            query("WHERE fonte_id = ? AND posicao = ? AND tipo = ?", source.toString(), position.toString(), kind.key).firstOrNull()
        }
    }

    /** Categorias de uma parte (TV ao vivo, Filmes ou Séries), de todas as listas. */
    suspend fun categories(kind: SectionKind): List<CategoryRef> = io {
        database.readableDatabase.rawQuery(
            "SELECT fonte_id, cat_id, nome, carregada, itens FROM categorias WHERE tipo = ? AND NOT (carregada = 1 AND itens = 0) " +
                "ORDER BY fonte_id, posicao",
            arrayOf(kind.key),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(CategoryRef(c.getLong(0), kind, c.getString(1), c.getString(2), c.getInt(3) == 1, c.getInt(4)))
            }
        }
    }

    /** Itens de uma categoria. Se ainda não chegaram, busca agora (passando na frente do segundo plano). */
    suspend fun categoryItems(ref: CategoryRef): Result<List<CardItem>> = withContext(Dispatchers.IO) {
        runCatching {
            categoryLock.withLock {
                val pending = database.readableDatabase.rawQuery(
                    "SELECT carregada FROM categorias WHERE fonte_id = ? AND tipo = ? AND cat_id = ?",
                    arrayOf(ref.sourceId.toString(), ref.kind.key, ref.id),
                ).use { c -> c.moveToFirst() && c.getInt(0) == 0 }
                if (pending) providerClient(ref.sourceId)?.let { loadCategoryLocked(ref.sourceId, ref.kind, ref.id, it) }
            }
            val group = database.readableDatabase.rawQuery(
                "SELECT grupo FROM categorias WHERE fonte_id = ? AND tipo = ? AND cat_id = ?",
                arrayOf(ref.sourceId.toString(), ref.kind.key, ref.id),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return@runCatching emptyList()
            query("WHERE fonte_id = ? AND tipo = ? AND grupo = ? ORDER BY posicao", ref.sourceId.toString(), ref.kind.key, group)
        }
    }

    /**
     * Carrega em segundo plano tudo o que ainda falta das listas de provedor: categoria por categoria
     * (TV primeiro, depois filmes e séries) ou, se o provedor só entrega a lista completa, lendo-a aos poucos.
     * Pode ser interrompido a qualquer momento (sair da conta, fechar o app): continua de onde parou depois.
     */
    suspend fun loadPending() = withContext(Dispatchers.IO) {
        val pending = database.readableDatabase.rawQuery(
            "SELECT id, endereco_cifrado, conta FROM fontes WHERE completa = 0 AND tipo = ? ORDER BY id",
            arrayOf(SourceKind.Provider.key),
        ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }
        for ((id, address, accountText) in pending) {
            val account = runCatching { JSONObject(accountText ?: "{}") }.getOrDefault(JSONObject())
            val login = runCatching { decodeLogin(crypto.decrypt(address)) }.getOrNull() ?: continue
            val ok = if (account.optString("modo") == "lista") {
                loadFullList(id, login, account.optString("variante", XtreamClient.LIST_VARIANTS.first()))
            } else {
                loadAllCategories(id)
            }
            if (!ok) {
                _loading.value = _loading.value?.copy(failed = true) ?: LoadProgress(0, 0, 0, failed = true)
                return@withContext
            }
            database.writableDatabase.execSQL("UPDATE fontes SET completa = 1, atualizado_em = ? WHERE id = ?", arrayOf<Any>(System.currentTimeMillis(), id))
            if (debug) Log.i(TAG, "segundo plano: lista $id completa")
        }
        _loading.value = null
        refreshSources()
        _changes.value++
    }

    private suspend fun loadAllCategories(id: Long): Boolean {
        val client = providerClient(id) ?: return false
        var failures = 0
        while (true) {
            kotlinx.coroutines.yield()
            val db = database.readableDatabase
            val (done, total) = db.rawQuery("SELECT SUM(carregada), COUNT(*) FROM categorias WHERE fonte_id = ?", arrayOf(id.toString()))
                .use { c -> c.moveToFirst(); c.getInt(0) to c.getInt(1) }
            _loading.value = LoadProgress(done, total, itemCount(id))
            val next = db.rawQuery(
                "SELECT tipo, cat_id FROM categorias WHERE fonte_id = ? AND carregada = 0 " +
                    "ORDER BY CASE tipo WHEN 'live' THEN 0 WHEN 'vod' THEN 1 ELSE 2 END, posicao LIMIT 1",
                arrayOf(id.toString()),
            ).use { c -> if (c.moveToFirst()) SectionKind.of(c.getString(0)) to c.getString(1) else null } ?: return true
            try {
                categoryLock.withLock { loadCategoryLocked(id, next.first, next.second, client) }
                failures = 0
            } catch (e: IOException) {
                if (debug) Log.i(TAG, "segundo plano: categoria falhou (${e.javaClass.simpleName})")
                if (++failures >= 3) return false
                kotlinx.coroutines.delay(3_000L * failures)
            }
        }
    }

    /**
     * Plano B: lê a lista completa direto da conexão, gravando de 2.000 em 2.000 itens.
     * A tela de escolha já mostra cada lote que chega; se a conexão cair, recomeça (até 3 vezes).
     */
    private suspend fun loadFullList(id: Long, login: ProviderLogin, variant: String): Boolean {
        val url = XtreamClient(login) { downloadToCache(it) {} }.fullListUrl(variant)
        for (attempt in 1..3) {
            kotlinx.coroutines.yield()
            val db = database.writableDatabase
            try {
                categoryLock.withLock {
                    db.delete("itens", "fonte_id = ?", arrayOf(id.toString()))
                    db.delete("categorias", "fonte_id = ?", arrayOf(id.toString()))
                }
                var count = 0
                openUrl(url).use { input ->
                    val insert = itemInsert(db)
                    db.beginTransaction()
                    try {
                        parseM3u(InputStreamReader(input, Charsets.UTF_8)) { entry ->
                            insertItem(insert, id, count.toLong(), entry, SectionKind.guess(entry.url, entry.group))
                            if (++count % 2_000 == 0) {
                                commitBatch(db, id)
                                _loading.value = LoadProgress(0, 0, count)
                                db.beginTransaction()
                            }
                        }
                        commitBatch(db, id)
                    } finally {
                        if (db.inTransaction()) db.endTransaction()
                    }
                }
                if (debug) Log.i(TAG, "segundo plano: lista completa com $count itens")
                return count > 0
            } catch (e: IOException) {
                if (debug) Log.i(TAG, "segundo plano: lista caiu na tentativa $attempt (${e.javaClass.simpleName})")
                kotlinx.coroutines.delay(2_000L * attempt)
            }
        }
        return false
    }

    /** Grava o lote, refaz as categorias da lista e avisa as telas. */
    private fun commitBatch(db: SQLiteDatabase, id: Long) {
        db.execSQL(SourcesDatabase.REBUILD_SECTIONS + " WHERE fonte_id = ? GROUP BY fonte_id, tipo, grupo", arrayOf<Any>(id))
        db.execSQL("UPDATE fontes SET itens = (SELECT COUNT(*) FROM itens WHERE fonte_id = ?) WHERE id = ?", arrayOf<Any>(id, id))
        db.setTransactionSuccessful()
        db.endTransaction()
        _changes.value++
    }

    private class CategoryLoad(val items: Int, val filtered: Boolean)

    /**
     * Busca os itens de uma categoria e grava. Se o provedor ignorar o filtro e mandar a parte inteira,
     * aproveita tudo (cada item na sua categoria) e marca a parte toda como carregada.
     * Precisa ser chamado com [categoryLock] (ou antes de a lista aparecer para a pessoa).
     */
    private fun loadCategoryLocked(sourceId: Long, kind: SectionKind, categoryId: String, client: XtreamClient): CategoryLoad {
        val db = database.writableDatabase
        val known = HashMap<String, Pair<String, Boolean>>()
        db.rawQuery("SELECT cat_id, grupo, carregada FROM categorias WHERE fonte_id = ? AND tipo = ?", arrayOf(sourceId.toString(), kind.key))
            .use { c -> while (c.moveToNext()) known[c.getString(0)] = c.getString(1) to (c.getInt(2) == 1) }
        if (known[categoryId]?.second == true) return CategoryLoad(0, true)
        val received = ArrayList<Pair<String?, M3uEntry>>()
        client.streams(kind, categoryId) { category, entry -> received += category to entry }
        val filtered = received.all { it.first == null || it.first == categoryId }
        db.beginTransaction()
        try {
            var position = db.rawQuery("SELECT IFNULL(MAX(posicao), -1) + 1 FROM itens WHERE fonte_id = ?", arrayOf(sourceId.toString()))
                .use { c -> c.moveToFirst(); c.getLong(0) }
            val insert = itemInsert(db)
            val counts = HashMap<String, Int>()
            for ((category, entry) in received) {
                val target = if (filtered) categoryId else category ?: OTHERS_ID
                if (known[target]?.second == true) continue
                val group = known[target]?.first ?: "${kind.groupPrefix} · Outros".also { group ->
                    // Item de uma categoria que não veio na lista de categorias: entra em "Outros".
                    db.execSQL(
                        "INSERT OR IGNORE INTO categorias (fonte_id, tipo, cat_id, nome, grupo, posicao, carregada, itens) VALUES (?, ?, ?, 'Outros', ?, 999999, 0, 0)",
                        arrayOf<Any>(sourceId, kind.key, target, group),
                    )
                    known[target] = group to false
                }
                insertItem(insert, sourceId, position++, entry.copy(group = group), kind)
                counts[target] = (counts[target] ?: 0) + 1
            }
            val loadedNow = if (filtered) listOf(categoryId) else known.filterValues { !it.second }.keys.toList()
            loadedNow.forEach { category ->
                db.execSQL(
                    "UPDATE categorias SET carregada = 1, itens = itens + ? WHERE fonte_id = ? AND tipo = ? AND cat_id = ?",
                    arrayOf<Any>(counts[category] ?: 0, sourceId, kind.key, category),
                )
            }
            db.execSQL("UPDATE fontes SET itens = (SELECT COUNT(*) FROM itens WHERE fonte_id = ?) WHERE id = ?", arrayOf<Any>(sourceId, sourceId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _changes.value++
        return CategoryLoad(received.size, filtered)
    }

    /** A 1ª categoria a carregar (TV primeiro). */
    private fun firstCategory(id: Long): Pair<SectionKind, String>? = database.readableDatabase.rawQuery(
        "SELECT tipo, cat_id FROM categorias WHERE fonte_id = ? ORDER BY CASE tipo WHEN 'live' THEN 0 WHEN 'vod' THEN 1 ELSE 2 END, posicao LIMIT 1",
        arrayOf(id.toString()),
    ).use { c -> if (c.moveToFirst()) SectionKind.of(c.getString(0)) to c.getString(1) else null }

    private fun itemCount(id: Long) =
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM itens WHERE fonte_id = ?", arrayOf(id.toString())).use { c -> c.moveToFirst(); c.getInt(0) }

    /** Conversa com o provedor desta lista (null se a lista não for de provedor). */
    private fun providerClient(id: Long): XtreamClient? {
        val (address, accountText) = database.readableDatabase.rawQuery(
            "SELECT endereco_cifrado, conta FROM fontes WHERE id = ? AND tipo = ?",
            arrayOf(id.toString(), SourceKind.Provider.key),
        ).use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null } ?: return null
        val login = runCatching { decodeLogin(crypto.decrypt(address)) }.getOrNull() ?: return null
        return XtreamClient(login, null, ::hasInternet) { url -> downloadToCache(url) {} }.apply {
            liveFormat = runCatching { JSONObject(accountText ?: "{}").optString("fmt", "m3u8") }.getOrDefault("m3u8")
        }
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
        val file = java.io.File.createTempFile("lista-", ".tmp", appContext.cacheDir)
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
                // O arquivo some sozinho quando a leitura termina.
                return object : java.io.FileInputStream(file) {
                    override fun close() {
                        super.close()
                        file.delete()
                    }
                }
            } catch (definitive: ImportException) {
                throw definitive // respostas claras do provedor (404, 403, limite...) não adiantam repetir
            } catch (e: IOException) {
                lastError = e
                if (debug) Log.i("NuvoraFontes", "conexão caiu na tentativa $attempt: ${e.javaClass.simpleName}")
                Thread.sleep(2_000L * attempt)
            }
        }
        file.delete()
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
            "SELECT id, nome, tipo, itens, atualizado_em, completa FROM fontes WHERE itens > 0 OR completa = 0 ORDER BY id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(SourceInfo(c.getLong(0), c.getString(1), SourceKind.entries.first { it.key == c.getString(2) }, c.getInt(3), c.getLong(4), c.getInt(5) == 1))
                }
            }
        }
    }

    private class ImportException(val reason: ImportFailure, override val detail: String? = null) : IOException(reason.name), DetailedFailure

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        /** Ids dos itens de lista: "f{lista}-{posição}". */
        const val ID_PREFIX = "f"

        private const val TAG = "NuvoraFontes"

        /** Categoria dos itens que o provedor manda sem categoria conhecida. */
        private const val OTHERS_ID = "outros"

        fun parseId(id: String): Pair<Long, Long>? {
            if (!id.startsWith(ID_PREFIX)) return null
            val parts = id.removePrefix(ID_PREFIX).split('-')
            return parts.getOrNull(0)?.toLongOrNull()?.let { s -> parts.getOrNull(1)?.toLongOrNull()?.let { p -> s to p } }
        }
    }
}
