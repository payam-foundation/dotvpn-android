package dot.vpn.payam

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dot.vpn.payam.ui.HopMark
import dot.vpn.payam.ui.HopState
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType
import dot.vpn.payam.ui.theme.DotVpnTheme
import mobile.Mobile

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var invited by mutableStateOf(false)
    private var message by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        invited = store.config != null
        importFrom(intent?.dataString)
        setContent {
            val persian = LocalConfiguration.current.locales[0].language == "fa"
            DotVpnTheme(persian = persian) {
                Surface(Modifier.fillMaxSize()) {
                    Home(
                        invited = invited,
                        message = message,
                        store = store,
                        onInvite = ::importFrom,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent.dataString)
    }

    /** Validates [text] in Go before saving, so a bad invite never replaces a good config. */
    private fun importFrom(text: String?) {
        val uri = findInvite(text) ?: return
        try {
            store.config = Mobile.importInvite(uri)
            invited = true
            message = R.string.invite_saved
        } catch (e: Exception) {
            message = R.string.invite_bad
        }
    }
}

@Composable
private fun Home(invited: Boolean, message: Int?, store: Store, onInvite: (String?) -> Unit) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val state by DotvpnService.status.collectAsState()
    var showApps by remember { mutableStateOf(false) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) DotvpnService.start(ctx)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The VPN works without the notification being visible; ask once, go on.
        connect(ctx, vpnPermission::launch)
    }

    if (showApps) {
        AppPicker(store) { showApps = false }
        return
    }

    val running = state is VpnState.Connecting || state is VpnState.Connected
    val connected = state as? VpnState.Connected
    val failed = state as? VpnState.Failed
    val toggle = {
        when {
            running -> DotvpnService.stop(ctx)
            Build.VERSION.SDK_INT >= 33 -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> connect(ctx, vpnPermission::launch)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!invited) {
            EmptyState(onInvite)
        } else {
            StatusHeader(state)
            connected?.let { CarrierPill(it.carrier) }
            failed?.let {
                Text(stringResource(R.string.failed_next), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
                Text(
                    stringResource(R.string.details) + ": " + isolate(it.reason),
                    style = DotType.mono, color = c.fgMuted,
                )
            }
        }
        message?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == R.string.invite_bad) c.danger else c.fgMuted,
            )
        }

        if (invited) {
            ConnectButton(state, toggle)
            InviteActions(onInvite)

            Text(stringResource(R.string.servers), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            val servers = remember(invited, state) {
                store.config?.let { runCatching { Mobile.endpoints(it) }.getOrNull() }.orEmpty().lines().filter(String::isNotBlank)
            }
            Column {
                servers.forEach { ServerRow(it, active = it == connected?.carrier) }
            }
            TextButton(onClick = { showApps = true }) { Text(stringResource(R.string.split_tunnel)) }
        }

        Text(stringResource(R.string.always_on_hint), style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }) {
            Text(stringResource(R.string.always_on))
        }
        Text(stringResource(R.string.limits), style = MaterialTheme.typography.labelSmall, color = c.fgMuted)
    }
}

/** Latin runs inside Persian text get a direction isolate so punctuation does not jump. */
private fun isolate(s: String) = "\u2066$s\u2069"

@Composable
private fun StatusHeader(state: VpnState) {
    val c = DotTheme.colors
    val (hop, word, color) = when (state) {
        VpnState.Off -> Triple(HopState.Off, R.string.status_off, c.fg)
        VpnState.Connecting -> Triple(HopState.Connecting, R.string.status_connecting, c.fg)
        is VpnState.Connected -> Triple(HopState.Connected, R.string.status_connected, c.signalFg)
        is VpnState.Failed -> Triple(HopState.Failed, R.string.status_failed, c.danger)
    }
    val label = stringResource(word)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HopMark(hop, label, size = 40.dp)
        Text(label, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

@Composable
private fun CarrierPill(carrier: String) {
    val c = DotTheme.colors
    Text(
        isolate(carrier),
        style = DotType.mono.copy(fontSize = 12.sp),
        color = c.signalFg,
        modifier = Modifier
            .border(1.dp, c.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val mod = Modifier.fillMaxWidth().height(56.dp)
    when (state) {
        VpnState.Connecting -> Button(
            onClick = onClick, modifier = mod, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = c.surface2, contentColor = c.fg),
        ) { Text(stringResource(R.string.cancel)) }
        is VpnState.Connected -> OutlinedButton(
            onClick = onClick, modifier = mod, shape = shape,
            border = BorderStroke(1.dp, c.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.fg),
        ) { Text(stringResource(R.string.disconnect)) }
        else -> Button(onClick = onClick, modifier = mod, shape = shape) { Text(stringResource(R.string.connect)) }
    }
}

@Composable
private fun InviteActions(onInvite: (String?) -> Unit) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val border = BorderStroke(1.dp, c.border)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onInvite(clipboardText(ctx)) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = shape, border = border,
        ) { Text(stringResource(R.string.paste_invite)) }
        OutlinedButton(
            onClick = { GmsBarcodeScanning.getClient(ctx).startScan().addOnSuccessListener { onInvite(it.rawValue) } },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = shape, border = border,
        ) { Text(stringResource(R.string.scan_qr)) }
    }
}

/** One mono Hop, one line, one action. */
@Composable
private fun EmptyState(onInvite: (String?) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val line = stringResource(R.string.no_invite)
        HopMark(HopState.Off, line, size = 64.dp)
        Text(line, style = MaterialTheme.typography.bodyLarge)
        InviteActions(onInvite)
    }
}

@Composable
private fun ServerRow(label: String, active: Boolean) {
    val c = DotTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // State is a shape plus a word elsewhere; the filled dot marks the active route.
        Canvas(Modifier.size(12.dp)) {
            val r = size.minDimension / 2
            if (active) {
                if (c.isLight) {
                    drawCircle(c.fg, r)
                    drawCircle(c.signal, r * 0.62f)
                } else {
                    drawCircle(c.signal, r)
                }
            } else {
                drawCircle(c.off, r)
            }
        }
        Text(isolate(label), style = DotType.mono, modifier = Modifier.weight(1f))
    }
}

/** Asks for VPN consent when the system requires it, then starts the service. */
private fun connect(ctx: Context, launch: (Intent) -> Unit) {
    val consent = VpnService.prepare(ctx)
    if (consent == null) DotvpnService.start(ctx) else launch(consent)
}

private fun clipboardText(ctx: Context): String? =
    ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

@Composable
private fun AppPicker(store: Store, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val apps = remember {
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }
    var mode by remember { mutableStateOf(store.splitMode) }
    var picked by remember { mutableStateOf(store.splitApps) }

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)) {
        for ((m, label) in listOf(
            SplitMode.ALL to R.string.split_all,
            SplitMode.ONLY to R.string.split_only,
            SplitMode.EXCEPT to R.string.split_except,
        )) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == m, onClick = { mode = m })
                Text(stringResource(label))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = ApplicationInfo::packageName) { app ->
                val on = app.packageName in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        picked = if (on) picked - app.packageName else picked + app.packageName
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, enabled = mode != SplitMode.ALL, onCheckedChange = null)
                    Text(app.loadLabel(pm).toString())
                }
            }
        }
        Button(onClick = {
            store.splitMode = mode
            store.splitApps = picked
            onDone()
        }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.done)) }
    }
}
