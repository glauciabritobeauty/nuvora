package com.nuvora.core.data.library

import androidx.compose.runtime.Immutable

/** Item salvo nos favoritos. Título e capa ficam guardados para a lista abrir sem internet. */
@Immutable
data class FavoriteEntry(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val addedAt: Long,
)

/** Item do histórico, com onde a pessoa parou. */
@Immutable
data class HistoryEntry(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** Regras de "Continuar assistindo". */
object WatchProgress {
    /** Abaixo disso a pessoa só deu uma espiada; acima, já terminou. */
    const val MIN_FRACTION = 0.05f
    const val MAX_FRACTION = 0.95f

    fun fraction(entry: HistoryEntry): Float =
        if (entry.durationMs <= 0) 0f else (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)

    fun isUnfinished(entry: HistoryEntry): Boolean = fraction(entry) in MIN_FRACTION..MAX_FRACTION

    fun remainingLabel(entry: HistoryEntry): String {
        val minutes = ((entry.durationMs - entry.positionMs).coerceAtLeast(0) + 59_999) / 60_000
        return if (minutes <= 1) "Falta 1 min" else "Faltam $minutes min"
    }
}
