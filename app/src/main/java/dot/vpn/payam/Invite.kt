// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam

private val invitePattern = Regex("""dv://[A-Za-z0-9_-]+""")
private val relayPattern = Regex("""dvr://[A-Za-z0-9_-]+""")

/** Collapse whitespace so messenger line-wraps still match (same as desktop). */
fun stripInviteNoise(text: String?): String? =
    text?.replace(Regex("""\s+"""), "")?.takeIf { it.isNotEmpty() }

/** First dv:// invite in [text] (clipboard, QR payload, intent data), or null. */
fun findInvite(text: String?): String? =
    text?.let { invitePattern.find(stripInviteNoise(it).orEmpty())?.value }

/** First dvr:// URI in [text], or null. */
fun findRelayInvite(text: String?): String? =
    text?.let { relayPattern.find(stripInviteNoise(it).orEmpty())?.value }

/** True when [uri] is a dv:// account invite. */
fun isAccountInvite(uri: String?): Boolean = findInvite(uri) != null

/** True when [uri] is a dvr:// relay invite. */
fun isRelayInvite(uri: String?): Boolean = findRelayInvite(uri) != null

/**
 * Deep-link policy C: auto-apply when empty; confirm replace when already
 * configured. Relays confirm when an account is already present.
 */
enum class DeepLinkAction { AutoApply, ConfirmReplace, ConfirmRelay, Ignore }

fun deepLinkAction(uri: String?, configured: Boolean): DeepLinkAction {
    val invite = findInvite(uri)
    val relay = findRelayInvite(uri)
    return when {
        invite != null && configured -> DeepLinkAction.ConfirmReplace
        invite != null -> DeepLinkAction.AutoApply
        relay != null && configured -> DeepLinkAction.ConfirmRelay
        relay != null -> DeepLinkAction.AutoApply
        else -> DeepLinkAction.Ignore
    }
}
