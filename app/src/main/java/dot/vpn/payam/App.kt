package dot.vpn.payam

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import mobile.Mobile

/** Application entry: opt-in Sentry only (project dv-android). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Mobile.setAppVersion(BuildConfig.VERSION_NAME) }
        val store = Store(this)
        store.config?.let { raw ->
            runCatching { Mobile.normalizeConfig(raw) }.getOrNull()?.let { normalized ->
                if (normalized != raw) store.config = normalized
            }
        }
        applyCrashReporting(store.crashReporting)
    }

    fun applyCrashReporting(enabled: Boolean) {
        if (!enabled || BuildConfig.SENTRY_DSN.isBlank()) {
            Sentry.close()
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.isSendDefaultPii = false
            options.tracesSampleRate = 0.0
            options.release = "dv-android@${BuildConfig.VERSION_NAME}"
            options.environment = if (BuildConfig.DEBUG) "dev" else "prod"
            options.setBeforeSend { event, _ ->
                fun hot(s: String?) =
                    !s.isNullOrEmpty() &&
                        (s.contains("dotvpn://", ignoreCase = true) || s.contains("Bearer ", ignoreCase = true))
                if (hot(event.message?.message)) return@setBeforeSend null
                event.exceptions?.forEach { ex ->
                    if (hot(ex.value)) return@setBeforeSend null
                }
                event.breadcrumbs?.forEach { crumb ->
                    if (hot(crumb.message)) return@setBeforeSend null
                }
                event.user = null
                event.request = null
                event.serverName = null
                event
            }
        }
    }
}
