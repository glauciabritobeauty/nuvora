package com.nuvora.core.data.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Banco local com favoritos e histórico, usando o SQLite que já vem no Android
 * (sem biblioteca extra). Fica só no aparelho; nada é enviado para fora.
 */
private class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, "biblioteca.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favoritos (id TEXT PRIMARY KEY, titulo TEXT NOT NULL, imagem TEXT, adicionado_em INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE historico (id TEXT PRIMARY KEY, titulo TEXT NOT NULL, imagem TEXT, " +
                "posicao_ms INTEGER NOT NULL, duracao_ms INTEGER NOT NULL, atualizado_em INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/** Favoritos e histórico. Toda leitura e escrita acontece fora da tela (sem travar a rolagem). */
class LibraryRepository(context: Context) {
    private val database = LibraryDatabase(context.applicationContext)

    private val _loaded = MutableStateFlow(false)
    private val _favorites = MutableStateFlow<List<FavoriteEntry>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val favorites: StateFlow<List<FavoriteEntry>> = _favorites.asStateFlow()

    /** Do mais recente para o mais antigo. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    suspend fun load() = io {
        refresh()
        _loaded.value = true
    }

    suspend fun toggleFavorite(id: String, title: String, imageUrl: String?) = io {
        val db = database.writableDatabase
        val removed = db.delete("favoritos", "id = ?", arrayOf(id))
        if (removed == 0) {
            db.insert(
                "favoritos",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("titulo", title)
                    put("imagem", imageUrl)
                    put("adicionado_em", System.currentTimeMillis())
                },
            )
        }
        refresh()
    }

    suspend fun removeFavorite(id: String) = io {
        database.writableDatabase.delete("favoritos", "id = ?", arrayOf(id))
        refresh()
    }

    /** Grava onde a pessoa parou (o player chama isso a partir da Etapa 6). */
    suspend fun saveProgress(entries: List<HistoryEntry>) = io {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                db.insertWithOnConflict(
                    "historico",
                    null,
                    ContentValues().apply {
                        put("id", entry.id)
                        put("titulo", entry.title)
                        put("imagem", entry.imageUrl)
                        put("posicao_ms", entry.positionMs)
                        put("duracao_ms", entry.durationMs)
                        put("atualizado_em", entry.updatedAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    suspend fun removeFromHistory(id: String) = io {
        database.writableDatabase.delete("historico", "id = ?", arrayOf(id))
        refresh()
    }

    /** Apaga de verdade todo o histórico deste aparelho. */
    suspend fun clearHistory() = io {
        database.writableDatabase.delete("historico", null, null)
        refresh()
    }

    private fun refresh() {
        val db = database.readableDatabase
        _favorites.value = db.rawQuery(
            "SELECT id, titulo, imagem, adicionado_em FROM favoritos ORDER BY adicionado_em DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(FavoriteEntry(c.getString(0), c.getString(1), c.getStringOrNull(2), c.getLong(3)))
                }
            }
        }
        _history.value = db.rawQuery(
            "SELECT id, titulo, imagem, posicao_ms, duracao_ms, atualizado_em FROM historico ORDER BY atualizado_em DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(HistoryEntry(c.getString(0), c.getString(1), c.getStringOrNull(2), c.getLong(3), c.getLong(4), c.getLong(5)))
                }
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
