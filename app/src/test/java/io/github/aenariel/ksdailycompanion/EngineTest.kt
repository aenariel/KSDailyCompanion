package io.github.aenariel.ksdailycompanion

import io.github.aenariel.ksdailycompanion.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Checks the port of the agenda's rules against known dates of the 28-day cycle anchored on 2026-11-09. */
class EngineTest {
    private val shared = Shared(
        "2026-11-09",
        listOf(
            CycleEvent("brawl", "🌟", "Alliance Brawl", 0, 7, 28, showDay = true, maxDay = 6, endTime = "12 UTC"),
            CycleEvent("swordland", "🏯", "Swordland Showdown", 0, 7, 14, on = false),
            CycleEvent("triclash", "🏤", "Tri-Alliance Clash", 14, 6, 28, on = false),
            CycleEvent("castle", "🌟 🏰", "Castle Battle", 12, 1, 28, startTime = "12 UTC"),
        ),
        listOf(SpecialEvent("fantasy1", "✨", "Fantasy Theater", "2026-11-09", "2026-11-15")),
    )
    private val base = freshState(shared, 1).let {
        it.copy(kingdoms = mapOf("My kingdom" to defaultKingdom("young")), alliances = mapOf("My alliance" to defaultAlliance("My kingdom")))
    }
    private val view = viewFor(base, "My kingdom")
    private fun d(iso: String) = dnum(iso)!!
    private fun ms(iso: String, hhmm: String): Long {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return d(iso) * DAY_MS + (h * 60 + m) * MIN_MS
    }

    @Test fun dates() {
        assertEquals(0, wday(d("2026-11-09")))          // Monday
        assertEquals(3, wday(d("2026-11-26")))          // Thursday
        assertEquals("2026-11-26", diso(d("2026-11-26")))
        assertEquals(20 * 60, parseTime("20 UTC"))
        assertEquals(12 * 60 + 30, parseTime("12:30 UTC"))
        assertEquals(null, parseTime(""))
    }

    @Test fun cycle() {
        val brawl = view.event("brawl")!!
        assertEquals(1, cycleHit(view, brawl, d("2026-11-09"))!!.dayIdx)
        assertEquals(7, cycleHit(view, brawl, d("2026-11-15"))!!.dayIdx)
        assertEquals(null, cycleHit(view, brawl, d("2026-11-16")))
        assertEquals(1, cycleHit(view, brawl, d("2026-12-07"))!!.dayIdx)
        val tri = view.event("triclash")!!
        assertEquals(d("2026-11-23"), cycleHit(view, tri, d("2026-11-26"))!!.start)
        assertEquals(4, cycleHit(view, tri, d("2026-11-26"))!!.dayIdx)
    }

    @Test fun rules() {
        val trap = Rule("every", date = "2026-11-12", every = 2)
        assertTrue(ruleHits(view, trap, d("2026-11-14")))
        assertTrue(!ruleHits(view, trap, d("2026-11-13")))
        val sl = Rule("event", ev = "swordland", from = 7, to = 7)
        assertTrue(ruleHits(view, sl, d("2026-11-15")))
        assertTrue(!ruleHits(view, sl, d("2026-11-22")))
        assertTrue(ruleHits(view, sl, d("2026-11-29")))
        val tri = Rule("event", ev = "triclash", from = 6, to = 6)
        assertTrue(ruleHits(view, tri, d("2026-11-28")))
        val reg = Rule("event", ev = "swordland", from = 3, to = 4, unless = "triclash")
        assertTrue(ruleHits(view, reg, d("2026-11-11")))
        assertTrue(!ruleHits(view, reg, d("2026-11-25")))   // Tri-Alliance week
    }

    @Test fun legionPicks() {
        val pingu = Character("c1", "Pingu", "My alliance", usual = mapOf("Swordland Showdown" to "sl-l2"))
        val other = Character("c2", "Alt", "My alliance")
        var st = base.copy(characters = listOf(pingu, other), settings = Settings(defaultLeads = listOf(720, 120)))

        val occ = occurrences(st, d("2026-11-29"), d("2026-11-29")).filter { it.key == "g:Swordland Showdown" }
        val p = occ.first { it.charId == "c1" }
        assertEquals(Kind.TIMED, p.kind)
        assertEquals("Swordland (Legion 2)", p.title)
        assertEquals(ms("2026-11-29", "12:00"), p.atMs)
        assertEquals(Kind.CHOICE_PENDING, occ.first { it.charId == "c2" }.kind)

        // Registration days 3-4 of the week that starts 2026-11-23: questions for both characters
        val prompts = dueBetween(st, ms("2026-11-24", "23:00"), ms("2026-11-26", "23:00")).filterIsInstance<Due.Prompt>()
        assertEquals(2 * 2 * 2, prompts.size)   // 2 characters x 2 groups x 2 days
        assertEquals(ms("2026-11-25", "12:00"), prompts.first().fireAt)

        // Explicit "not playing" for Pingu: no reminders, no more questions for that occurrence
        st = st.copy(picks = mapOf("c1" to mapOf(occKey("Swordland Showdown", d("2026-11-23")) to NONE)))
        val due = dueBetween(st, ms("2026-11-25", "00:00"), ms("2026-11-30", "00:00"))
        assertTrue(due.none { it is Due.Reminder && it.occ.charId == "c1" && it.occ.key == "g:Swordland Showdown" })
        assertTrue(due.none { it is Due.Prompt && it.charId == "c1" && it.group.group == "Swordland Showdown" })

        // Pick Legion 1 (0 UTC on Sunday): reminders at 12:00 and 22:00 UTC on Saturday
        st = st.copy(picks = mapOf("c1" to mapOf(occKey("Swordland Showdown", d("2026-11-23")) to "sl-l1")))
        val r = dueBetween(st, ms("2026-11-28", "00:00"), ms("2026-11-29", "00:00"))
            .filterIsInstance<Due.Reminder>().filter { it.occ.key == "g:Swordland Showdown" }
        assertEquals(listOf(ms("2026-11-28", "12:00"), ms("2026-11-28", "22:00")), r.map { it.fireAt })
    }

