package com.nuvora.core.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Anotações do player no registro do Android, SÓ na versão de teste (app "debuggable").
 * O teste automático lê essas anotações para conferir que o vídeo anda, que a conexão caiu e voltou etc.
 * Na versão final não anota nada.
 */
internal class PlayerDiagnostics(context: Context) {
    val enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun log(message: String) {
        if (enabled) Log.i("NuvoraPlayer", message)
    }
}
