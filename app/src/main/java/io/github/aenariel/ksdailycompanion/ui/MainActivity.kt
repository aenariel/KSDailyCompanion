package io.github.aenariel.ksdailycompanion.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.aenariel.ksdailycompanion.data.Log
import io.github.aenariel.ksdailycompanion.data.Store
import io.github.aenariel.ksdailycompanion.model.CompanionState
import io.github.aenariel.ksdailycompanion.notify.Notifier
import io.github.aenariel.ksdailycompanion.notify.Scheduler

/** Holds the state for the UI; every edit is saved and re-plans the alarm. */
class AppModel(private val ctx: Context) {
    var state by mutableStateOf(Store.load(ctx))
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var message by mutableStateOf<String?>(null)
    var notificationsAllowed by mutableStateOf(Notifier.allowed(ctx))
    var accent by mutableStateOf(UiPrefs.accent(ctx))
        private set

    fun chooseAccent(a: Accent) { accent = a; UiPrefs.setAccent(ctx, a); Log.i("Accent: ${a.label}") }

    fun update(why: String, f: (CompanionState) -> CompanionState) {
        val next = f(state)
        if (next == state) return
        state = next
        Log.i("Edit: $why")
        Store.save(ctx, next)
        Scheduler.replan(ctx, why)
    }

    fun reload() {
        state = Store.load(ctx)
        now = System.currentTimeMillis()
        notificationsAllowed = Notifier.allowed(ctx)
    }

    fun refreshEvents(quiet: Boolean) {
        val snapshot = state
        Thread {
            val (fresh, msg) = Store.fetchEvents(snapshot)
            if (!msg.startsWith("Could not")) Store.setLastFetch(ctx, System.currentTimeMillis())
            Handler(Looper.getMainLooper()).post {
                if (fresh != null) update("events update") { it.copy(shared = fresh.shared, sharedVersion = fresh.sharedVersion) }
                if (!quiet || fresh != null) message = msg
            }
        }.start()
    }

    fun toast(msg: String) { message = msg }

    /** Global events exactly as published, kingdom events back to their default format; dates kept. */
    fun resetFormats() {
        val url = state.settings.eventsUrl
        Thread {
            val online = Store.fetchEventsFile(url)
            val (ver, shared) = online ?: Store.bundledShared(ctx)
            Handler(Looper.getMainLooper()).post {
                update("reset event formats (events v$ver, ${if (online != null) "online" else "bundled"})") { io.github.aenariel.ksdailycompanion.model.resetFormats(it, shared, ver) }
                toast("Event formats reset (events v$ver${if (online == null) ", offline copy" else ""}). Dates kept.")
            }
        }.start()
    }

    fun eraseAll() {
        Store.eraseAll(ctx)
        reload()
        Scheduler.replan(ctx, "erase all")
        toast("All data erased")
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.init(this)
        Log.i("App opened")
        Notifier.channels(this)
        enableEdgeToEdge()
        model = AppModel(applicationContext)
        Scheduler.replan(applicationContext, "app open")
        model.refreshEvents(quiet = true)
        setContent {
            KsTheme(model.accent) { App(model) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.reload()
    }
}

enum class Tab(val icon: String, val label: String) {
    AGENDA("📅", "Agenda"), CHARACTERS("🤴🏻", "Characters"), ALLIANCES("🛡️", "Alliances"), KINGDOMS("🏰", "Kingdoms"), SETTINGS("⚙️", "Settings")
}

@Composable
private fun App(model: AppModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.AGENDA) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(model.message) {
        model.message?.let { snack.showSnackbar(it); model.message = null }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            AppNavBar(tab) { tab = it }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            Tab.AGENDA -> AgendaScreen(model, m, onGo = { tab = it })
            Tab.CHARACTERS -> CharactersScreen(model, m)
            Tab.ALLIANCES -> AlliancesScreen(model, m)
            Tab.KINGDOMS -> KingdomsScreen(model, m)
            Tab.SETTINGS -> SettingsScreen(model, m)
        }
    }
}

/** Bottom tabs. When the labels would not fit (narrow screen or large text) only the open tab shows its name. */
@Composable
internal fun AppNavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    // "Characters" needs about 78 dp at normal text; without that room the tabs show icons only (the screen title names the tab)
    val roomy = cfg.screenWidthDp / fontScale >= 390f
    NavigationBar {
        for (t in Tab.entries) NavigationBarItem(
            selected = tab == t, onClick = { onTab(t) },
            icon = { Text(t.icon) },
            label = if (roomy) ({ Text(t.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }) else null,
        )
    }
}