    @Test fun mergedReminders() {
        val st = base.copy(characters = listOf(Character("a", "A", "My alliance"), Character("b", "B", "My alliance")))
        val traps = dueBetween(st, ms("2026-11-28", "00:00"), ms("2026-11-28", "23:59"))
            .filterIsInstance<Due.Reminder>().filter { it.occ.key == groupKey(BEAR_GROUP) && it.occ.title == "Trap 1" }
        // Trap 1 at 20 UTC on the 28th: 12h before (08:00) and 2h before (18:00), one reminder for both characters
        assertEquals(listOf(ms("2026-11-28", "08:00"), ms("2026-11-28", "18:00")), traps.map { it.fireAt })
        assertEquals(listOf("A (My alliance, My kingdom)", "B (My alliance, My kingdom)"), traps.first().charNames)
        // Castle Battle (day 13 of the cycle = 2026-11-21, 12 UTC) has a start time, so it is reminded too
        val castle = occurrences(st, d("2026-11-21"), d("2026-11-21")).filter { it.key == "c:castle" }
        assertEquals(2, castle.size)
        assertEquals(ms("2026-11-21", "12:00"), nextFire(st.copy(characters = st.characters.take(1),
            settings = Settings(defaultLeads = listOf(0))), ms("2026-11-21", "11:59")))
    }

    @Test fun formatting() {
        assertEquals(listOf(720, 120, 15), parseLeads("2h, 12h 15m"))
        assertEquals(listOf(90), parseLeads("1h30m"))
        assertEquals(listOf(180), parseLeads("3"))
        assertEquals(null, parseLeads("soon"))
        assertEquals("12h, 1h30m, 15m", fmtLeads(listOf(720, 90, 15)))
        val lis = ZoneId.of("Europe/Lisbon")
        assertEquals("12:00 UTC · 12:00 local", fmtBoth(ms("2026-11-29", "12:00"), lis))
        assertEquals("12:00 UTC · 13:00 local", fmtBoth(ms("2026-07-05", "12:00"), lis))
        assertEquals("23:30 UTC · 00:30 (+1d) local", fmtBoth(ms("2026-07-05", "23:30"), lis))
        assertEquals("Thu 26/11", dayLabel(d("2026-11-26")))
        val labels = dayEvents(view, d("2026-11-15")).map { dayEventLabel(it) }
        assertEquals(listOf("🌟 Alliance Brawl (day 6/7)", "✨ Fantasy Theater (day 7/7)"), labels)
    }

    @Test fun labelsColorsAndGlyphs() {
        assertEquals("#1130", kingdomTag("1130"))
        assertEquals("#1130", kingdomTag("#1130"))
        val st = base.copy(
            alliances = mapOf("Ffs" to defaultAlliance("1130")),
            kingdoms = mapOf("1130" to defaultKingdom("old")),
            characters = listOf(Character("a", "Pingu", "Ffs"), Character("b", "Pingu", "Ffs", color = PALETTE[0])),
        )
        assertEquals("Pingu (Ffs, #1130)", charLabel(st, st.characters[0]))
        val colored = withColors(st)
        assertEquals(listOf(PALETTE[1], PALETTE[0]), colored.characters.map { it.color })
        assertEquals("🌟 ⚔️", displayEmoji("🌟 \uE044"))
        assertEquals("🛡️ Armament Competition", withEmoji("\uE010", "Armament Competition"))
        assertEquals("Odd", withEmoji("\uE0FF", "Odd"))
    }

    @Test fun kingdomVariantAndDates() {
        val old = defaultKingdom("old")
        assertTrue(old.cycle.first { it.id == "fishing" }.on)          // Gen 4+ keeps the Fishing Tournament
        assertTrue(old.cycle.first { it.id == "divination" }.on)
        assertEquals("Champion's Way", old.cycle.first { it.id == "desert" }.name)
        val moved = old.copy(cycle = old.cycle.map { if (it.id == "desert") it.copy(start = 5) else it })
        val young = withVariant(moved, false)
        assertEquals("Desert Trial", young.cycle.first { it.id == "desert" }.name)
        assertEquals(5, young.cycle.first { it.id == "desert" }.start)   // dates survive the switch
        // "Desert Trial started on 2026-11-25" -> offset 2 in a 14-day cycle from 2026-11-09
        assertEquals(2, startFromDate(view, d("2026-11-25"), 14))
        val desert = viewFor(base, "My kingdom").event("desert")!!
        assertEquals(d("2026-11-25"), currentOrNext(view, desert, d("2026-11-27")).start)
        assertEquals(d("2026-12-09"), currentOrNext(view, desert, d("2026-11-28")).start)
    }

    @Test fun groupLeadsMigration() {
        val st = base.copy(settings = Settings(leads = mapOf("t:trap1" to listOf(30), "t:trap2" to listOf(30), "c:castle" to listOf(60))))
        val m = withGroupLeads(st)
        assertEquals(mapOf(groupKey(BEAR_GROUP) to listOf(30), "c:castle" to listOf(60)), m.settings.leads)
        assertEquals(m, withGroupLeads(m))
        assertTrue(freshState(shared, 1).alliances.isEmpty() && freshState(shared, 1).kingdoms.isEmpty())
    }
}
