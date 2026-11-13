package dot.vpn.payam.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dot.vpn.payam.R
import dot.vpn.payam.findInvite
import dot.vpn.payam.findRelayInvite
import dot.vpn.payam.ui.theme.DotTheme

/**
 * Invite entry: mono dv:// field with Paste, QR scan and gallery import, and an optional
 * dvr:// relay field when the control plane is unreachable. Scan and gallery results are
 * delivered by the host, which owns the camera and picker launchers.
 */
@Composable
fun InviteForm(
    onSubmit: (String) -> Unit,
    onScan: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
    replacing: Boolean = false,
    needsRelay: Boolean = false,
    onRelaySubmit: (String) -> Unit = {},
    showCrashOptIn: Boolean = false,
    crashReporting: Boolean = false,
    onCrashReportingChange: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    var invite by remember { mutableStateOf("") }
    var relay by remember { mutableStateOf("") }
    var relayOpen by remember { mutableStateOf(false) }

    fun submit() {
        val v = invite.trim()
        if (v.isNotEmpty()) onSubmit(v)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showCrashOptIn) {
            DotCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                SettingToggleRow(
                    title = stringResource(R.string.crash_reporting),
                    hint = stringResource(R.string.crash_reporting_hint),
                    checked = crashReporting,
                    onCheckedChange = onCrashReportingChange,
                )
            }
        }

        Text(stringResource(R.string.invite_label), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DotField(
                value = invite,
                onValueChange = { invite = it },
                placeholder = stringResource(R.string.invite_placeholder),
                modifier = Modifier.weight(1f),
                onDone = ::submit,
            )
            DotOutlinedButton(
                text = stringResource(R.string.paste_invite),
                onClick = { clipboardText(ctx)?.let { invite = findInvite(it) ?: it.trim() } },
            )
        }
        if (replacing) {
            Text(stringResource(R.string.invite_hint), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotOutlinedButton(stringResource(R.string.scan_qr), onScan, Modifier.weight(1f))
            DotOutlinedButton(stringResource(R.string.scan_gallery), onGallery, Modifier.weight(1f))
        }

        if (needsRelay) {
            Text(stringResource(R.string.invite_needs_relay), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
            if (!relayOpen) {
                TextButton(onClick = { relayOpen = true }) { Text(stringResource(R.string.invite_bypass_link)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DotField(
                        value = relay,
                        onValueChange = { relay = it },
                        placeholder = stringResource(R.string.invite_relay_placeholder),
                        modifier = Modifier.weight(1f),
                    )
                    DotOutlinedButton(
                        text = stringResource(R.string.action_paste),
                        onClick = { clipboardText(ctx)?.let { relay = findRelayInvite(it) ?: it.trim() } },
                    )
                }
                Text(stringResource(R.string.invite_relay_hint), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                DotOutlinedButton(
                    text = stringResource(R.string.action_add),
                    onClick = { onRelaySubmit(relay.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = relay.isNotBlank(),
                )
            }
        }

        DotButton(
            text = stringResource(R.string.action_add),
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = invite.isNotBlank(),
        )
    }
}

private fun clipboardText(ctx: Context): String? =
    ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
