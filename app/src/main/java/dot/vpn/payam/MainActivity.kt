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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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
            MaterialTheme {
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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            when (val s = state) {
                VpnState.Off -> stringResource(if (invited) R.string.status_off else R.string.no_invite)
                VpnState.Connecting -> stringResource(R.string.status_connecting)
                is VpnState.Connected -> stringResource(R.string.status_connected)
                is VpnState.Failed -> stringResource(R.string.status_error, s.reason)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        (state as? VpnState.Connected)?.let { Text(stringResource(R.string.carrier, it.carrier)) }
        message?.let { Text(stringResource(it)) }

        val running = state is VpnState.Connecting || state is VpnState.Connected
        Button(
            enabled = invited,
            modifier = Modifier.fillMaxWidth().size(72.dp),
            onClick = {
                when {
                    running -> DotvpnService.stop(ctx)
                    Build.VERSION.SDK_INT >= 33 -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> connect(ctx, vpnPermission::launch)
                }
            },
        ) { Text(stringResource(if (running) R.string.disconnect else R.string.connect)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onInvite(clipboardText(ctx)) }) { Text(stringResource(R.string.paste_invite)) }
            OutlinedButton(onClick = {
                GmsBarcodeScanning.getClient(ctx).startScan()
                    .addOnSuccessListener { onInvite(it.rawValue) }
            }) { Text(stringResource(R.string.scan_qr)) }
        }

        if (invited) {
            Text(stringResource(R.string.servers), style = MaterialTheme.typography.titleSmall)
            val servers = remember(invited, state) { store.config?.let { runCatching { Mobile.endpoints(it) }.getOrNull() } }
            Text(servers.orEmpty().trim())
            TextButton(onClick = { showApps = true }) { Text(stringResource(R.string.split_tunnel)) }
        }

        Text(stringResource(R.string.always_on_hint), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }) {
            Text(stringResource(R.string.always_on))
        }
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        for ((m, label) in listOf(
            SplitMode.ALL to R.string.split_all,
            SplitMode.ONLY to R.string.split_only,
            SplitMode.EXCEPT to R.string.split_except,
        )) {
            Row(Modifier.fillMaxWidth().clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == m, onClick = { mode = m })
                Text(stringResource(label))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = ApplicationInfo::packageName) { app ->
                val on = app.packageName in picked
                Row(
                    Modifier.fillMaxWidth().clickable {
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
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
    }
}
