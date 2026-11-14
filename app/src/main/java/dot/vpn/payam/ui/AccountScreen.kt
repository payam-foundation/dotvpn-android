// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dot.vpn.payam.AccountInfo
import dot.vpn.payam.BuildConfig
import dot.vpn.payam.R
import dot.vpn.payam.Store
import dot.vpn.payam.accountHasDetails
import dot.vpn.payam.daysUntil
import dot.vpn.payam.formatBytes
import dot.vpn.payam.parseAccount
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobile.Mobile
import java.time.Instant

@Composable
fun AccountScreen(store: Store, onReplace: () -> Unit, onLogout: () -> Unit) {
    val c = DotTheme.colors
    var account by remember {
        mutableStateOf(store.accountCache?.let { runCatching { parseAccount(it) }.getOrNull() })
    }
    var refreshed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cfg = store.config
        if (cfg == null) {
            store.accountCache = null
            account = AccountInfo()
            refreshed = true
            return@LaunchedEffect
        }
        val cached = store.accountCache?.let { runCatching { parseAccount(it) }.getOrNull() }
        if (cached != null && accountHasDetails(cached)) account = cached

        val result = withContext(Dispatchers.IO) {
            runCatching { Mobile.account(cfg) }
        }
        result.onSuccess { raw ->
            val parsed = parseAccount(raw)
            if (accountHasDetails(parsed)) {
                store.accountCache = raw
                account = parsed
            } else if (raw.trim() == "{}") {
                // Older control with no account payload — keep cache if we have one.
                if (account == null) account = AccountInfo()
            }
        }.onFailure { err ->
            if (err.message == "auth") {
                store.accountCache = null
                account = AccountInfo()
            } else if (account == null) {
                account = cached ?: AccountInfo()
            }
        }
        refreshed = true
    }

    val a = account ?: AccountInfo()
    val loaded = refreshed
    val hasAccount = accountHasDetails(a)
    val dash = "—"

    val quota = when {
        !hasAccount -> dash
        a.quotaLimit <= 0 -> stringResource(R.string.account_unlimited)
        else -> "${formatBytes(a.quotaRemaining)} / ${formatBytes(a.quotaLimit)}"
    }
    val resetDays = runCatching { Instant.parse(a.quotaReset).epochSecond }.getOrNull()
        ?.let { daysLeft(it) }
        ?.let { stringResource(R.string.account_reset_in, it) }
    val ends = if (a.expires > 0) {
        if (a.expires * 1000 < System.currentTimeMillis()) {
            stringResource(R.string.account_ended)
        } else {
            daysLeft(a.expires)?.let { stringResource(R.string.account_ends_in, it) }
        }
    } else {
        null
    }
    val devices = when {
        !hasAccount -> dash
        a.maxDevices > 0 -> stringResource(R.string.account_devices_of, a.devicesUsed, a.maxDevices)
        else -> "${a.devicesUsed}/∞"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(R.drawable.ic_account, R.string.account_title)

        if (a.disabled) {
            Text(stringResource(R.string.account_disabled), style = MaterialTheme.typography.bodyMedium, color = c.danger)
        }
        if (loaded && !hasAccount) {
            Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
        }

        DotCard {
            AccountRow(stringResource(R.string.account_name), a.name.trim().ifEmpty { dash })
            DividerLine()
            AccountRow(stringResource(R.string.account_plan), a.packageName.trim().ifEmpty { dash }, ends)
            DividerLine()
            AccountRow(stringResource(R.string.account_quota), quota, if (hasAccount) resetDays else null)
            DividerLine()
            AccountRow(stringResource(R.string.account_devices), devices)
        }

        DotButton(stringResource(R.string.replace_account), onReplace, Modifier.fillMaxWidth())
        DotOutlinedButton(stringResource(R.string.account_logout), onLogout, Modifier.fillMaxWidth())

        Text(
            stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME),
            style = DotType.mono,
            color = c.fgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccountRow(label: String, value: String, note: String? = null) {
    val c = DotTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.fgMuted, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = c.fg, textAlign = TextAlign.End)
            if (!note.isNullOrEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = c.fgMuted, textAlign = TextAlign.End)
            }
        }
    }
}

/** Whole days remaining, rounded up like the desktop; zero once the moment has passed. */
private fun daysLeft(unixSec: Long): Int? = daysUntil(unixSec)?.let { if (it < 0) 0 else it + 1 }
