// SPDX-License-Identifier: AGPL-3.0-only

package dot.vpn.payam

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dot.vpn.payam.ui.AccountScreen
import dot.vpn.payam.ui.HomeScreen
import dot.vpn.payam.ui.RelayScreen
import dot.vpn.payam.ui.SettingsScreen
import dot.vpn.payam.ui.SplashLockup
import dot.vpn.payam.ui.theme.DotVpnTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile
import java.util.Locale

private enum class Tab { Home, Account, Relay, Settings }

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private val scope: CoroutineScope = MainScope()
    private var invited by mutableStateOf(false)
    private var needsRelay by mutableStateOf(false)
    private var message by mutableStateOf<Int?>(null)
    private var themePref by mutableStateOf(ThemePref.SYSTEM)
    private var langPref by mutableStateOf(LangPref.SYSTEM)
    private var confirmUri by mutableStateOf<String?>(null)
    private var confirmAction by mutableStateOf(DeepLinkAction.Ignore)
    private var splashReady = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("dotvpn", MODE_PRIVATE)
        val lang = runCatching { LangPref.valueOf(prefs.getString("lang", LangPref.SYSTEM.name)!!) }
            .getOrDefault(LangPref.SYSTEM)
        super.attachBaseContext(applyLang(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !splashReady }
        super.onCreate(savedInstanceState)
        store = Store(this)
        themePref = store.themePref
        langPref = store.langPref
        invited = store.config?.let { Mobile.configured(it) } == true
        if (invited) store.setupDone = true
        handleIncoming(intent?.dataString, fromIntent = true)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (themePref) {
                ThemePref.SYSTEM -> systemDark
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            val persian = when (langPref) {
                LangPref.FA -> true
                LangPref.EN -> false
                LangPref.SYSTEM -> resources.configuration.locales[0].language == "fa"
            }
            var showBrandSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                // Dismiss the blank system SplashScreen as soon as Compose brand splash draws.
                splashReady = true
                delay(1400)
                showBrandSplash = false
            }
            DotVpnTheme(darkTheme = dark, persian = persian) {
                Surface(Modifier.fillMaxSize()) {
                    if (showBrandSplash) {
                        SplashLockup()
                    } else {
                        AppRoot(
                            invited = invited,
                            needsRelay = needsRelay,
                            message = message,
                            store = store,
                            onInvite = { importFrom(it, force = true) },
                            onRelay = { addRelayFrom(it, force = true) },
                            onTheme = {
                                themePref = it
                                store.themePref = it
                            },
                            onLang = {
                                langPref = it
                                store.langPref = it
                                recreate()
                            },
                            onCrashReporting = {
                                store.crashReporting = it
                                (application as App).applyCrashReporting(it)
                            },
                            onReplaceAccount = {
                                invited = false
                                needsRelay = false
                                message = null
                            },
                            onLogout = { logout() },
                            onExit = { finish() },
                        )
                    }
                    confirmUri?.let { uri ->
                        ConfirmDeepLinkDialog(
                            action = confirmAction,
                            onAccept = {
                                val u = uri
                                confirmUri = null
                                when (confirmAction) {
                                    DeepLinkAction.ConfirmReplace -> importFrom(u, force = true)
                                    DeepLinkAction.ConfirmRelay -> addRelayFrom(u, force = true)
                                    else -> Unit
                                }
                            },
                            onDismiss = { confirmUri = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent.dataString, fromIntent = true)
    }

    override fun onStart() {
        super.onStart()
        checkAccount()
    }

    override fun onDestroy() {
        scope.cancel()
        sweepReports(this)
        super.onDestroy()
    }

    private fun handleIncoming(text: String?, fromIntent: Boolean) {
        if (text.isNullOrBlank()) return
        val configured = store.config?.let { Mobile.configured(it) } == true
        when (val action = deepLinkAction(text, configured)) {
            DeepLinkAction.AutoApply -> {
                if (findInvite(text) != null) importFrom(text, force = true)
                else addRelayFrom(text, force = true)
            }
            DeepLinkAction.ConfirmReplace, DeepLinkAction.ConfirmRelay -> {
                if (fromIntent) {
                    confirmUri = text
                    confirmAction = action
                }
            }
            DeepLinkAction.Ignore -> Unit
        }
    }

    private fun importFrom(text: String?, force: Boolean) {
        val uri = findInvite(text) ?: return
        if (!force) {
            handleIncoming(text, fromIntent = false)
            return
        }
        scope.launch {
            try {
                var imported = withContext(Dispatchers.IO) { Mobile.importInvite(uri) }
                val relay = findRelayInvite(text)
                if (relay != null) {
                    imported = withContext(Dispatchers.IO) {
                        runCatching { Mobile.addRelay(imported, relay) }.getOrDefault(imported)
                    }
                }
                val cfg = withContext(Dispatchers.IO) {
                    runCatching { Mobile.normalizeConfig(imported) }.getOrDefault(imported)
                }
                store.config = cfg
                store.setupDone = true
                val blocked = relay == null && withContext(Dispatchers.IO) { Mobile.needsRelay(cfg) }
                needsRelay = blocked
                invited = !blocked
                message = if (blocked) null else R.string.invite_saved
            } catch (_: Exception) {
                message = R.string.invite_bad
            }
        }
    }

    private fun addRelayFrom(text: String?, force: Boolean) {
        val cfg = store.config
        val relay = findRelayInvite(text) ?: text.orEmpty().trim()
        if (relay.isEmpty()) return
        if (cfg == null) {
            message = R.string.relay_bad
            return
        }
        if (!force && Mobile.configured(cfg)) {
            confirmUri = text
            confirmAction = DeepLinkAction.ConfirmRelay
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { Mobile.addRelay(cfg, relay) }
            }.onSuccess {
                store.config = it
                needsRelay = false
                invited = true
                message = R.string.invite_saved
            }.onFailure { message = R.string.relay_bad }
        }
    }

    private fun logout() {
        val cfg = store.config ?: return
        scope.launch {
            if (DotvpnService.status.value !is VpnState.Off) DotvpnService.stop(this@MainActivity)
            val next = withContext(Dispatchers.IO) { runCatching { Mobile.logout(cfg) }.getOrNull() }
            store.config = next
            store.accountCache = null
            invited = false
            needsRelay = false
            message = null
        }
    }

    private fun checkAccount() {
        val cfg = store.config ?: return
        scope.launch {
            val next = withContext(Dispatchers.IO) { runCatching { Mobile.validateAccount(cfg) }.getOrNull() }
            if (next == null || next == cfg || store.config != cfg) return@launch
            if (DotvpnService.status.value !is VpnState.Off) DotvpnService.stop(this@MainActivity)
            store.config = next
            store.accountCache = null
            invited = Mobile.configured(next)
            needsRelay = false
            message = R.string.invite_revoked
        }
    }

    companion object {
        fun applyLang(base: Context, pref: LangPref): Context {
            val tag = when (pref) {
                LangPref.EN -> "en"
                LangPref.FA -> "fa"
                LangPref.SYSTEM -> return base
            }
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}

@Composable
private fun ConfirmDeepLinkDialog(
    action: DeepLinkAction,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (action) {
        DeepLinkAction.ConfirmReplace -> R.string.invite_confirm_replace_title
        else -> R.string.invite_confirm_relay_title
    }
    val body = when (action) {
        DeepLinkAction.ConfirmReplace -> R.string.invite_confirm_replace_body
        else -> R.string.invite_confirm_relay_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.action_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AppRoot(
    invited: Boolean,
    needsRelay: Boolean,
    message: Int?,
    store: Store,
    onInvite: (String?) -> Unit,
    onRelay: (String) -> Unit,
    onTheme: (ThemePref) -> Unit,
    onLang: (LangPref) -> Unit,
    onCrashReporting: (Boolean) -> Unit,
    onReplaceAccount: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlayBack by remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler(enabled = overlayBack != null || (invited && tab != Tab.Home)) {
        val dismiss = overlayBack
        if (dismiss != null) dismiss()
        else if (tab != Tab.Home) tab = Tab.Home
        else onExit()
    }

    Scaffold(
        bottomBar = {
            if (invited) {
                NavigationBar {
                    TabNav(Tab.Home, tab, R.drawable.ic_power, R.string.tab_home) { tab = Tab.Home }
                    TabNav(Tab.Account, tab, R.drawable.ic_account, R.string.tab_account) { tab = Tab.Account }
                    TabNav(Tab.Relay, tab, R.drawable.ic_relay, R.string.tab_relay) { tab = Tab.Relay }
                    TabNav(Tab.Settings, tab, R.drawable.ic_settings, R.string.tab_settings) { tab = Tab.Settings }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when {
                !invited || tab == Tab.Home -> HomeScreen(
                    store = store,
                    invited = invited,
                    needsRelay = needsRelay,
                    message = message,
                    onInvite = onInvite,
                    onRelay = onRelay,
                    onHomeAdIssuer = { issuer, relayId ->
                        store.homeAdIssuer = issuer
                        store.homeAdRelayId = relayId
                    },
                    onOverlayBack = { overlayBack = it },
                )
                tab == Tab.Account -> AccountScreen(
                    store = store,
                    onReplace = {
                        tab = Tab.Home
                        onReplaceAccount()
                    },
                    onLogout = onLogout,
                )
                tab == Tab.Relay -> RelayScreen(store = store)
                tab == Tab.Settings -> SettingsScreen(
                    store = store,
                    onTheme = onTheme,
                    onLang = onLang,
                    onCrashReporting = onCrashReporting,
                    onOverlayBack = { overlayBack = it },
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabNav(id: Tab, current: Tab, icon: Int, label: Int, onClick: () -> Unit) {
    NavigationBarItem(
        selected = current == id,
        onClick = onClick,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}
