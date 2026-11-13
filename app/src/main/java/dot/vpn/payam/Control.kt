package dot.vpn.payam

import org.json.JSONArray
import org.json.JSONObject

data class ClientSettings(
    val useRelays: Boolean = false,
    val carriers: List<String> = emptyList(),
    val pin: String = "",
    val dns: String = "1.1.1.1:53",
    val dnsPreset: String = "cloudflare",
    val doh: Boolean = false,
    val directDomains: List<String> = emptyList(),
    val directCidrs: List<String> = emptyList(),
)

data class AccountInfo(
    val name: String = "",
    val packageName: String = "",
    val quotaLimit: Long = 0,
    val quotaRemaining: Long = 0,
    val quotaReset: String = "",
    val expires: Long = 0,
    val maxDevices: Int = 0,
    val devicesUsed: Int = 0,
    val mbps: Int = 0,
    val disabled: Boolean = false,
)

data class RelayInfo(
    val id: String,
    val name: String,
    val country: String = "",
    val tier: String = "",
    val health: String = "offline",
    val invite: String = "",
    val isPublic: Boolean = false,
    val optedIn: Boolean = false,
)

/** Open catalog: system relays and donated public ones. */
fun List<RelayInfo>.catalogRelays(): List<RelayInfo> = filter { it.tier == "system" || it.isPublic }

/** Relays the user opted into that are not in the open catalog. */
fun List<RelayInfo>.privateRelays(): List<RelayInfo> = filter { it.optedIn && !it.isPublic && it.tier != "system" }

data class AdInfo(
    val id: String,
    val issuer: String,
    val relayId: String = "",
    val format: String = "card",
    val title: String = "",
    val desc: String = "",
    val link: String = "",
    val imageUrl: String = "",
    val iconUrl: String = "",
)

data class ServerProbe(
    val id: String,
    val name: String,
    val carrier: String,
    val country: String = "",
    val latencyMs: Int = 0,
    val error: String = "",
) {
    val label: String get() = if (carrier.isNotEmpty()) "$name($carrier)" else name
}

val SELECTABLE_CARRIERS = listOf("h3", "h2", "cdnws", "webtunnel", "snowflake")

val DNS_PRESETS = listOf("cloudflare", "google", "quad9", "custom")

fun parseSettings(json: String): ClientSettings {
    val o = JSONObject(json)
    return ClientSettings(
        useRelays = o.optBoolean("useRelays", false),
        carriers = o.optJSONArray("carriers")?.toStringList().orEmpty(),
        pin = o.optString("pin"),
        dns = o.optString("dns", "1.1.1.1:53"),
        dnsPreset = o.optString("dnsPreset", "cloudflare").ifEmpty { "cloudflare" },
        doh = o.optBoolean("doh", false),
        directDomains = o.optJSONArray("directDomains")?.toStringList().orEmpty(),
        directCidrs = o.optJSONArray("directCidrs")?.toStringList().orEmpty(),
    )
}

fun settingsPatch(
    useRelays: Boolean? = null,
    carriers: List<String>? = null,
    pin: String? = null,
    dnsPreset: String? = null,
    dns: String? = null,
    doh: Boolean? = null,
    directDomains: List<String>? = null,
    directCidrs: List<String>? = null,
): String {
    val o = JSONObject()
    if (useRelays != null) o.put("useRelays", useRelays)
    if (carriers != null) o.put("carriers", JSONArray(carriers))
    if (pin != null) o.put("pin", pin)
    if (dnsPreset != null) o.put("dnsPreset", dnsPreset)
    if (dns != null) o.put("dns", dns)
    if (doh != null) o.put("doh", doh)
    if (directDomains != null) o.put("directDomains", JSONArray(directDomains))
    if (directCidrs != null) o.put("directCidrs", JSONArray(directCidrs))
    return o.toString()
}

fun parseAccount(json: String): AccountInfo {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "null") return AccountInfo()
    val o = JSONObject(trimmed)
    return AccountInfo(
        name = o.optString("name"),
        packageName = o.optString("package"),
        quotaLimit = o.optLong("quotaLimit"),
        quotaRemaining = o.optLong("quotaRemaining"),
        quotaReset = o.optString("quotaReset"),
        expires = o.optLong("expires"),
        maxDevices = o.optInt("maxDevices"),
        devicesUsed = o.optInt("devicesUsed"),
        mbps = o.optInt("mbps"),
        disabled = o.optBoolean("disabled"),
    )
}

/** True when control returned usable subscription fields (name alone is enough). */
fun accountHasDetails(a: AccountInfo): Boolean =
    a.name.isNotBlank() || a.packageName.isNotBlank() || a.quotaLimit > 0 || a.maxDevices > 0 || a.expires > 0

fun parseRelays(json: String): List<RelayInfo> {
    val arr = JSONArray(json)
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                RelayInfo(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    country = o.optString("country"),
                    tier = o.optString("tier"),
                    health = o.optString("health", "offline"),
                    invite = o.optString("invite"),
                    isPublic = o.optBoolean("public", false),
                    optedIn = o.optBoolean("optedIn", false),
                ),
            )
        }
    }
}

fun parseAds(json: String): List<AdInfo> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                AdInfo(
                    id = o.optString("id"),
                    issuer = o.optString("issuer"),
                    relayId = o.optString("relayId").ifEmpty { o.optString("relay_id") },
                    format = o.optString("format", "card"),
                    title = o.optString("title"),
                    desc = o.optString("desc").ifEmpty { o.optString("description") },
                    link = o.optString("link"),
                    imageUrl = o.optString("imageUrl").ifEmpty { o.optString("image_url") },
                    iconUrl = o.optString("iconUrl").ifEmpty { o.optString("icon_url") },
                ),
            )
        }
    }
}

fun parseProbes(json: String): List<ServerProbe> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                ServerProbe(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    carrier = o.optString("carrier"),
                    country = o.optString("country"),
                    latencyMs = o.optInt("latencyMs"),
                    error = o.optString("error"),
                ),
            )
        }
    }
}

fun carrierEnabled(carriers: List<String>, kind: String): Boolean =
    carriers.isEmpty() || kind in carriers

/** Toggle like desktop: empty means all on; toggling off one materializes the rest. */
fun toggleCarrier(carriers: List<String>, kind: String): List<String> {
    val cur = if (carriers.isEmpty()) SELECTABLE_CARRIERS.toMutableSet() else carriers.toMutableSet()
    if (kind in cur) cur.remove(kind) else cur.add(kind)
    val next = SELECTABLE_CARRIERS.filter { it in cur }
    return if (next.size == SELECTABLE_CARRIERS.size) emptyList() else next
}

/** True when [link] is safe to open from an ad (http/https only). */
fun isSafeAdLink(link: String): Boolean {
    val u = link.trim().lowercase()
    return u.startsWith("https://") || u.startsWith("http://")
}

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val kb = n / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format("%.1f GB", gb)
    return String.format("%.1f TB", gb / 1024.0)
}

fun daysUntil(unixSec: Long, nowSec: Long = System.currentTimeMillis() / 1000): Int? {
    if (unixSec <= 0) return null
    val d = ((unixSec - nowSec) / 86400).toInt()
    return d
}

private fun JSONArray.toStringList(): List<String> =
    buildList { for (i in 0 until length()) add(optString(i)) }
