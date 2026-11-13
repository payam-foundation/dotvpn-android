package dot.vpn.payam.ui

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType

internal val DotRadius = RoundedCornerShape(12.dp)
internal val DotPill = RoundedCornerShape(999.dp)

/** ISO 3166 alpha-2 normalised to upper case, or "" when cc is not a valid code. */
internal fun flagCode(cc: String?): String {
    val a = cc.orEmpty().trim().uppercase()
    return if (a.length == 2 && a.all { it in 'A'..'Z' }) a else ""
}

/** Regional-indicator emoji for a valid ISO code, else "". */
internal fun flagEmoji(cc: String?): String {
    val a = flagCode(cc)
    if (a.isEmpty()) return ""
    return a.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

@Composable
internal fun Flag(cc: String?, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val code = flagCode(cc)
    val emoji = remember(code) {
        flagEmoji(code).takeIf { it.isNotEmpty() && Paint().hasGlyph(it) }.orEmpty()
    }
    Box(modifier.width(36.dp), contentAlignment = Alignment.Center) {
        when {
            emoji.isNotEmpty() -> Text(emoji, fontSize = 20.sp)
            code.isNotEmpty() -> Text(
                code,
                style = DotType.mono.copy(fontSize = 11.sp),
                color = c.fgMuted,
                modifier = Modifier
                    .border(1.dp, c.border, DotPill)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            else -> Text("·", fontSize = 20.sp, color = c.fgMuted)
        }
    }
}

@Composable
internal fun ScreenHeading(icon: Int, title: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = DotTheme.colors.fg)
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DotCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DotTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, c.border, DotRadius)
            .clip(DotRadius)
            .background(c.surface)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun DotRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = DotTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, c.border, DotRadius)
            .clip(DotRadius)
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun DotToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val c = DotTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.bg,
            checkedTrackColor = c.fg,
            uncheckedThumbColor = c.fgMuted,
            uncheckedTrackColor = c.surface2,
        ),
    )
}

@Composable
internal fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface2, DotRadius)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(DotRadius)
                    .background(if (on) c.bg else Color.Transparent)
                    .border(1.dp, if (on) c.border else Color.Transparent, DotRadius)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                    color = if (on) c.fg else c.fgMuted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ProtocolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.colors
    Text(
        label,
        style = DotType.mono.copy(fontSize = 13.sp),
        color = if (selected) c.fg else c.fgMuted,
        modifier = modifier
            .border(1.dp, if (selected) c.fg else c.border, DotPill)
            .clip(DotPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Ink on lime — same as desktop `.badge.safe` (`#1a2e05` on `--dot-signal`). */
private val SignalOnInk = Color(0xFF1A2E05)

@Composable
internal fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.colors
    val (bg, fg) = when (tone) {
        // Filled lime always needs dark ink text (light + dark). signalFg is for text-on-bg, not text-on-signal.
        PillTone.Safe -> c.signal to SignalOnInk
        PillTone.Danger -> c.danger to Color.White
        PillTone.Warn -> c.surface2 to c.fg
        PillTone.Ok -> c.signal.copy(alpha = 0.22f) to if (c.isLight) c.signalFg else c.signal
        PillTone.Bad -> c.danger.copy(alpha = 0.15f) to c.danger
        PillTone.Neutral -> c.surface2 to c.fgMuted
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = fg,
        modifier = modifier
            .background(bg, DotPill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

enum class PillTone { Safe, Danger, Warn, Ok, Bad, Neutral }

@Composable
internal fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val c = DotTheme.colors
    // No extra vertical padding — DotCard / parent already insets like DotRow (14–16dp).
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.fg)
            if (!hint.isNullOrEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
            }
        }
        DotToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DotSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DotTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.bg,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(dot.vpn.payam.R.string.action_back),
                    color = c.fgMuted,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
                )
            }
            content()
        }
    }
}

@Composable
internal fun CountBadge(n: Int) {
    val c = DotTheme.colors
    Box(
        Modifier
            .size(28.dp)
            .background(c.surface2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = c.fg)
    }
}

@Composable
internal fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DotTheme.colors.border))
}
