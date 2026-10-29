package com.nuvora.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Paleta NUVORA, derivada da identidade VEXORA. */
@Immutable
data class NuvoraColors(
    val background: Color = Color(0xFF070B14),
    val surface: Color = Color(0xFF121826),
    val surfaceRaised: Color = Color(0xFF1B2335),
    val outline: Color = Color(0xFF243049),
    val primary: Color = Color(0xFF2F6BFF),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val accent: Color = Color(0xFF22D3EE),
    val violet: Color = Color(0xFF8B5CF6),
    val textPrimary: Color = Color(0xFFF1F5F9),
    val textSecondary: Color = Color(0xFF94A3B8),
    val error: Color = Color(0xFFF87171),
) {
    /** Gradiente da marca (azul elétrico → violeta), usado no logo e em detalhes. */
    val brandBrush: Brush get() = Brush.linearGradient(listOf(primary, violet))
}
