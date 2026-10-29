package org.dotvpn.android

/** What the Go side reports through Mobile.status(). */
sealed interface VpnState {
    data object Off : VpnState
    data object Connecting : VpnState
    data class Connected(val carrier: String) : VpnState
    data class Failed(val reason: String) : VpnState
}

fun parseStatus(s: String): VpnState = when {
    s == "off" -> VpnState.Off
    s == "connecting" -> VpnState.Connecting
    s.startsWith("connected ") -> VpnState.Connected(s.removePrefix("connected "))
    s.startsWith("error: ") -> VpnState.Failed(s.removePrefix("error: "))
    else -> VpnState.Off
}
