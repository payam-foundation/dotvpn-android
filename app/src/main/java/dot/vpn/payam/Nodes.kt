package dot.vpn.payam

/** One picker row: node name + country, best latency among carriers. */
data class NodeGroup(
    val name: String,
    val country: String = "",
    /** Preferred endpoint id under this name (pin target). */
    val id: String,
    val latencyMs: Int = 0,
    val ids: List<String>,
)

/** Group ladder rungs by endpoint name for the Home node picker. */
fun groupNodes(servers: List<ServerProbe>): List<NodeGroup> {
    data class Acc(
        var name: String,
        var country: String,
        var id: String,
        var latencyMs: Int,
        val ids: MutableList<String>,
    )
    val map = LinkedHashMap<String, Acc>()
    for (s in servers) {
        val key = s.name.ifEmpty { s.id }
        val g = map[key]
        if (g == null) {
            map[key] = Acc(key, s.country, s.id, s.latencyMs, mutableListOf(s.id))
            continue
        }
        g.ids.add(s.id)
        if (s.country.isNotEmpty() && g.country.isEmpty()) g.country = s.country
        if (s.latencyMs > 0 && (g.latencyMs <= 0 || s.latencyMs < g.latencyMs)) {
            g.latencyMs = s.latencyMs
            g.id = s.id
        }
    }
    return map.values.map {
        NodeGroup(name = it.name, country = it.country, id = it.id, latencyMs = it.latencyMs, ids = it.ids.toList())
    }
}
