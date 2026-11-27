package io.github.aenariel.ksdailycompanion.model

import java.time.LocalDate
import kotlin.math.max

// Date math and rules ported from KSDailyAgenda (dnum, diso, wday, cycleHit, specialHit, ruleHits, hourOf).
// Days are UTC day numbers (days since 1970-01-01), as in the agenda.

const val DAY_MS = 86_400_000L
const val MIN_MS = 60_000L

fun dnum(iso: String): Int? = try { LocalDate.parse(iso.trim()).toEpochDay().toInt() } catch (e: Exception) { null }
fun diso(n: Int): String = LocalDate.ofEpochDay(n.toLong()).toString()
/** 0 = Monday. */
fun wday(n: Int): Int = mod(n + 3, 7)
fun mod(a: Int, b: Int): Int = ((a % b) + b) % b
fun dayOf(ms: Long): Int = Math.floorDiv(ms, DAY_MS).toInt()

/** "20 UTC", "20:30 UTC", "7" -> minutes after 00:00 UTC; null when there is no hour. */
fun parseTime(t: String): Int? {
    val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(t) ?: return null
    val h = m.groupValues[1].toInt()
    val mm = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (h > 24 || mm > 59) return null
    return h * 60 + mm
}

fun timeString(min: Int): String = "%02d:%02d UTC".format(min / 60, min % 60)

/** Order of the agenda's default event list, used to sort events for display. */
val ORDER = listOf(
    "brawl", "merchant", "swordland", "championship", "armament1", "fishing", "sanctuary", "desert", "officer1", "armament2",
    "divination", "officer2", "hallheroes", "sg", "glaives", "vikings", "eternity", "roulette", "beasts", "brew", "allout",
    "castle", "cesares", "mobilization", "triclash", "kvkmatch", "kvkprep", "kvkbattle",
)

private fun orderOf(id: String): Int = ORDER.indexOf(id).let { if (it < 0) 999 else it }

/** Shared events plus one kingdom's own events (the agenda's makeView). */
class View(anchor: String, cycle: List<CycleEvent>, val special: List<SpecialEvent>) {
    val anchorN: Int = dnum(anchor) ?: 0
    val cycle: List<CycleEvent> = cycle.sortedBy { orderOf(it.id) }
    fun event(id: String): CycleEvent? = cycle.firstOrNull { it.id == id }
}

fun viewFor(state: CompanionState, kingdomName: String): View {
    val k = state.kingdoms[kingdomName] ?: Kingdom()
    val ids = state.shared.cycle.map { it.id }.toSet()
    return View(state.shared.anchor, state.shared.cycle + k.cycle.filter { it.id !in ids }, state.shared.special + k.special)
}

data class Hit(val start: Int, val end: Int, val dayIdx: Int)

fun cycleHit(v: View, ev: CycleEvent, n: Int): Hit? {
    val every = max(1, ev.every)
    val len = max(1, ev.len)
    val off = mod(n - v.anchorN - ev.start, every)
    return if (off < len) Hit(n - off, n - off + len - 1, off + 1) else null
}

fun specialHit(sp: SpecialEvent, n: Int): Hit? {
    val s = dnum(sp.from) ?: return null
    val e = (if (sp.to.isBlank()) null else dnum(sp.to)) ?: s
    return if (n in s..e) Hit(s, e, n - s + 1) else null
}

fun ruleHits(v: View, rule: Rule, n: Int): Boolean = when (rule.type) {
    "every" -> dnum(rule.date)?.let { mod(n - it, max(1, rule.every)) == 0 } ?: false
    "date" -> dnum(rule.date) == n
    "weekday" -> wday(n) in rule.days
    "event" -> {
        val ev = v.event(rule.ev)
        val h = ev?.let { cycleHit(v, it, n) }
        if (h == null) false else {
            val from = if (rule.from != 0) rule.from else 1
            val to = if (rule.to != 0) rule.to else from
            val blocked = rule.unless.isNotBlank() && v.event(rule.unless)?.let { cycleHit(v, it, n) } != null
            !blocked && h.dayIdx in from..to
        }
    }
    else -> false
}

// ---------------------------------------------------------------- planning

enum class Kind { TIMED, CHOICE_PENDING, CHOICE_NONE }

data class ChoiceInfo(val group: ChoiceGroup, val occKey: String, val options: List<TimedItem>, val selected: String?, val explicit: Boolean)

