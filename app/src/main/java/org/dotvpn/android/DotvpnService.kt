package org.dotvpn.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
            else -> connect()
        }
        return START_NOT_STICKY
    }

    private fun connect() {
        val store = Store(this)
        val cfg = store.config
        if (cfg == null) {
            stop()
            return
        }
        startForegroundCompat()
        // Start returns once the engine runs; a second call while running errors.
        try {
            Mobile.stop()
            val pfd = build(store) ?: throw IllegalStateException("VPN permission revoked")
            Mobile.start(cfg, store.dataDir, pfd.detachFd().toLong(), object : Protector {
                override fun protect(fd: Long): Boolean = this@DotvpnService.protect(fd.toInt())
            })
        } catch (e: Exception) {
            Log.e("dotvpn", "start failed", e)
            stop()
            state.value = VpnState.Failed(e.message ?: "start failed")
            return
        }
        poll?.cancel()
        poll = scope.launch {
            while (isActive) {
                val s = parseStatus(Mobile.status())
                state.value = s
                if (s is VpnState.Failed) {
                    stop()
                    return@launch
                }
                delay(1000)
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
        // Apps must exist on the device or addAllowed/DisallowedApplication throws.
        val apps = store.splitApps.filter { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }
        when (store.splitMode) {
            SplitMode.ALL -> Unit
            SplitMode.ONLY -> if (apps.isEmpty()) b.addAllowedApplication(packageName) else apps.forEach(b::addAllowedApplication)
            SplitMode.EXCEPT -> apps.forEach(b::addDisallowedApplication)
        }
        return b.establish()
    }

    private fun stop() {
        poll?.cancel()
        Mobile.stop()
        state.value = parseStatus(Mobile.status()).let { if (it is VpnState.Failed) it else VpnState.Off }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stop()

    override fun onDestroy() {
        poll?.cancel()
        Mobile.stop()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DotvpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val ACTION_STOP = "org.dotvpn.android.STOP"
        const val MTU = 1380
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1

        private val state = MutableStateFlow<VpnState>(VpnState.Off)
        val status: StateFlow<VpnState> get() = state

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DotvpnService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, DotvpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
