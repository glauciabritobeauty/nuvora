package com.nuvora.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nuvora.designsystem.theme.NuvoraTheme

/**
 * Campo de texto com título em cima.
 * - Celular: toque e digite.
 * - TV: passar pelo campo com as setas não abre o teclado; o teclado abre quando a pessoa aperta OK
 *   (e não digita sozinho a primeira letra).
 */
@Composable
fun NuvoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    /** Texto que precisa ficar exatamente como foi digitado (usuário, senha, endereço): sem corretor nem maiúscula automática. */
    exact: Boolean = false,
    /** Na TV, permite colocar o foco do controle neste campo (ex.: primeiro campo de um formulário). */
    tvFocusRequester: FocusRequester? = null,
) {
    val colors = NuvoraTheme.colors
    val isTv = NuvoraTheme.isTv
    val shape = RoundedCornerShape(14.dp)
    var editing by remember { mutableStateOf(!isTv) }
    var focused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (isTv && editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }
    Column(modifier.fillMaxWidth()) {
        NuvoraText(label, NuvoraTheme.typography.label, modifier = Modifier.padding(bottom = 6.dp))
        val field = @Composable { inner: Modifier ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else keyboardType,
                    showKeyboardOnFocus = !isTv,
                    autoCorrectEnabled = if (exact || secret) false else null,
                    capitalization = if (exact || secret) KeyboardCapitalization.None else KeyboardCapitalization.Unspecified,
                ),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = NuvoraTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = inner
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (isTv && !it.isFocused && editing) editing = false
                    }
                    .then(
                        if (isTv) {
                            Modifier
                                .focusProperties { canFocus = editing }
                                // TV: com o teclado fechado, ↑ ↓ saem do campo (nunca deixa a pessoa presa) e OK reabre o teclado.
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionDown, Key.DirectionUp -> {
                                            editing = false
                                            focusManager.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter -> {
                                            keyboard?.show()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
                decorationBox = { text ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (value.isEmpty()) {
                            NuvoraText(placeholder, NuvoraTheme.typography.body, color = colors.textSecondary, maxLines = 1)
                        }
                        text()
                    }
                },
            )
        }
        if (isTv) {
            NuvoraClickable(
                onClick = { editing = true },
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tvFocusRequester != null) Modifier.focusRequester(tvFocusRequester) else Modifier),
                containerColor = if (editing) colors.surfaceRaised else colors.surface,
                focusedContainerColor = colors.surfaceRaised,
                border = BorderStroke(if (editing) 3.dp else 1.dp, if (editing) colors.accent else colors.outline),
            ) { field(Modifier) }
        } else {
            field(
                Modifier
                    .background(colors.surface, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.outline, shape),
            )
        }
    }
}