data class Occurrence(
    val charId: String,
    /** Name with alliance and kingdom, e.g. "Pingu (Ffs, #1130)". */
    val charName: String,
    /** Key for reminder leads: "t:<itemId>", "grp:<group>" (grouped items), "c:<eventId>", "g:<legion group>". */
    val key: String,
    val emoji: String,
    val title: String,
    val day: Int,
    /** Minutes after 00:00 UTC, null for day-only rows (a legion not picked yet). */
    val minute: Int?,
    val kind: Kind = Kind.TIMED,
    val choice: ChoiceInfo? = null,
) {
    val atMs: Long get() = day * DAY_MS + (minute ?: 0) * MIN_MS
}

fun occKey(group: String, startDay: Int): String = "$group@${diso(startDay)}"

/** "1130" -> "#1130"; names that are not a number stay as they are. */
fun kingdomTag(name: String): String = if (name.isNotEmpty() && name.all { it.isDigit() }) "#$name" else name

/** "Pingu (Ffs, #1130)": tells apart characters with the same name. */
fun charLabel(state: CompanionState, ch: Character): String {
    val al = state.alliances[ch.alliance] ?: return "${ch.name} (${ch.alliance})"
    return "${ch.name} (${ch.alliance}, ${kingdomTag(al.kingdom)})"
}

/** Cycle offset so that an event of period [every] starts on [date]. */
fun startFromDate(v: View, date: Int, every: Int): Int = mod(date - v.anchorN, max(1, every))

/** The current run of an event if it is on, else the next one. */
fun currentOrNext(v: View, ev: CycleEvent, n: Int): Hit {
    cycleHit(v, ev, n)?.let { return it }
    val every = max(1, ev.every)
    val s = n + (every - mod(n - v.anchorN - ev.start, every))
    return Hit(s, s + max(1, ev.len) - 1, 0)
}

/** Everything with a date for the characters that are on, days [fromDay, toDay], sorted by time. */
fun occurrences(state: CompanionState, fromDay: Int, toDay: Int): List<Occurrence> {
    val out = ArrayList<Occurrence>()
    val choiceNames = state.choiceGroups.map { it.group }.toSet()
    for (ch in state.characters) {
        if (!ch.on) continue
        val al = state.alliances[ch.alliance] ?: continue
        val v = viewFor(state, al.kingdom)
        val label = charLabel(state, ch)
        for (n in fromDay..toDay) {
            for (it in al.timed) {
                if (!it.on || it.group in choiceNames || "t:${it.id}" in ch.off) continue
                if (!ruleHits(v, it.rule, n)) continue
                val m = parseTime(it.time) ?: continue
                out += Occurrence(ch.id, label, if (it.group.isNotBlank()) groupKey(it.group) else "t:${it.id}", it.emoji, it.label, n, m)
            }
            for (cg in state.choiceGroups) {
                if ("g:${cg.group}" in ch.off) continue
                val items = al.timed.filter { it.on && it.group == cg.group }
                val hits = items.filter { ruleHits(v, it.rule, n) }
                if (hits.isEmpty()) continue
                val start = v.event(cg.ev)?.let { cycleHit(v, it, n) }?.start ?: n
                val key = occKey(cg.group, start)
                val explicit = state.picks[ch.id]?.get(key)
                val sel = explicit ?: ch.usual[cg.group]
                val info = ChoiceInfo(cg, key, items, sel, explicit != null)
                val emoji = items.first().emoji
                when {
                    sel == null -> out += Occurrence(ch.id, label, "g:${cg.group}", emoji, cg.group, n, null, Kind.CHOICE_PENDING, info)
                    sel == NONE -> out += Occurrence(ch.id, label, "g:${cg.group}", emoji, cg.group, n, null, Kind.CHOICE_NONE, info)
                    else -> {
                        val it = hits.firstOrNull { x -> x.id == sel } ?: continue
                        val m = parseTime(it.time) ?: continue
                        out += Occurrence(ch.id, label, "g:${cg.group}", it.emoji, it.label, n, m, Kind.TIMED, info)
                    }
                }
            }
            for (ev in v.cycle) {
                if (!ev.on || ev.startTime.isBlank() || "c:${ev.id}" in ch.off) continue
                val h = cycleHit(v, ev, n) ?: continue
                if (h.start != n) continue
                val m = parseTime(ev.startTime) ?: continue
                out += Occurrence(ch.id, label, "c:${ev.id}", ev.emoji, ev.name, n, m)
            }
        }
    }
    return out.sortedWith(compareBy<Occurrence>({ it.day }, { it.minute ?: -1 }, { it.title }, { it.charName }))
}

/** Something to notify about. Characters in the same alliance or kingdom share one reminder for the same thing. */
sealed class Due {
    abstract val id: String
    abstract val fireAt: Long

