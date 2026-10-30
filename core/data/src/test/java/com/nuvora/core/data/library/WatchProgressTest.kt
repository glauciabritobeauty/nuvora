package com.nuvora.core.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    private fun entry(position: Long, duration: Long) =
        HistoryEntry(id = "x", title = "x", imageUrl = null, positionMs = position, durationMs = duration, updatedAt = 0)

    private val minute = 60_000L

    @Test
    fun `quem so deu uma espiada nao aparece em continuar assistindo`() {
        assertFalse(WatchProgress.isUnfinished(entry(position = 1 * minute, duration = 100 * minute)))
    }

    @Test
    fun `quem parou no meio aparece em continuar assistindo`() {
        assertTrue(WatchProgress.isUnfinished(entry(position = 40 * minute, duration = 100 * minute)))
    }

    @Test
    fun `quem terminou nao aparece em continuar assistindo`() {
        assertFalse(WatchProgress.isUnfinished(entry(position = 97 * minute, duration = 100 * minute)))
    }

    @Test
    fun `duracao desconhecida nao quebra e nao aparece`() {
        assertEquals(0f, WatchProgress.fraction(entry(position = 10 * minute, duration = 0)))
        assertFalse(WatchProgress.isUnfinished(entry(position = 10 * minute, duration = 0)))
    }

    @Test
    fun `tempo restante arredonda para cima`() {
        assertEquals("Faltam 20 min", WatchProgress.remainingLabel(entry(position = 30 * minute + 30_000, duration = 50 * minute)))
        assertEquals("Falta 1 min", WatchProgress.remainingLabel(entry(position = 49 * minute + 30_000, duration = 50 * minute)))
    }
}
