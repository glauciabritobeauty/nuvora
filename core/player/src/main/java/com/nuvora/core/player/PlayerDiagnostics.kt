package com.nuvora.core.player

import android.content.Context
import android.util.Log
import com.nuvora.core.data.diagnosticsEnabled

/**
 * Anotações do player no registro do Android, SÓ nas versões de teste (debug e otimizada).
 * O teste automático lê essas anotações para conferir que o vídeo anda, que a conexão caiu e voltou etc.
 * Nunca anota endereços nem senhas. Na versão final não anota nada.
 */
internal class PlayerDiagnostics(context: Context) {
    val enabled = diagnosticsEnabled(context)

    fun log(message: String) {
        if (enabled) Log.i("NuvoraPlayer", message)
    }
}