    data class Reminder(override val id: String, override val fireAt: Long, val lead: Int, val occ: Occurrence, val charNames: List<String>) : Due()
    data class Prompt(override val id: String, override val fireAt: Long, val charId: String, val charName: String,
                      val group: ChoiceGroup, val occKey: String, val options: List<TimedItem>, val usual: String?) : Due()
    data class Digest(override val id: String, override val fireAt: Long, val day: Int) : Due()
}

fun leadsFor(s: Settings, key: String): List<Int> = s.leads[key] ?: s.defaultLeads

/** All notifications whose time falls in (afterMs, uptoMs]. */
fun dueBetween(state: CompanionState, afterMs: Long, uptoMs: Long): List<Due> {
    val s = state.settings
    val maxLead = (s.defaultLeads + s.leads.values.flatten()).maxOrNull() ?: 0
    val fromDay = dayOf(afterMs) - 1
    val toDay = dayOf(uptoMs) + 1 + (maxLead + 1439) / 1440
    val out = ArrayList<Due>()

    val merged = LinkedHashMap<String, Due.Reminder>()
    for (o in occurrences(state, fromDay, toDay)) {
        if (o.kind != Kind.TIMED) continue
        for (lead in leadsFor(s, o.key)) {
            val fire = o.atMs - lead * MIN_MS
            if (fire <= afterMs || fire > uptoMs) continue
            val id = "r|${o.key}|${o.atMs}|$lead"
            val prev = merged[id]
            merged[id] = if (prev == null) Due.Reminder(id, fire, lead, o, listOf(o.charName))
            else prev.copy(charNames = (prev.charNames + o.charName).distinct())
        }
    }
    out += merged.values

    for (ch in state.characters) {
        if (!ch.on) continue
        val al = state.alliances[ch.alliance] ?: continue
        val v = viewFor(state, al.kingdom)
        for (cg in state.choiceGroups) {
            if ("g:${cg.group}" in ch.off) continue
            val items = al.timed.filter { it.on && it.group == cg.group }
            if (items.isEmpty()) continue
            val ev = v.event(cg.ev) ?: continue
            for (n in fromDay..toDay) {
                val h = cycleHit(v, ev, n) ?: continue
                if (h.dayIdx !in cg.regFrom..cg.regTo) continue
                val key = occKey(cg.group, h.start)
                if (state.picks[ch.id]?.containsKey(key) == true) continue
                val fire = n * DAY_MS + s.promptMin * MIN_MS
                if (fire <= afterMs || fire > uptoMs) continue
                out += Due.Prompt("p|${ch.id}|$key|$n", fire, ch.id, charLabel(state, ch), cg, key, items, ch.usual[cg.group])
            }
        }
    }

    if (s.digestOn && state.characters.any { it.on }) {
        for (n in fromDay..toDay) {
            val fire = n * DAY_MS + s.digestMin * MIN_MS
            if (fire <= afterMs || fire > uptoMs) continue
            out += Due.Digest("d|$n", fire, n)
        }
    }
    return out.sortedBy { it.fireAt }
}

/** The earliest notification after [afterMs], looking [horizonDays] ahead. */
fun nextFire(state: CompanionState, afterMs: Long, horizonDays: Int = 8): Long? =
    dueBetween(state, afterMs, afterMs + horizonDays * DAY_MS).firstOrNull()?.fireAt

// ---------------------------------------------------------------- day view

data class DayEvent(val key: String, val emoji: String, val name: String, val dayIdx: Int, val len: Int, val showDay: Boolean, val maxDay: Int)

/** Events running on day n in one kingdom (the agenda's eventsFor, without the "tomorrow" rows). */
fun dayEvents(v: View, n: Int): List<DayEvent> {
    val list = ArrayList<DayEvent>()
    for (ev in v.cycle) {
        if (!ev.on) continue
        val h = cycleHit(v, ev, n) ?: continue
        list += DayEvent("c:${ev.id}", ev.emoji, ev.name, h.dayIdx, h.end - h.start + 1, ev.showDay, ev.maxDay)
    }
    for (sp in v.special) {
        if (!sp.on) continue
        val h = specialHit(sp, n) ?: continue
        list += DayEvent("s:${sp.id}", sp.emoji, sp.name, h.dayIdx, h.end - h.start + 1, sp.showDay, 0)
    }
    val seen = HashSet<String>()
    return list.filter { seen.add(it.name) }
}

fun dayEventLabel(e: DayEvent): String {
    val d = if (e.maxDay > 0) minOf(e.dayIdx, e.maxDay) else e.dayIdx
    val tail = if (e.len > 1) " (day $d/${e.len})" else ""
    return withEmoji(e.emoji, e.name + tail)
}
