// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dot.vpn.payam.AdInfo
import dot.vpn.payam.DotvpnService
import dot.vpn.payam.R
import dot.vpn.payam.RelayInfo
import dot.vpn.payam.Store
import dot.vpn.payam.VpnState
import dot.vpn.payam.catalogRelays
import dot.vpn.payam.parseAds
import dot.vpn.payam.parseRelays
import dot.vpn.payam.privateRelays
import dot.vpn.payam.ui.theme.DotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile

private enum class RelaySheet { Public, Private }

@Composable
fun RelayScreen(store: Store) {
    val c = DotTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by DotvpnService.status.collectAsState()
    val connected = state as? VpnState.Connected
    var relays by remember { mutableStateOf<List<RelayInfo>>(emptyList()) }
    var relayAd by remember { mutableStateOf<AdInfo?>(null) }
    var sheet by remember { mutableStateOf<RelaySheet?>(null) }
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf("") }

    suspend fun reload() {
        val cfg = store.config ?: return
        withContext(Dispatchers.IO) { runCatching { parseRelays(Mobile.relays(cfg)) }.getOrNull() }
            ?.let { relays = it }
    }

    fun add() {
        val v = draft.trim()
        if (v.isEmpty()) return
        scope.launch {
            val cfg = store.config ?: return@launch
            val next = withContext(Dispatchers.IO) { runCatching { Mobile.addRelay(cfg, v) }.getOrNull() }
            if (next == null) {
                invalid = true
            } else {
                store.config = next
                draft = ""
                invalid = false
                reload()
            }
        }
    }

    fun remove(id: String) {
        scope.launch {
            val cfg = store.config ?: return@launch
            val next = withContext(Dispatchers.IO) { runCatching { Mobile.removeRelay(cfg, id) }.getOrNull() }
                ?: return@launch
            store.config = next
            reload()
        }
    }

    fun copy(r: RelayInfo) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", r.invite))
        copied = r.id
        scope.launch {
            delay(1500)
            if (copied == r.id) copied = ""
        }
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(connected?.relayId, connected?.viaRelayActive) {
        relayAd = null
        val rid = connected?.relayId.orEmpty()
        if (connected?.viaRelayActive != true || rid.isEmpty()) return@LaunchedEffect
        val cfg = store.config ?: return@LaunchedEffect
        val ads = withContext(Dispatchers.IO) {
            runCatching { parseAds(Mobile.ads(cfg, "relay", rid)) }.getOrNull()
        } ?: return@LaunchedEffect
        val homeIssuer = store.homeAdIssuer
        val homeRelay = store.homeAdRelayId.orEmpty()
        relayAd = ads.firstOrNull { !(it.issuer == homeIssuer && rid == homeRelay) }
    }

    val catalog = remember(relays) { relays.catalogRelays() }
    val mine = remember(relays) { relays.privateRelays() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeading(R.drawable.ic_relay, R.string.relay_title)

            DotRow(onClick = { sheet = RelaySheet.Public }) {
                CountBadge(catalog.size)
                Text(stringResource(R.string.relay_ours), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                DotChevron()
            }
            DotRow(onClick = { sheet = RelaySheet.Private }) {
                CountBadge(mine.size)
                Text(stringResource(R.string.relay_private), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                DotChevron()
            }

            Text(stringResource(R.string.relay_paste), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DotField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        invalid = false
                    },
                    placeholder = "dvr://",
                    modifier = Modifier.weight(1f),
                    onDone = ::add,
                )
                DotButton(stringResource(R.string.action_add), ::add, enabled = draft.isNotBlank())
            }
            if (invalid) {
                Text(stringResource(R.string.relay_bad), style = MaterialTheme.typography.bodyMedium, color = c.danger)
            }
        }

        relayAd?.let {
            AdBanner(
                it,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
            )
        }
    }

    sheet?.let { which ->
        val isPrivate = which == RelaySheet.Private
        val list = if (isPrivate) mine else catalog
        DotSheet(
            title = stringResource(if (isPrivate) R.string.relay_private else R.string.relay_ours),
            onDismiss = { sheet = null },
        ) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) {
                    Text(
                        stringResource(if (isPrivate) R.string.relay_private_empty else R.string.relay_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.fgMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                list.forEachIndexed { i, r ->
                    if (i > 0) DividerLine()
                    RelayRow(
                        r = r,
                        isPrivate = isPrivate,
                        copied = copied == r.id,
                        onCopy = { copy(r) },
                        onRemove = { remove(r.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayRow(r: RelayInfo, isPrivate: Boolean, copied: Boolean, onCopy: () -> Unit, onRemove: () -> Unit) {
    val c = DotTheme.colors
    val ok = r.health == "ok"
    val badge = when {
        isPrivate -> stringResource(R.string.relay_label_private)
        r.tier != "system" -> stringResource(R.string.relay_label_donated)
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Flag(r.country)
            Column(Modifier.weight(1f)) {
                Text(r.name.ifEmpty { r.id }, style = MaterialTheme.typography.bodyLarge)
                if (badge != null) {
                    Text(badge, style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                }
            }
            StatusPill(
                stringResource(if (ok) R.string.relay_label_good else R.string.relay_label_failed),
                if (ok) PillTone.Ok else PillTone.Bad,
            )
        }
        if (isPrivate) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (r.invite.isNotEmpty()) {
                    TextButton(onClick = onCopy) {
                        Text(stringResource(if (copied) R.string.action_copied else R.string.action_copy))
                    }
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}
