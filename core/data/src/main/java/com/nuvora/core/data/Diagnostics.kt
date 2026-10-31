package com.nuvora.core.data

import android.content.Context

/** Versão de teste? (debug ou otimizada). Só nelas o app anota tempos e eventos no registro do Android. */
fun diagnosticsEnabled(context: Context): Boolean = context.resources.getBoolean(R.bool.nuvora_diagnostico)
