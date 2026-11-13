package dot.vpn.payam.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dot.vpn.payam.ui.theme.Geist
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dot.vpn.payam.AdInfo
import dot.vpn.payam.App
import dot.vpn.payam.ClientSettings
import dot.vpn.payam.DotvpnService
import dot.vpn.payam.R
import dot.vpn.payam.ServerProbe
import dot.vpn.payam.Store
import dot.vpn.payam.VpnState
import dot.vpn.payam.findInvite
import dot.vpn.payam.formatUptime
import dot.vpn.payam.groupNodes
import dot.vpn.payam.parseAds
import dot.vpn.payam.parseProbes
import dot.vpn.payam.parseSettings
import dot.vpn.payam.settingsPatch
import dot.vpn.payam.ui.theme.DotTheme
import dot.vpn.payam.ui.theme.DotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mobile.Mobile
import mobile.Protector
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

private val IP_ECHO_URLS = listOf("https://checkip.amazonaws.com", "https://ifconfig.me/ip")
private val IP_SHAPE = Regex("^[0-9a-fA-F:.]{2,45}$")

@Composable
fun HomeScreen(
    store: Store,
    invited: Boolean,
    needsRelay: Boolean,
    message: Int?,
    onInvite: (String?) -> Unit,
    onRelay: (String) -> Unit,
    onHomeAdIssuer: (issuer: String?, relayId: String) -> Unit,
    onOverlayBack: ((() -> Unit)?) -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = DotTheme.colors
    val scope = rememberCoroutineScope()
    val state by DotvpnService.status.collectAsState()
    val connected = state as? VpnState.Connected
    val failed = state as? VpnState.Failed
    val running = state is VpnState.Connecting || state is VpnState.Connected

    var homeAd by remember { mutableStateOf<AdInfo?>(null) }
    var settings by remember { mutableStateOf(ClientSettings()) }
    var probes by remember { mutableStateOf<List<ServerProbe>>(emptyList()) }
    var probing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<Int?>(null) }
    var crashOn by remember { mutableStateOf(store.crashReporting) }
    val firstRun = remember { !store.setupDone }
    val nodes = remember(probes) { groupNodes(probes) }
    var ip by remember { mutableStateOf<String?>(null) }
    var ipChecking by remember { mutableStateOf(false) }
    var directIp by remember { mutableStateOf<String?>(null) }

    DisposableEffect(picker) {
        if (picker) onOverlayBack { picker = false } else onOverlayBack(null)
        onDispose { onOverlayBack(null) }
    }
    BackHandler(enabled = picker) { picker = false }

    fun deliver(text: String?) {
        if (findInvite(text) == null) {
            localMessage = R.string.invite_bad
        } else {
            localMessage = null
            onInvite(text)
        }
    }

    fun probe() {
        val cfg = store.config ?: return
        if (probing) return
        probing = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching {
                    parseProbes(
                        Mobile.probeServers(cfg, store.dataDir, object : Protector {
                            override fun protect(fd: Long): Boolean = DotvpnService.protectFd(fd)
                        }),
                    )
                }.getOrDefault(emptyList())
            }
            if (next.isNotEmpty()) probes = next
            probing = false
        }
    }

    fun pin(id: String) {
        scope.launch {
            val cfg = store.config ?: return@launch
            val applied = withContext(Dispatchers.IO) {
                runCatching {
                    val next = Mobile.applySettings(cfg, settingsPatch(pin = id))
                    next to parseSettings(Mobile.settings(next))
                }.getOrNull()
            } ?: return@launch
            store.config = applied.first
            settings = applied.second
            picker = false
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) DotvpnService.start(ctx)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        connect(ctx, vpnPermission::launch)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { deliver(readQr(ctx, uri)) }
    }

    val toggle = {
        when {
            running -> DotvpnService.stop(ctx)
            Build.VERSION.SDK_INT >= 33 -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> connect(ctx, vpnPermission::launch)
        }
    }

    LaunchedEffect(invited) {
        if (!invited) return@LaunchedEffect
        val cfg = store.config ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { parseSettings(Mobile.settings(cfg)) }.getOrNull() }
            ?.let { settings = it }
        probe()
    }

    LaunchedEffect(invited, connected?.relayId, connected?.viaRelayActive) {
        homeAd = null
        store.homeAdIssuer = null
        store.homeAdRelayId = null
        onHomeAdIssuer(null, "")
        if (!invited) return@LaunchedEffect
        val cfg = store.config ?: return@LaunchedEffect
        val relayId = connected?.relayId.orEmpty().takeIf { connected?.viaRelayActive == true }.orEmpty()
        val ad = withContext(Dispatchers.IO) {
            runCatching { parseAds(Mobile.ads(cfg, "home", relayId)).firstOrNull() }.getOrNull()
        } ?: return@LaunchedEffect
        homeAd = ad
        store.homeAdIssuer = ad.issuer
        store.homeAdRelayId = relayId
        onHomeAdIssuer(ad.issuer, relayId)
    }

    val ipKey = when (val s = state) {
        VpnState.Off -> "off"
        VpnState.Connecting -> "connecting"
        is VpnState.Connected -> "up:${s.sinceMs}:${s.carrier}"
        is VpnState.Failed -> "failed"
    }
    LaunchedEffect(invited, ipKey) {
        ip = null
        if (!invited || state is VpnState.Connecting) {
            ipChecking = false
            return@LaunchedEffect
        }
        ipChecking = true
        try {
            repeat(4) {
                val found = withContext(Dispatchers.IO) { lookupPublicIp() }
                if (found != null) {
                    ip = found
                    if (state !is VpnState.Connected) directIp = found
                    return@LaunchedEffect
                }
                delay(1500)
            }
        } finally {
            ipChecking = false
        }
    }

    val shownMessage = localMessage ?: message
    val line = stringResource(R.string.no_invite)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!invited) {
                BrandLockup(HopState.Off, line)
                Text(line, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                shownMessage?.let { MessageLine(it) }
                InviteForm(
                    onSubmit = ::deliver,
                    onScan = {
                        GmsBarcodeScanning.getClient(ctx).startScan().addOnSuccessListener { deliver(it.rawValue) }
                    },
                    onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    needsRelay = needsRelay,
                    onRelaySubmit = onRelay,
                    showCrashOptIn = firstRun,
                    crashReporting = crashOn,
                    onCrashReportingChange = { on ->
                        crashOn = on
                        store.crashReporting = on
                        (ctx.applicationContext as? App)?.applyCrashReporting(on)
                    },
                )
            } else {
                StatusHeader(state)
                connected?.let { conn ->
                    if (conn.viaRelayActive) {
                        val community = conn.viaRelayTier == "community"
                        StatusPill(
                            stringResource(
                                if (community) R.string.home_community_relay else R.string.home_relay,
                                isolate(conn.carrier),
                            ),
                            PillTone.Neutral,
                        )
                        conn.viaRelay?.let {
                            Text(
                                stringResource(R.string.home_via_relay, isolate(it)),
                                style = DotType.mono.copy(fontSize = 12.sp),
                                color = c.fgMuted,
                            )
                        }
                    }
                    Uptime(conn.sinceMs)
                }
                failed?.let {
                    Text(
                        stringResource(R.string.failed_next),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.fgMuted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.details) + ": " + isolate(it.reason),
                        style = DotType.mono.copy(fontSize = 12.sp),
                        color = c.fgMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                shownMessage?.let { MessageLine(it) }

                PowerOrb(state, toggle)

                IpBlock(
                    address = when {
                        state is VpnState.Connecting || ipChecking -> stringResource(R.string.home_ip_checking)
                        ip != null -> ip.orEmpty()
                        else -> "-"
                    },
                    badge = when {
                        state is VpnState.Connecting || ipChecking ->
                            stringResource(R.string.home_ip_checking_badge) to PillTone.Warn
                        ip == null -> null
                        connected != null && ip != directIp -> stringResource(R.string.home_ip_safe) to PillTone.Safe
                        else -> stringResource(R.string.home_ip_unsafe) to PillTone.Danger
                    },
                    ping = connected?.latencyMs?.takeIf { it > 0 },
                    carrier = connected?.carrier?.takeIf { it.isNotBlank() },
                )

                val pinned = settings.pin.takeIf { it.isNotEmpty() }
                val pinnedNode = pinned?.let { id -> nodes.firstOrNull { id in it.ids } }
                val activeNode = connected?.let { cn ->
                    val hit = probes.firstOrNull { it.carrier == cn.carrier } ?: return@let null
                    nodes.firstOrNull { hit.id in it.ids }
                }
                DotRow(onClick = {
                    picker = true
                    if (probes.isEmpty()) probe()
                }) {
                    if (pinned == null) AutoMark() else Flag(pinnedNode?.country)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (pinned == null) stringResource(R.string.servers_auto) else isolate(pinnedNode?.name ?: pinned),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            when {
                                pinned != null -> stringResource(R.string.servers_pinned)
                                activeNode != null -> isolate(
                                    listOfNotNull(activeNode.name, activeNode.country.takeIf { it.isNotEmpty() })
                                        .joinToString(" · "),
                                )
                                else -> stringResource(R.string.servers_auto_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.fgMuted,
                        )
                    }
                    (pinnedNode ?: activeNode.takeIf { pinned == null })?.latencyMs?.takeIf { it > 0 }?.let {
                        Text("$it ms", style = DotType.mono.copy(fontSize = 12.sp), color = c.fgMuted)
                    }
                    DotChevron()
                }
            }
        }

        if (invited) {
            homeAd?.let {
                AdBanner(
                    it,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }
    }

    if (picker) {
        DotSheet(title = stringResource(R.string.servers_pick), onDismiss = { picker = false }) {
            if (probing) {
                Text(
                    stringResource(R.string.servers_probing),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.fgMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ServerOption(
                    selected = settings.pin.isEmpty(),
                    onClick = { pin("") },
                    mark = { AutoMark() },
                    title = stringResource(R.string.servers_auto),
                    subtitle = stringResource(R.string.servers_auto_hint),
                    trailing = {},
                )
                nodes.forEach { n ->
                    val active = connected != null && probes.any { it.id in n.ids && it.carrier == connected.carrier }
                    ServerOption(
                        selected = settings.pin in n.ids,
                        onClick = { pin(n.id) },
                        mark = { Flag(n.country) },
                        title = isolate(n.name),
                        subtitle = if (active) stringResource(R.string.servers_active) else n.country.takeIf { it.isNotEmpty() },
                        trailing = {
                            when {
                                n.latencyMs > 0 -> Text(
                                    "${n.latencyMs} ms",
                                    style = DotType.mono.copy(fontSize = 12.sp),
                                    color = c.fgMuted,
                                )
                                probes.any { it.id in n.ids && it.error.isNotBlank() } ->
                                    StatusPill(stringResource(R.string.servers_probe_fail), PillTone.Bad)
                            }
                        },
                    )
                }
                if (nodes.isEmpty() && !probing) {
                    Text(
                        stringResource(R.string.servers_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.fgMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageLine(message: Int) {
    val c = DotTheme.colors
    val bad = message == R.string.invite_bad || message == R.string.invite_revoked || message == R.string.relay_bad
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = if (bad) c.danger else c.fgMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BrandLockup(hop: HopState, statusLabel: String, statusColor: Color? = null) {
    val c = DotTheme.colors
    val tone = statusColor ?: c.fg
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HopMark(hop, statusLabel, size = 40.dp)
            Text(
                stringResource(R.string.app_name),
                color = c.fg,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.03).em,
            )
        }
        Text(statusLabel, style = MaterialTheme.typography.headlineSmall, color = tone)
    }
}

@Composable
private fun StatusHeader(state: VpnState) {
    val c = DotTheme.colors
    val (hop, word, color) = when (state) {
        VpnState.Off -> Triple(HopState.Off, R.string.status_off, c.fg)
        VpnState.Connecting -> Triple(HopState.Connecting, R.string.status_connecting, c.fg)
        is VpnState.Connected -> Triple(HopState.Connected, R.string.status_connected, c.signalFg)
        is VpnState.Failed -> Triple(HopState.Failed, R.string.status_failed, c.danger)
    }
    BrandLockup(hop, stringResource(word), color)
}

@Composable
private fun Uptime(sinceMs: Long) {
    if (sinceMs <= 0) return
    var now by remember(sinceMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(formatUptime(now - sinceMs), style = DotType.mono.copy(fontSize = 18.sp), color = DotTheme.colors.fgMuted)
}

@Composable
private fun PowerOrb(state: VpnState, onClick: () -> Unit) {
    val c = DotTheme.colors
    val ctx = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val busy = state is VpnState.Connecting
    val (bg, fg) = when (state) {
        is VpnState.Connected -> c.fg to c.bg
        VpnState.Connecting -> c.surface2 to c.fg
        else -> c.fg to c.bg
    }
    val label = stringResource(
        when (state) {
            VpnState.Connecting -> R.string.cancel
            is VpnState.Connected -> R.string.disconnect
            is VpnState.Failed -> R.string.retry
            VpnState.Off -> R.string.connect
        },
    )
    val orbit = rememberInfiniteTransition(label = "orb")
    val angle by orbit.animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "orb-angle",
    )
    val pulse by orbit.animateFloat(
        0.92f,
        1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb-pulse",
    )
    val density = LocalDensity.current
    val radiusPx = with(density) { 80.dp.toPx() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
            if (busy && !reduceMotion) {
                Box(
                    Modifier
                        .size(172.dp)
                        .scale(pulse)
                        .border(1.dp, c.fg.copy(alpha = 0.35f), CircleShape),
                )
                listOf(
                    Triple(0f, 8.dp, 1f),
                    Triple(120f, 6.dp, 0.72f),
                    Triple(240f, 5.dp, 0.4f),
                ).forEach { (start, size, alpha) ->
                    val rad = Math.toRadians((angle + start).toDouble())
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (cos(rad) * radiusPx).roundToInt(),
                                    (sin(rad) * radiusPx).roundToInt(),
                                )
                            }
                            .size(size)
                            .clip(CircleShape)
                            .background(c.fg.copy(alpha = alpha)),
                    )
                }
            }
            Box(
                Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .then(if (state is VpnState.Failed) Modifier.border(3.dp, c.danger, CircleShape) else Modifier)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_power),
                    contentDescription = label,
                    modifier = Modifier.size(56.dp),
                    tint = fg,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.fgMuted)
    }
}

@Composable
private fun IpBlock(address: String, badge: Pair<String, PillTone>?, ping: Int?, carrier: String?) {
    val c = DotTheme.colors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.home_ip), style = MaterialTheme.typography.labelSmall, color = c.fgMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(address, style = DotType.mono.copy(fontSize = 15.sp), color = c.fg)
            badge?.let { StatusPill(it.first, it.second) }
        }
        if (ping != null || carrier != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ping?.let {
                    Text(
                        stringResource(R.string.home_ping, it),
                        style = DotType.mono.copy(fontSize = 12.sp),
                        color = c.fgMuted,
                    )
                }
                carrier?.let { StatusPill(isolate(it), PillTone.Safe) }
            }
        }
    }
}

