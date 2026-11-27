package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import io.github.aenariel.ksdailycompanion.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.ZoneId

/**
 * Renders the parts that break with large text, at several widths and text sizes, so they can be looked at
 * before a release (images in app/src/test/snapshots/images after `recordPaparazziDebug`; CI uploads them).
 * Sizes: a 360 dp phone at normal, 1.3x and 2x text (Android's largest), and a 411 dp phone at normal text.
 * Paparazzi scales each image down to 1000 px on its longest side, so each snapshot holds about one screen.
 */
@RunWith(Parameterized::class)
class LayoutSnapshotTest(private val label: String, private val device: DeviceConfig) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = listOf(
            arrayOf("360dp-1.0x", DeviceConfig.NEXUS_5.copy(fontScale = 1.0f, nightMode = NightMode.NIGHT)),
            arrayOf("360dp-1.3x", DeviceConfig.NEXUS_5.copy(fontScale = 1.3f, nightMode = NightMode.NIGHT)),
            arrayOf("360dp-2.0x", DeviceConfig.NEXUS_5.copy(fontScale = 2.0f, nightMode = NightMode.NIGHT)),
            arrayOf("411dp-1.0x", DeviceConfig.PIXEL_5.copy(fontScale = 1.0f, nightMode = NightMode.NIGHT)),
        )
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device,
        theme = "android:Theme.Material.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,   // full width, as tall as the content
        showSystemUi = false,
    )

    private val shared = Shared(
        "2026-11-09",
        listOf(
            CycleEvent("armament2", "🛡️", "Armament Competition", 4, 2, 14),
            CycleEvent("mobilization", "🏆", "Alliance Mobilization", 14, 7, 28),
            CycleEvent("triclash", "🏤", "Tri-Alliance Clash", 14, 6, 28),
            CycleEvent("kvkmatch", "🌟 🏰", "KvK: Matchmaking Phase", 19, 2, 28, lastDay = false, highlight = true),
        ),
        emptyList(),
    )

    /** Two kingdoms, three alliances, four characters (one with a long name), Friday 27/11 and Saturday 28/11 2026. */
    private val state: CompanionState = freshState(shared, 1).let { s ->
        s.copy(
            kingdoms = mapOf("1130" to defaultKingdom("old"), "2116" to defaultKingdom("young")),
            alliances = mapOf("Ffs" to defaultAlliance("1130"), "VAR" to defaultAlliance("2116"), "TKO" to defaultAlliance("2116")),
            characters = listOf(
                Character("a", "Pingu", "Ffs", color = PALETTE[1]),
                Character("b", "Pingu", "VAR", color = PALETTE[6]),
                Character("c", "GinSoakedLady", "TKO", color = PALETTE[2]),
                Character("d", "Mara", "VAR", color = PALETTE[4]),
            ),
        )
    }
    private val fri = dnum("2026-11-27")!!
    private val now = fri * DAY_MS + 5 * 60 * MIN_MS

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        KsTheme(Accent.BLUE) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp)) { content() }
        }
    }

    private val byId get() = state.characters.associateBy { it.id }
    private val zone = ZoneId.of("Europe/Lisbon")

    @Composable
    private fun Events(n: Int) {
        GroupLabel("🌍 Global")
        for (e in globalEvents(state, n)) EventRow(e)
        for (k in listOf("1130", "2116")) {
            val ev = kingdomEvents(state, k, n)
            if (ev.isEmpty()) continue
            GroupLabel("🏰 " + kingdomTag(k))
            for (e in ev) EventRow(e)
        }
    }

    @Composable
    private fun Scheduled(n: Int) {
        GroupLabel("🕒 Scheduled")
        for ((o, chars) in mergeRows(occurrences(state, n, n), byId)) OccurrenceRow(o, chars, now, zone) {}
    }

    @Test
    fun agendaEvents() {
        paparazzi.snapshot {
            Screen {
                ScreenHeader(Tab.AGENDA, "Next 7 days", "Game days are UTC days. Times: UTC · your local time (Europe/Lisbon).")
                SectionCard(dayLabel(fri)) { Events(fri) }
            }
        }
    }

    @Test
    fun agendaScheduledFriday() {
        paparazzi.snapshot { Screen { SectionCard(dayLabel(fri)) { Scheduled(fri) } } }
    }

    @Test
    fun agendaScheduledSaturday() {
        paparazzi.snapshot { Screen { SectionCard(dayLabel(fri + 1)) { Scheduled(fri + 1) } } }
    }

    @Test
    fun trialsAndTabs() {
        paparazzi.snapshot {
            Screen {
                SectionCard(dayLabel(fri)) { GroupLabel("🔮 Mystic Trials"); TrialsTable(trialsOn(state, fri)) }
                Spacer(Modifier.height(12.dp))
                AppNavBar(Tab.AGENDA) {}
                Spacer(Modifier.height(12.dp))
                AppNavBar(Tab.CHARACTERS) {}
            }
        }
    }

    @Test
    fun settingsRows() {
        paparazzi.snapshot {
            Screen {
                ScreenHeader(Tab.SETTINGS)
                SectionCard("Reminders") {
                    ValueRow("Default: before each event", "12h, 2h") {}
                    ValueRow("🏯 Swordland", "default") {}
                    ValueRow("🌟 🏰 KvK Castle Battle", "default") {}
                }
                SectionCard("Questions") {
                    ValueRow("Ask which legion at", "12:00 UTC · 12:00 local", "On registration days, for each character without a pick for that week.") {}
                    ValueRow("Ask the Eternity's Reach time at", "12:00 UTC · 12:00 local", "The day before, for each character without a time.") {}
                }
            }
        }
    }

    @Test
    fun settingsArena() {
        paparazzi.snapshot {
            Screen {
                SectionCard("Arena") {
                    SwitchRow("Remind me before the daily reset", true, "Arena attempts reset at 00:00 UTC") {}
                    ValueRow("Before the reset", "15m", "23:45 UTC · 23:45 local") {}
                }
                CharDots(state.characters, suffix = "legion not set")
            }
        }
    }
}
