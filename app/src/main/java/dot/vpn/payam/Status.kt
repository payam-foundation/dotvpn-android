package dot.vpn.payam

/** What the Go side reports through Mobile.status(). */
sealed interface VpnState {
    data object Off : VpnState
    data object Connecting : VpnState
    data class Connected(
        val carrier: String,
        val viaRelay: String? = null,
        val viaRelayTier: String? = null,
        val relayId: String? = null,
        /** Unix ms the session was established; 0 when unknown. */
        val sinceMs: Long = 0,
        /** Winning endpoint's dial + probe time; 0 when unknown. */
        val latencyMs: Int = 0,
    ) : VpnState {
        val viaRelayActive: Boolean get() = !viaRelay.isNullOrEmpty()
    }
    data class Failed(val reason: String) : VpnState
}

/**
 * Parses Mobile.status(). Connected lines are
 * `connected <kind>|<viaName>|<viaTier>|<relayId>|<sinceUnixMs>|<latencyMs>`
 * (empty via* when direct). The four-field form without since/latency and the
 * legacy `connected <label>` (no pipes) are still accepted.
 */
fun parseStatus(s: String): VpnState = when {
    s == "off" -> VpnState.Off
    s == "connecting" -> VpnState.Connecting
    s.startsWith("connected ") -> {
        val rest = s.removePrefix("connected ")
        val parts = rest.split("|", limit = 6)
        if (parts.size == 4 || parts.size == 6) {
            VpnState.Connected(
                carrier = parts[0],
                viaRelay = parts[1].ifEmpty { null },
                viaRelayTier = parts[2].ifEmpty { null },
                relayId = parts[3].ifEmpty { null },
                sinceMs = parts.getOrNull(4)?.toLongOrNull() ?: 0,
                latencyMs = parts.getOrNull(5)?.toIntOrNull() ?: 0,
            )
        } else {
            VpnState.Connected(carrier = rest)
        }
    }
    s.startsWith("error: ") -> VpnState.Failed(s.removePrefix("error: "))
    else -> VpnState.Off
}

/** Session uptime as m:ss, or h:mm:ss from one hour. */
fun formatUptime(ms: Long): String {
    val s = maxOf(0, ms / 1000)
    val h = s / 3600
    val m = s % 3600 / 60
    val pad = { x: Long -> x.toString().padStart(2, '0') }
    return if (h > 0) "$h:${pad(m)}:${pad(s % 60)}" else "$m:${pad(s % 60)}"
}
