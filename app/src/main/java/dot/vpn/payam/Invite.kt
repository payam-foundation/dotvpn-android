package dot.vpn.payam

private val invitePattern = Regex("""dotvpn://[A-Za-z0-9_-]+""")

/** First dotvpn:// invite in [text] (clipboard, QR payload, intent data), or null. */
fun findInvite(text: String?): String? = text?.let { invitePattern.find(it)?.value }
