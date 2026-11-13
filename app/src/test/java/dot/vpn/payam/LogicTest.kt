package dot.vpn.payam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test
    fun findsInviteInNoise() {
        // Whitespace is stripped (desktop parity); terminate codes with punctuation.
        assertEquals("dv://abc-_9", findInvite("my invite:\n dv://abc-_9.\nthanks"))
        assertNull(findInvite("https://example.com"))
        assertNull(findInvite(null))
        assertEquals("dvr://rid1", findRelayInvite("relay\ndvr://rid1.\nok"))
        assertNull(findRelayInvite("dv://only"))
        assertEquals("dv://wrapped", findInvite("dv://wrap\nped"))
    }

    @Test
    fun deepLinkPolicyC() {
        assertEquals(DeepLinkAction.AutoApply, deepLinkAction("dv://new", configured = false))
        assertEquals(DeepLinkAction.ConfirmReplace, deepLinkAction("dv://new", configured = true))
        assertEquals(DeepLinkAction.AutoApply, deepLinkAction("dvr://r1", configured = false))
        assertEquals(DeepLinkAction.ConfirmRelay, deepLinkAction("dvr://r1", configured = true))
        assertEquals(DeepLinkAction.Ignore, deepLinkAction("https://x", configured = true))
    }

    @Test
    fun parsesBypassEntries() {
        val r = parseEntries("example.com\n10.0.0.0/8, bad!!; bank.ir")
        assertEquals(3, r.ok.size)
        assertEquals(listOf("bad!!"), r.bad)
        assertTrue(r.ok.any { it is BypassEntry.Domain && it.value == "example.com" })
        assertTrue(r.ok.any { it is BypassEntry.Cidr && it.value == "10.0.0.0/8" })
    }

    @Test
    fun settingsPatchRoundTrip() {
        val json = settingsPatch(
            useRelays = true,
            pin = "ep1",
            dnsPreset = "quad9",
            doh = true,
            directDomains = listOf("a.ir"),
            directCidrs = listOf("10.0.0.0/8"),
        )
        assertTrue(json.contains("\"pin\":\"ep1\""))
        assertTrue(json.contains("\"dnsPreset\":\"quad9\""))
        assertTrue(isSafeAdLink("https://x.test"))
        assertFalse(isSafeAdLink("dv://evil"))
    }

    @Test
    fun parsesStatus() {
        assertEquals(VpnState.Off, parseStatus("off"))
        assertEquals(VpnState.Connecting, parseStatus("connecting"))
        assertEquals(VpnState.Connected("h3", null, null, null), parseStatus("connected h3|||"))
        val via = parseStatus("connected reverse|Quiet|community|r1") as VpnState.Connected
        assertEquals("reverse", via.carrier)
        assertEquals("Quiet", via.viaRelay)
        assertEquals("community", via.viaRelayTier)
        assertEquals("r1", via.relayId)
        assertTrue(via.viaRelayActive)
        val timed = parseStatus("connected h3||||1700000000000|42") as VpnState.Connected
        assertEquals(1700000000000L, timed.sinceMs)
        assertEquals(42, timed.latencyMs)
        assertEquals(VpnState.Connected("h2(x)"), parseStatus("connected h2(x)"))
        assertEquals(VpnState.Failed("boom"), parseStatus("error: boom"))
        assertEquals(VpnState.Off, parseStatus("???"))
    }

    @Test
    fun carrierToggle() {
        assertTrue(carrierEnabled(emptyList(), "h3"))
        val off = toggleCarrier(emptyList(), "h3")
        assertFalse(carrierEnabled(off, "h3"))
        assertTrue(carrierEnabled(off, "cdnws"))
        val back = toggleCarrier(off, "h3")
        assertTrue(back.isEmpty())
    }

    @Test
    fun formatsUptime() {
        assertEquals("0:00", formatUptime(-5))
        assertEquals("1:05", formatUptime(65_000))
        assertEquals("1:01:01", formatUptime(3_661_000))
    }

    @Test
    fun splitsRelayCatalogFromPrivate() {
        val relays = parseRelays(
            """[
              {"id":"s","name":"S","tier":"system","health":"ok","public":true},
              {"id":"d","name":"D","tier":"community","health":"ok","public":true,"optedIn":true},
              {"id":"p","name":"P","tier":"community","health":"bad","invite":"dvr://x","optedIn":true},
              {"id":"o","name":"O","tier":"community","health":"ok"}
            ]""",
        )
        assertEquals(listOf("s", "d"), relays.catalogRelays().map { it.id })
        assertEquals(listOf("p"), relays.privateRelays().map { it.id })
    }

    @Test
    fun parsesServerProbes() {
        val rows = parseProbes(
            """[{"id":"a","name":"n1","carrier":"h2","country":"DE","latencyMs":42,"error":""},{"id":"b","name":"n1","carrier":"cdnws","latencyMs":0,"error":"timeout"}]""",
        )
        assertEquals("n1(h2)", rows[0].label)
        assertEquals(42, rows[0].latencyMs)
        assertEquals("DE", rows[0].country)
        assertEquals("", rows[1].country)
        assertEquals("timeout", rows[1].error)
    }

    @Test
    fun accountHasDetailsWhenNameOrPlanPresent() {
        assertFalse(accountHasDetails(AccountInfo()))
        assertTrue(accountHasDetails(AccountInfo(name = "Ada")))
        assertTrue(accountHasDetails(AccountInfo(packageName = "pro")))
        assertTrue(accountHasDetails(parseAccount("""{"name":"Ada","package":"pro","quotaLimit":1}""")))
    }

    @Test
    fun groupsCarriersUnderOneNodeName() {
        val g = groupNodes(
            listOf(
                ServerProbe(id = "a", name = "Frankfurt", carrier = "h3", country = "DE", latencyMs = 42),
                ServerProbe(id = "b", name = "Frankfurt", carrier = "cdnws", country = "DE", latencyMs = 118),
                ServerProbe(id = "c", name = "Istanbul", carrier = "h3", country = "TR", latencyMs = 91),
            ),
        )
        assertEquals(2, g.size)
        assertEquals("Frankfurt", g[0].name)
        assertEquals(listOf("a", "b"), g[0].ids)
        assertEquals(42, g[0].latencyMs)
        assertEquals("a", g[0].id)
        assertEquals("Istanbul", g[1].name)
    }

    @Test
    fun parsesRelaysAndAds() {
        val relays = parseRelays("""[{"id":"r1","name":"N","tier":"system","health":"ok","public":true,"optedIn":false}]""")
        assertEquals(1, relays.size)
        assertEquals("system", relays[0].tier)
        assertTrue(relays[0].isPublic)
        val ads = parseAds("""[{"id":"a1","issuer":"owner","format":"card","title":"Hi","link":"https://x"}]""")
        assertEquals("owner", ads[0].issuer)
        assertEquals("Hi", ads[0].title)
        assertTrue(parseAds("null").isEmpty())
        assertTrue(parseAds("").isEmpty())
        assertTrue(parseAds("[]").isEmpty())
    }

    @Test
    fun buildsFlagsFromIsoCodes() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", dot.vpn.payam.ui.flagEmoji("de"))
        assertEquals("DE", dot.vpn.payam.ui.flagCode(" de "))
        assertEquals("", dot.vpn.payam.ui.flagCode("DEU"))
        assertEquals("", dot.vpn.payam.ui.flagEmoji(null))
    }
}
