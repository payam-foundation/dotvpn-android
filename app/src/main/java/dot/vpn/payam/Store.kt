package dot.vpn.payam

import android.content.Context

/** How the app list applies to the VPN. */
enum class SplitMode { ALL, ONLY, EXCEPT }

/**
 * App-private storage: the client config (it holds the account key, so it never
 * leaves filesDir, and backups are disabled in the manifest) and the split
 * tunnel choice.
 */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("dotvpn", Context.MODE_PRIVATE)
    private val configFile get() = ctx.filesDir.resolve("client.yaml")

    var config: String?
        get() = configFile.takeIf { it.exists() }?.readText()
        set(v) {
            if (v == null) configFile.delete() else configFile.writeText(v)
        }

    var splitMode: SplitMode
        get() = SplitMode.valueOf(prefs.getString("split_mode", SplitMode.ALL.name)!!)
        set(v) = prefs.edit().putString("split_mode", v.name).apply()

    var splitApps: Set<String>
        get() = prefs.getStringSet("split_apps", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("split_apps", v).apply()

    /** Directory for state.json and the server list cache. */
    val dataDir: String get() = ctx.filesDir.absolutePath
}
