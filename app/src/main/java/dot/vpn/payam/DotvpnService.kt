// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile
import mobile.Protector

/**
 * Owns the TUN device and hands its descriptor to the Go client. Routing,
 * DNS and split tunnelling are configured here; the Go side only reads and
 * writes packets.
 */
class DotvpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var poll: Job? = null
    private var startJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        active = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
            else -> connectAsync()
        }
        return START_NOT_STICKY
    }

    private fun connectAsync() {
        startJob?.cancel()
        startJob = scope.launch {
            val store = Store(this@DotvpnService)
            var cfg = store.config
            if (cfg == null) {
                stop()
                return@launch
            }
            runCatching { Mobile.normalizeConfig(cfg) }.getOrNull()?.let { normalized ->
                if (normalized != cfg) {
                    store.config = normalized
                    cfg = normalized
                }
            }
            startForegroundCompat(getString(R.string.status_connecting))
            state.value = VpnState.Connecting
            try {
                withContext(Dispatchers.IO) {
                    Mobile.stop()
                    val pfd = build(store) ?: throw IllegalStateException("VPN permission revoked")
                    Mobile.start(cfg, store.dataDir, pfd.detachFd().toLong(), object : Protector {
                        override fun protect(fd: Long): Boolean = this@DotvpnService.protect(fd.toInt())
                    })
                }
            } catch (e: Exception) {
                stop()
                state.value = VpnState.Failed(e.message ?: "start failed")
                return@launch
            }
            poll?.cancel()
            poll = scope.launch {
                var lastConnected: VpnState.Connected? = null
                while (isActive) {
                    val s = parseStatus(Mobile.status())
                    if (s is VpnState.Connected && s != lastConnected) {
                        notify(getString(R.string.notif_connected, s.carrier))
                        lastConnected = s
                    }
                    state.value = s
                    if (s is VpnState.Failed) {
                        stop()
                        return@launch
                    }
                    // Slower poll while connected to save battery; faster while connecting.
                    delay(if (s is VpnState.Connected) 2_500 else 1_000)
                }
            }
        }
    }

    /** Fake DNS lives at 198.18.0.2 inside the Go stack; IPv6 goes to a blackhole. */
    private fun build(store: Store): ParcelFileDescriptor? {
        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("198.18.0.2")
            .addAddress("fd00:198:18::1", 128)
            .addRoute("::", 0)
            .setBlocking(false)
        if (Build.VERSION.SDK_INT >= 29) {
            b.setMetered(false)
        }
        val apps = store.splitApps.filter { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }
        when (store.splitMode) {
            SplitMode.ALL -> Unit
            SplitMode.ONLY -> if (apps.isEmpty()) b.addAllowedApplication(packageName) else apps.forEach(b::addAllowedApplication)
            SplitMode.EXCEPT -> apps.forEach(b::addDisallowedApplication)
        }
        return b.establish()
    }

    private fun stop() {
        startJob?.cancel()
        poll?.cancel()
        scope.launch(Dispatchers.IO) { Mobile.stop() }
        state.value = parseStatus(Mobile.status()).let { if (it is VpnState.Failed) it else VpnState.Off }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stop()

    override fun onDestroy() {
        if (active === this) active = null
        startJob?.cancel()
        poll?.cancel()
        Mobile.stop()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DotvpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .build()
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun startForegroundCompat(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val ACTION_STOP = "dot.vpn.payam.STOP"
        const val MTU = 1380
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1

        @Volatile private var active: DotvpnService? = null

        private val state = MutableStateFlow<VpnState>(VpnState.Off)
        val status: StateFlow<VpnState> get() = state

        /** Protect a socket when the VPN is up; no-op (true) when it is not. */
        fun protectFd(fd: Long): Boolean = active?.protect(fd.toInt()) ?: true

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DotvpnService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, DotvpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
