package dot.vpn.payam

import android.content.Context

/** How the app list applies to the VPN. */
enum class SplitMode { ALL, ONLY, EXCEPT }

/** In-app theme preference (system follows the device). */
enum class ThemePref { SYSTEM, LIGHT, DARK }

/** In-app language preference (system follows the device locale). */
enum class LangPref { SYSTEM, EN, FA }

/**
 * App-private storage: client config YAML and shell preferences.
 * Account key never leaves filesDir; backups are disabled in the manifest.
 */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("dotvpn", Context.MODE_PRIVATE)
    private val configFile get() = ctx.filesDir.resolve("client.yaml")

    /** Full client YAML; mutate via Mobile.applySettings / addRelay / removeRelay. */
    var config: String?
        get() = configFile.takeIf { it.exists() }?.readText()
        set(v) {
            if (v == null) configFile.delete() else configFile.writeText(v)
        }

    var splitMode: SplitMode
        get() = runCatching { SplitMode.valueOf(prefs.getString("split_mode", SplitMode.ALL.name)!!) }
            .getOrDefault(SplitMode.ALL)
        set(v) = prefs.edit().putString("split_mode", v.name).apply()

    var splitApps: Set<String>
        get() = prefs.getStringSet("split_apps", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("split_apps", v).apply()

    /** Opt-in crash reporting (Sentry dv-android). Off by default. */
    var crashReporting: Boolean
        get() = prefs.getBoolean("crash_reporting", false)
        set(v) = prefs.edit().putBoolean("crash_reporting", v).apply()

    /** True once the first-run setup (diagnostics opt-in) has been shown. */
    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) = prefs.edit().putBoolean("setup_done", v).apply()

    var themePref: ThemePref
        get() = runCatching { ThemePref.valueOf(prefs.getString("theme", ThemePref.SYSTEM.name)!!) }
            .getOrDefault(ThemePref.SYSTEM)
        set(v) = prefs.edit().putString("theme", v.name).apply()

    var langPref: LangPref
        get() = runCatching { LangPref.valueOf(prefs.getString("lang", LangPref.SYSTEM.name)!!) }
            .getOrDefault(LangPref.SYSTEM)
        set(v) = prefs.edit().putString("lang", v.name).apply()

    /** Pending deep-link URI waiting for confirm (policy C). */
    var pendingDeepLink: String?
        get() = prefs.getString("pending_deep_link", null)
        set(v) = prefs.edit().putString("pending_deep_link", v).apply()

    /** Issuer of the home ad last shown (skip duplicate on Relay). */
    var homeAdIssuer: String?
        get() = prefs.getString("home_ad_issuer", null)
        set(v) = prefs.edit().putString("home_ad_issuer", v).apply()

    var homeAdRelayId: String?
        get() = prefs.getString("home_ad_relay", null)
        set(v) = prefs.edit().putString("home_ad_relay", v).apply()

    /** Last successful GET /v1/account JSON (camelCase). Cleared on logout/auth. */
    var accountCache: String?
        get() = prefs.getString("account_cache", null)
        set(v) {
            if (v == null) prefs.edit().remove("account_cache").apply()
            else prefs.edit().putString("account_cache", v).apply()
        }

    /** Directory for state.json and the server list cache. */
    val dataDir: String get() = ctx.filesDir.absolutePath
}
