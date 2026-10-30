package com.nuvora.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nuvora.designsystem.theme.NuvoraTheme

data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val style: NuvoraButtonStyle = NuvoraButtonStyle.Secondary,
)

/**
 * Janela de escolha/confirmação. Fecha com Voltar ou tocando fora.
 * O foco começa na última ação (use "Cancelar" por último), para nunca apagar algo sem querer na TV.
 */
@Composable
fun NuvoraDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<DialogAction>,
    message: String? = null,
) {
    val colors = NuvoraTheme.colors
    val shape = NuvoraTheme.dimens.heroShape
    val safeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(colors.surface, shape)
                .border(1.dp, colors.outline, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NuvoraText(title, NuvoraTheme.typography.title, textAlign = TextAlign.Center)
            if (message != null) {
                NuvoraText(message, NuvoraTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
            actions.forEachIndexed { index, action ->
                NuvoraButton(
                    text = action.label,
                    style = action.style,
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 10.dp else 0.dp)
                        .then(if (index == actions.lastIndex) Modifier.focusRequester(safeFocus) else Modifier),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { safeFocus.requestFocus() } }
}
