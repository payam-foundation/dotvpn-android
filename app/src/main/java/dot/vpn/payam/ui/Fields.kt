// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dot.vpn.payam.R
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType

/** Bordered mono text field for invites, relay links, DNS and bypass entries. Always laid out left to right. */
@Composable
internal fun DotField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onDone: (() -> Unit)? = null,
) {
    val c = DotTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = DotType.mono.copy(color = c.fg, textDirection = TextDirection.Ltr),
        cursorBrush = SolidColor(c.fg),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border, DotRadius)
                        .clip(DotRadius)
                        .background(c.surface)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = DotType.mono, color = c.fgMuted)
                    inner()
                }
            }
        },
    )
}

/** Forward arrow that follows the layout direction. */
@Composable
internal fun DotChevron(modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painterResource(R.drawable.ic_chevron_down),
        contentDescription = null,
        modifier = modifier.rotate(if (rtl) 90f else -90f),
        tint = DotTheme.colors.fgMuted,
    )
}

@Composable
internal fun DotButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = DotRadius,
    ) { Text(text) }
}

@Composable
internal fun DotOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = DotTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = DotRadius,
        border = BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.fg),
    ) { Text(text) }
}
