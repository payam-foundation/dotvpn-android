package dot.vpn.payam

private val label = Regex("""^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$""", RegexOption.IGNORE_CASE)

sealed class BypassEntry {
    abstract val value: String
    data class Domain(override val value: String) : BypassEntry()
    data class Cidr(override val value: String) : BypassEntry()
}

data class BypassParseResult(val ok: List<BypassEntry>, val bad: List<String>)

fun isDomain(s: String): Boolean {
    val host = if (s.startsWith("*.")) s.substring(2) else s
    if (host.isEmpty() || host.length > 253) return false
    val parts = host.split('.')
    return parts.size >= 2 &&
        parts.all { label.matches(it) } &&
        !parts.last().all { it.isDigit() }
}

private fun isV4(s: String): Boolean {
    val p = s.split('.')
    return p.size == 4 && p.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
}

private fun isV6(s: String): Boolean {
    if (!Regex("""^[0-9a-f:]+$""", RegexOption.IGNORE_CASE).matches(s) ||
        !s.contains(':') || s.split("::").size > 2
    ) {
        return false
    }
    val groups = s.split(':')
    val compressed = s.contains("::")
    if (groups.size > 8 || (!compressed && groups.size != 8)) return false
    return groups.all { it.length <= 4 }
}

fun isCidr(s: String): Boolean {
    val bits = s.split('/')
    if (bits.size > 2) return false
    val addr = bits[0]
    val v4 = isV4(addr)
    if (!v4 && !isV6(addr)) return false
    if (bits.size == 1) return true
    val n = bits[1].toIntOrNull() ?: return false
    return n in 0..(if (v4) 32 else 128)
}

/** Strip scheme/path/port so pasted URLs become hostnames. Keeps CIDR suffixes. */
fun hostFromUrl(raw: String): String {
    var v = raw.trim().lowercase()
    val hadScheme = Regex("""^[a-z][a-z0-9+.-]*://""").containsMatchIn(v)
    v = v.replace(Regex("""^[a-z][a-z0-9+.-]*://"""), "")
    if (hadScheme) {
        v = v.substringBefore('/').substringBefore('?')
    }
    if (v.startsWith('[') && v.contains(']')) {
        return v.substring(1, v.indexOf(']'))
    }
    if (Regex("""^\d{1,3}(\.\d{1,3}){3}:\d+$""").matches(v) ||
        (Regex("""^[a-z0-9.-]+:\d+$""", RegexOption.IGNORE_CASE).matches(v) && !v.contains("::"))
    ) {
        v = v.replace(Regex(""":\d+$"""), "")
    }
    return v
}

fun parseEntry(raw: String): BypassEntry? {
    val v = hostFromUrl(raw)
    if (v.isEmpty()) return null
    if (isCidr(v)) return BypassEntry.Cidr(v)
    if (isDomain(v)) return BypassEntry.Domain(v)
    return null
}

/** Split a paste of domains/IPs on commas, whitespace, or newlines. */
fun parseEntries(raw: String): BypassParseResult {
    val parts = raw.split(Regex("""[\s,;]+""")).map { it.trim() }.filter { it.isNotEmpty() }
    val ok = mutableListOf<BypassEntry>()
    val bad = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (p in parts) {
        val e = parseEntry(p)
        if (e == null) {
            bad.add(p)
            continue
        }
        val k = "${e::class.simpleName}:${e.value}"
        if (!seen.add(k)) continue
        ok.add(e)
    }
    return BypassParseResult(ok, bad)
}
