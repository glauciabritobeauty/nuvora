package com.nuvora.core.data.settings

import android.content.Context
import com.nuvora.core.data.model.SubtitleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes simples da pessoa, guardados só no aparelho (arquivo de preferências do próprio Android). */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _subtitleMode = MutableStateFlow(
        prefs.getString(KEY_SUBTITLES, null)?.let { saved -> SubtitleMode.entries.firstOrNull { it.name == saved } }
            ?: SubtitleMode.Auto,
    )
    val subtitleMode: StateFlow<SubtitleMode> = _subtitleMode.asStateFlow()

    fun setSubtitleMode(mode: SubtitleMode) {
        prefs.edit().putString(KEY_SUBTITLES, mode.name).apply()
        _subtitleMode.value = mode
    }

    private companion object {
        const val KEY_SUBTITLES = "legendas"
    }
}
