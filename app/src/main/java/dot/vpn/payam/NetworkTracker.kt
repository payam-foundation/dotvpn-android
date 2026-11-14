// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam

/**
 * Turns raw ConnectivityManager callbacks into "the network we ride changed".
 *
 * registerNetworkCallback replays every matching network at registration and
 * keeps reporting the ones that are not in use, so a handle appearing is not a
 * change. The first network is the baseline; after that onChange fires with the
 * replacement when the current one is lost, and with the next network to appear
 * when there was none. Capability and link churn is ignored.
 */
class NetworkTracker<N>(private val onChange: (N) -> Unit) {
    private val up = LinkedHashSet<N>()
    private var current: N? = null
    private var seen = false

    fun onAvailable(n: N) {
        up.add(n)
        if (current != null) return
        current = n
        if (seen) onChange(n)
        seen = true
    }

    fun onLost(n: N) {
        up.remove(n)
        if (current != n) return
        current = up.firstOrNull()
        current?.let(onChange)
    }
}
