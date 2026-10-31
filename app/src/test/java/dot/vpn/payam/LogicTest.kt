package dot.vpn.payam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicTest {
    @Test
    fun findsInviteInNoise() {
        assertEquals("dotvpn://abc-_9", findInvite("my invite:\n dotvpn://abc-_9 \nthanks"))
        assertNull(findInvite("https://example.com"))
        assertNull(findInvite(null))
    }

    @Test
    fun parsesStatus() {
        assertEquals(VpnState.Off, parseStatus("off"))
        assertEquals(VpnState.Connecting, parseStatus("connecting"))
        assertEquals(VpnState.Connected("h2(x)"), parseStatus("connected h2(x)"))
        assertEquals(VpnState.Failed("boom"), parseStatus("error: boom"))
        assertEquals(VpnState.Off, parseStatus("???"))
    }
}
