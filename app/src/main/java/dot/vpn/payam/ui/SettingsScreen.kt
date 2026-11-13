package dot.vpn.payam.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dot.vpn.payam.BuildConfig
import dot.vpn.payam.BypassEntry
import dot.vpn.payam.ClientSettings
import dot.vpn.payam.DNS_PRESETS
import dot.vpn.payam.LangPref
import dot.vpn.payam.R
import dot.vpn.payam.SELECTABLE_CARRIERS
import dot.vpn.payam.Store
import dot.vpn.payam.ThemePref
import dot.vpn.payam.carrierEnabled
import dot.vpn.payam.parseEntries
import dot.vpn.payam.parseSettings
import dot.vpn.payam.settingsPatch
import dot.vpn.payam.toggleCarrier
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mobile.Mobile

private enum class SettingsTab(val label: Int) {
    App(R.string.settings_app),
    Connection(R.string.settings_connection),
    Dns(R.string.settings_dns_page),
    Bypass(R.string.settings_bypass),
    Apps(R.string.settings_apps),
}

/**
 * Theme, language and crash-reporting choices are saved to [store] here, then reported through
 * [onTheme], [onLang] and [onCrashReporting] so the host can restyle, relocalize or start Sentry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: Store,
    onTheme: (ThemePref) -> Unit,
    onLang: (LangPref) -> Unit,
    onCrashReporting: (Boolean) -> Unit,
    onOverlayBack: ((() -> Unit)?) -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val scope = rememberCoroutineScope()
    val lock = remember { Mutex() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(ClientSettings()) }
    var theme by remember { mutableStateOf(store.themePref) }
    var lang by remember { mutableStateOf(store.langPref) }
    var crashOn by remember { mutableStateOf(store.crashReporting) }
    var reportMsg by remember { mutableStateOf<Int?>(null) }
    var dnsSheet by remember { mutableStateOf(false) }
    var dnsDraft by remember { mutableStateOf("") }
    var bypassDraft by remember { mutableStateOf("") }
    var bypassErr by remember { mutableStateOf<Int?>(null) }

    fun persist(patch: String) {
        scope.launch {
            lock.withLock {
                val cfg = store.config ?: return@withLock
                val applied = withContext(Dispatchers.IO) {
                    runCatching {
                        val next = Mobile.applySettings(cfg, patch)
                        next to parseSettings(Mobile.settings(next))
                    }.getOrNull()
                } ?: return@withLock
                store.config = applied.first
                settings = applied.second
            }
        }
    }

    fun sendDiagnostics() {
        val cfg = store.config ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Mobile.reportDiagnostics(cfg, "", "") } }
                .onSuccess { raw ->
                    reportMsg = R.string.report_sent
                    val (url, via) = raw.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    if (via == "browser" && url.startsWith("https://")) open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                .onFailure { reportMsg = R.string.report_failed }
        }
    }

    fun addBypass() {
        val parsed = parseEntries(bypassDraft)
        if (parsed.ok.isEmpty()) {
            bypassErr = R.string.routing_invalid
            return
        }
        bypassErr = if (parsed.bad.isNotEmpty()) R.string.routing_partial else null
        bypassDraft = parsed.bad.joinToString("\n")
        val domains = settings.directDomains.toMutableSet()
        val cidrs = settings.directCidrs.toMutableSet()
        parsed.ok.forEach {
            when (it) {
                is BypassEntry.Domain -> domains.add(it.value)
                is BypassEntry.Cidr -> cidrs.add(it.value)
            }
        }
        persist(settingsPatch(directDomains = domains.toList(), directCidrs = cidrs.toList()))
    }

    fun applyDns() {
        val v = dnsDraft.trim()
        if (v.isNotEmpty()) persist(settingsPatch(dnsPreset = "custom", dns = v))
    }

    LaunchedEffect(Unit) {
        val cfg = store.config ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { parseSettings(Mobile.settings(cfg)) }.getOrNull() }
            ?.let { settings = it }
    }
    LaunchedEffect(settings.dns) { dnsDraft = settings.dns }

    DisposableEffect(dnsSheet) {
        if (dnsSheet) onOverlayBack { dnsSheet = false } else onOverlayBack(null)
        onDispose { onOverlayBack(null) }
    }
    BackHandler(enabled = dnsSheet) { dnsSheet = false }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeading(R.drawable.ic_settings, R.string.settings_title)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTab.entries.forEachIndexed { i, t ->
                val on = i == tab
                Text(
                    stringResource(t.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) c.fg else c.fgMuted,
                    modifier = Modifier
                        .border(1.dp, if (on) c.fg else c.border, DotPill)
                        .clip(DotPill)
                        .background(if (on) c.surface2 else c.bg)
                        .clickable(role = Role.Tab) { tab = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        when (SettingsTab.entries[tab]) {
            SettingsTab.Apps -> AppsSettingsPanel(store, Modifier.weight(1f))

            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (SettingsTab.entries[tab]) {
                    SettingsTab.App -> {
                        // Separate cards — same inset on every side so Language isn't airy at the card bottom.
                        DotCard {
                            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                            Segmented(
                                options = listOf(
                                    stringResource(R.string.theme_system),
                                    stringResource(R.string.theme_light),
                                    stringResource(R.string.theme_dark),
                                ),
                                selected = ThemePref.entries.indexOf(theme),
                                onSelect = { i ->
                                    val v = ThemePref.entries[i]
                                    theme = v
                                    store.themePref = v
                                    onTheme(v)
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        DotCard {
                            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                            Segmented(
                                options = listOf(
                                    stringResource(R.string.lang_system),
                                    stringResource(R.string.lang_en),
                                    stringResource(R.string.lang_fa),
                                ),
                                selected = LangPref.entries.indexOf(lang),
                                onSelect = { i ->
                                    val v = LangPref.entries[i]
                                    lang = v
                                    store.langPref = v
                                    onLang(v)
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        DotCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                            SettingToggleRow(
                                title = stringResource(R.string.crash_reporting),
                                hint = stringResource(R.string.crash_reporting_hint),
                                checked = crashOn,
                                onCheckedChange = { on ->
                                    crashOn = on
                                    store.crashReporting = on
                                    onCrashReporting(on)
                                },
                            )
                        }
                        LinkRow(stringResource(R.string.always_on), stringResource(R.string.always_on_hint)) {
                            open(ctx, Intent(Settings.ACTION_VPN_SETTINGS))
                        }
                        LinkRow(stringResource(R.string.battery_opt), stringResource(R.string.battery_opt_hint)) {
                            val req = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                            if (!open(ctx, req)) {
                                open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                        LinkRow(stringResource(R.string.notifications_perm), stringResource(R.string.notifications_perm_hint)) {
                            open(
                                ctx,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            LinkRow(stringResource(R.string.send_diagnostics), stringResource(R.string.report_hint)) {
                                sendDiagnostics()
                            }
                            reportMsg?.let {
                                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
                            }
                        }
                    }

                    SettingsTab.Connection -> {
                        DotCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                            SettingToggleRow(
                                title = stringResource(R.string.settings_use_relays),
                                hint = stringResource(R.string.settings_use_relays_hint),
                                checked = settings.useRelays,
                                onCheckedChange = {
                                    settings = settings.copy(useRelays = it)
                                    persist(settingsPatch(useRelays = it))
                                },
                            )
                        }
                        DotCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.settings_carriers), style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        stringResource(R.string.settings_carriers_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.fgMuted,
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SELECTABLE_CARRIERS.forEach { kind ->
                                        ProtocolChip(
                                            label = kind,
                                            selected = carrierEnabled(settings.carriers, kind),
                                            onClick = {
                                                val next = toggleCarrier(settings.carriers, kind)
                                                settings = settings.copy(carriers = next)
                                                persist(settingsPatch(carriers = next))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.settings_apply_next), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                    }

                    SettingsTab.Dns -> {
                        LinkRow(stringResource(R.string.settings_dns_preset), dnsLabel(settings.dnsPreset)) { dnsSheet = true }
                        if (settings.dnsPreset == "custom") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                DotField(
                                    value = dnsDraft,
                                    onValueChange = { dnsDraft = it },
                                    placeholder = stringResource(R.string.dns_custom_hint),
                                    modifier = Modifier.weight(1f),
                                    onDone = ::applyDns,
                                )
                                DotOutlinedButton(stringResource(R.string.done), ::applyDns, enabled = dnsDraft.isNotBlank())
                            }
                        }
                        DotCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                            SettingToggleRow(
                                title = stringResource(R.string.settings_doh),
                                hint = stringResource(R.string.settings_doh_hint),
                                checked = settings.doh,
                                onCheckedChange = {
                                    settings = settings.copy(doh = it)
                                    persist(settingsPatch(doh = it))
                                },
                            )
                        }
                        Text(stringResource(R.string.settings_apply_next), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                    }

                    SettingsTab.Bypass -> {
                        Text(stringResource(R.string.routing_body), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
                        Text(stringResource(R.string.routing_add), style = MaterialTheme.typography.labelLarge)
                        DotField(
                            value = bypassDraft,
                            onValueChange = { bypassDraft = it },
                            placeholder = stringResource(R.string.routing_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 3,
                        )
                        DotButton(
                            stringResource(R.string.action_add),
                            ::addBypass,
                            Modifier.fillMaxWidth(),
                            enabled = bypassDraft.isNotBlank(),
                        )
                        bypassErr?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.danger) }
                        val entries = settings.directDomains + settings.directCidrs
                        if (entries.isEmpty()) {
                            Text(stringResource(R.string.routing_empty), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
                        } else {
                            DotCard {
                                entries.forEachIndexed { i, v ->
                                    if (i > 0) DividerLine()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(v, style = DotType.mono, color = c.fg, modifier = Modifier.weight(1f))
                                        TextButton(
                                            onClick = {
                                                persist(
                                                    settingsPatch(
                                                        directDomains = settings.directDomains.filter { it != v },
                                                        directCidrs = settings.directCidrs.filter { it != v },
                                                    ),
                                                )
                                            },
                                        ) { Text(stringResource(R.string.action_remove)) }
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.settings_apply_next), style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
                    }

                    SettingsTab.Apps -> Unit
                }
            }
        }
    }

    if (dnsSheet) {
        DotSheet(title = stringResource(R.string.settings_dns_preset), onDismiss = { dnsSheet = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DNS_PRESETS.forEach { p ->
                    DotRow(onClick = {
                        dnsSheet = false
                        val custom = if (p == "custom") dnsDraft.ifBlank { settings.dns } else null
                        settings = settings.copy(dnsPreset = p)
                        persist(settingsPatch(dnsPreset = p, dns = custom))
                    }) {
                        RadioButton(selected = settings.dnsPreset == p, onClick = null)
                        Text(dnsLabel(p), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, hint: String?, onClick: () -> Unit) {
    DotRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = DotTheme.colors.fgMuted)
            }
        }
        DotChevron()
    }
}

@Composable
private fun dnsLabel(preset: String): String = stringResource(
    when (preset) {
        "google" -> R.string.dns_google
        "quad9" -> R.string.dns_quad9
        "custom" -> R.string.dns_custom
        else -> R.string.dns_cloudflare
    },
)

private fun open(ctx: Context, intent: Intent): Boolean =
    runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
