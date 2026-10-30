package com.nuvora.core.data.model

/** Como as legendas começam em cada vídeo. A pessoa sempre pode mudar no próprio vídeo. */
enum class SubtitleMode(val label: String, val description: String) {
    Auto("Automático", "Legenda em português aparece quando o áudio não está em português."),
    Always("Sempre ligadas", "Aparecem em todo vídeo que tiver legenda (de preferência em português e a legenda para surdos)."),
    Off("Desligadas", "Só aparecem se você ligar no vídeo."),
}