@Composable
private fun AutoMark() {
    Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
        Text("⇄", fontSize = 20.sp, color = DotTheme.colors.fgMuted)
    }
}

@Composable
private fun ServerOption(
    selected: Boolean,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
) {
    val c = DotTheme.colors
    DotRow(onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
        mark()
        Column(Modifier.weight(1f)) {
            Text(title, style = DotType.mono, color = c.fg)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.fgMuted)
            }
        }
        trailing()
    }
}

/** Latin runs inside Persian text get a direction isolate so punctuation does not jump. */
internal fun isolate(s: String) = "\u2066$s\u2069"

internal fun connect(ctx: Context, launch: (Intent) -> Unit) {
    val consent = VpnService.prepare(ctx)
    if (consent == null) DotvpnService.start(ctx) else launch(consent)
}

/** Plain-text IP echo over whatever route the app has right now (tunnel when up, direct when off). */
private fun lookupPublicIp(): String? {
    for (url in IP_ECHO_URLS) {
        val body = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.bufferedReader().use { it.readText() }.take(64).trim()
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        if (body != null && IP_SHAPE.matches(body)) return body
    }
    return null
}

/** First QR payload in the picked image, or null when none decodes. */
private suspend fun readQr(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )
    try {
        val image = InputImage.fromFilePath(ctx, uri)
        suspendCancellableCoroutine<String?> { cont ->
            scanner.process(image)
                .addOnSuccessListener { codes -> cont.resume(codes.firstNotNullOfOrNull { it.rawValue }) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: IOException) {
        null
    } finally {
        scanner.close()
    }
}
