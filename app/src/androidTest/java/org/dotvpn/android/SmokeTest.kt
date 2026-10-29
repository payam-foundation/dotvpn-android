package org.dotvpn.android

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import mobile.Mobile
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live smoke test against a real node. Run with
 *   -e invite 'dotvpn://...'
 * VPN consent must already be granted on the device
 * (`adb shell appops set org.dotvpn.android ACTIVATE_VPN allow`).
 * Without an invite it is skipped.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val args = InstrumentationRegistry.getArguments()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waitConnected(timeoutMs: Long = 30_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (DotvpnService.status.value is VpnState.Connected) return true
            Thread.sleep(500)
        }
        return false
    }

    @Test
    fun connectsAndCarriesTcpAndUdp() {
        val invite = args.getString("invite")
        assumeTrue("no invite argument", invite != null)
        val store = Store(ctx)
        store.config = Mobile.importInvite(invite!!)
        // With ACTIVATE_VPN allowed prepare() returns null and registers us as the VPN package.
        assertTrue("vpn consent", VpnService.prepare(ctx) == null)
        DotvpnService.start(ctx)
        assertTrue("connected, state=${DotvpnService.status.value} status=${Mobile.status()}", waitConnected())

        // TCP: a 204 from the connectivity endpoint.
        val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as java.net.HttpURLConnection
        assertTrue("tcp", c.responseCode == 204)

        // UDP: a DNS query to 9.9.9.9 must come back through the tunnel.
        val q = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 7, 'e'.code.toByte(), 'x'.code.toByte(),
            'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 1, 0, 1)
        DatagramSocket().use { s ->
            s.soTimeout = 5000
            s.send(DatagramPacket(q, q.size, InetAddress.getByName("9.9.9.9"), 53))
            val buf = ByteArray(512)
            val r = DatagramPacket(buf, buf.size)
            s.receive(r)
            assertTrue("udp reply id", buf[0] == 0x12.toByte() && buf[1] == 0x34.toByte())
        }

        DotvpnService.stop(ctx)
    }
}
