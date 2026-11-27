package io.github.aenariel.ksdailycompanion.model

// Data model. The event and rule fields mirror KSDailyAgenda (index.html / events.json),
// so its events.json and its backups can be read without translation.

data class CycleEvent(
    val id: String,
    val emoji: String = "",
    val name: String,
    val start: Int,
    val len: Int,
    val every: Int,
    val on: Boolean = true,
    val showDay: Boolean = false,
    val maxDay: Int = 0,
    val endTime: String = "",
    val startTime: String = "",
    /** Days of the event to show (empty = every day), e.g. Viking Vengeance 1 and 3. */
    val days: List<Int> = emptyList(),
    /** Mark the last day (false for e.g. KvK Matchmaking). */
    val lastDay: Boolean = true,
    /** Stands out in the agenda (KvK, Castle Battle). */
    val highlight: Boolean = false,
)

data class SpecialEvent(
    val id: String,
    val emoji: String = "",
    val name: String,
    val from: String,
    val to: String = "",
    val on: Boolean = true,
    val showDay: Boolean = false,
)

/** type: "every" (date + every N days), "date", "weekday" (days, 0 = Monday), "event" (ev, from..to, unless). */
data class Rule(
    val type: String,
    val date: String = "",
    val every: Int = 1,
    val ev: String = "",
    val from: Int = 1,
    val to: Int = 0,
    val unless: String = "",
    val days: List<Int> = emptyList(),
)

/** An alliance item with an hour, e.g. "Trap 1 at 20 UTC every 2 days" or "Swordland (Legion 2) at 12 UTC". */
data class TimedItem(
    val id: String,
    val on: Boolean = true,
    val emoji: String = "",
    val label: String,
    val time: String,
    val group: String = "",
    val rule: Rule,
)

/**
 * What events.json publishes: the global events and, optionally, game settings (Mystic Trials,
 * possible hours of Eternity's Reach and of the Friday battles, legion registration and battle days).
 */
data class Shared(
    val anchor: String,
    val cycle: List<CycleEvent>,
    val special: List<SpecialEvent>,
    val trials: List<Trial>? = null,
    val slots: Slots? = null,
    val legions: List<LegionDef>? = null,
)

/** Possible hours (UTC). */
data class Slots(val eternity: List<Int>, val battles: List<Int>)

/** A legion event: registration days and battle day, counted in days of event [ev]. */
data class LegionDef(val group: String, val ev: String, val regFrom: Int, val regTo: Int, val battleDay: Int)

data class Kingdom(val variant: String = "young", val cycle: List<CycleEvent> = emptyList(), val special: List<SpecialEvent> = emptyList())

data class Alliance(val kingdom: String, val timed: List<TimedItem>)

/**
 * A group of alliance items where a character takes part in only one per occurrence (the legions).
 * Registration runs on days regFrom..regTo of event [ev]; the character is asked which item applies.
 */
data class ChoiceGroup(val group: String, val ev: String, val regFrom: Int, val regTo: Int)

data class Character(
    val id: String,
    val name: String,
    val alliance: String,
    val on: Boolean = true,
    /** Keys switched off for this character: "t:<itemId>", "c:<eventId>", "g:<group>". */
    val off: Set<String> = emptySet(),
    /** Usual item per choice group (group -> item id, or "none"). */
    val usual: Map<String, String> = emptyMap(),
    /** ARGB colour shown on the agenda and notifications; 0 = not set yet. */
    val color: Int = 0,
)

/** Character colours, distinct on dark and light backgrounds. */
val PALETTE: List<Int> = listOf(
    0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFD54F, 0xFFBA68C8,
    0xFF4DD0E1, 0xFFFF8A65, 0xFFF06292, 0xFFA1887F, 0xFF90A4AE,
).map { it.toInt() }

/** First palette colour not used by another character (cycles when all are taken). */
fun nextColor(chars: List<Character>): Int {
    val used = chars.map { it.color }.toSet()
    return PALETTE.firstOrNull { it !in used } ?: PALETTE[chars.size % PALETTE.size]
}

/** Gives a colour to characters saved before colours existed. */
fun withColors(s: CompanionState): CompanionState {
    if (s.characters.none { it.color == 0 }) return s
    val out = ArrayList<Character>()
    for (c in s.characters) out += if (c.color != 0) c else c.copy(color = nextColor(out + s.characters.filter { it.color != 0 }))
    return s.copy(characters = out)
}

data class Settings(
    /** Minutes before the start. */
    val defaultLeads: List<Int> = listOf(720, 120),
    /** Per key ("t:<itemId>", "c:<eventId>", "g:<group>") override of the leads. */
    val leads: Map<String, List<Int>> = emptyMap(),
    val digestOn: Boolean = false,
    /** Minutes after 00:00 UTC. */
    val digestMin: Int = 8 * 60,
    /** When the legion question is asked on registration days, minutes after 00:00 UTC. */
    val promptMin: Int = 12 * 60,
    /** When the Eternity's Reach time is asked, the day before, minutes after 00:00 UTC. */
    val soloPromptMin: Int = 12 * 60,
    val eventsUrl: String = DEFAULT_EVENTS_URL,
    val showTrials: Boolean = true,
)

data class CompanionState(
    val schema: Int = SCHEMA,
    val sharedVersion: Int = 0,
    val shared: Shared,
    val kingdoms: Map<String, Kingdom>,
    val alliances: Map<String, Alliance>,
    val characters: List<Character> = emptyList(),
    /** character id -> (occurrence key "group@yyyy-mm-dd" -> item id or "none"). */
    val picks: Map<String, Map<String, String>> = emptyMap(),
    val choiceGroups: List<ChoiceGroup> = DEFAULT_CHOICE_GROUPS,
    val settings: Settings = Settings(),
    /** Sanctuary/Fortress battle hours per alliance and Friday: alliance -> (yyyy-mm-dd -> UTC hours). */
    val battles: Map<String, Map<String, List<Int>>> = emptyMap(),
    /** Mystic Trials by weekday, as in the agenda. */
    val trials: List<Trial> = DEFAULT_TRIALS,
)

/** A Mystic Trial with its troop ratio, on weekdays (0 = Monday). */
data class Trial(val emoji: String, val name: String, val ratio: String, val days: List<Int>)

val DEFAULT_TRIALS = listOf(
    Trial("🎪", "Coliseum", "50/10/40", listOf(0, 1)),
    Trial("🌲", "Forest of Life", "50/15/35", listOf(2, 3)),
    Trial("💎", "Crystal Cave", "60/20/20", listOf(2, 3)),
    Trial("📜", "Knowledge Nexus", "50/20/30", listOf(4, 5)),
    Trial("🌋", "Molten Fort", "60/15/25", listOf(4, 5)),
    Trial("🗼", "Radiant Spire", "50/15/35", listOf(6)),
)

const val SCHEMA = 1
const val NONE = "none"
const val DEFAULT_EVENTS_URL = "https://aenariel.github.io/KSDailyAgenda/events.json"

val DEFAULT_CHOICE_GROUPS = listOf(
    ChoiceGroup("Swordland Showdown", "swordland", 3, 4),
    ChoiceGroup("Tri-Alliance Clash", "triclash", 3, 4),
)

/**
 * Kingdom-specific events, from the agenda's allDefaultEvents() (the shared ones come from events.json).
 * Fishing Tournament and Mystic Divination run in every kingdom; Gen 4+ only turns Desert Trial into Champion's Way.
 */
fun defaultKingdom(variant: String): Kingdom {
    val old = variant == "old"
    return Kingdom(
        variant = if (old) "old" else "young",
        cycle = listOf(
            CycleEvent("fishing", "🎣", "Fishing Tournament", 1, 3, 28),
            desertEvent(old, CycleEvent("desert", "", "", 2, 3, 14)),
            CycleEvent("divination", "🔮", "Mystic Divination", 4, 2, 28),
            CycleEvent("cesares", "👹", "Cesares Fury", 12, 3, 28),
        ),
    )
}

private fun desertEvent(old: Boolean, e: CycleEvent) =
    e.copy(emoji = if (old) "🏆" else "🐺", name = if (old) "Champion's Way" else "Desert Trial")

/** Gen 4+ switch: renames Desert Trial / Champion's Way and keeps every date and on/off as they are. */
fun withVariant(k: Kingdom, old: Boolean): Kingdom =
    k.copy(variant = if (old) "old" else "young", cycle = k.cycle.map { if (it.id == "desert") desertEvent(old, it) else it })

fun defaultLegionTimes(): List<TimedItem> = listOf(
    TimedItem("sl-l1", true, "🏯", "Swordland (Legion 1)", "0 UTC", "Swordland Showdown", Rule("event", ev = "swordland", from = 7, to = 7)),
    TimedItem("sl-l2", true, "🏯", "Swordland (Legion 2)", "12 UTC", "Swordland Showdown", Rule("event", ev = "swordland", from = 7, to = 7)),
    TimedItem("tri-l1", true, "🏤", "Tri-Alliance (Legion 1)", "0 UTC", "Tri-Alliance Clash", Rule("event", ev = "triclash", from = 6, to = 6)),
    TimedItem("tri-l2", true, "🏤", "Tri-Alliance (Legion 2)", "12 UTC", "Tri-Alliance Clash", Rule("event", ev = "triclash", from = 6, to = 6)),
)

fun defaultAlliance(kingdom: String): Alliance = Alliance(
    kingdom,
    listOf(
        TimedItem("trap1", true, "🐻", "Trap 1", "20 UTC", BEAR_GROUP, Rule("every", date = "2026-11-12", every = 2)),
        TimedItem("trap2", true, "🐻", "Trap 2", "12 UTC", BEAR_GROUP, Rule("every", date = "2026-11-12", every = 2)),
    ) + defaultLegionTimes() + vikingItems(),
)

const val VIKING_GROUP = "Viking Vengeance"

/** Alliance event on days 1 and 3 of Viking Vengeance; no hour until the user sets one. */
fun vikingItems(): List<TimedItem> = listOf(
    TimedItem("vv1", true, "🪓", "Day 1", "", VIKING_GROUP, Rule("event", ev = "vikings", from = 1, to = 1)),
    TimedItem("vv3", true, "🪓", "Day 3", "", VIKING_GROUP, Rule("event", ev = "vikings", from = 3, to = 3)),
)

/** Adds the Viking Vengeance items to alliances saved before they existed. */
fun withVikings(s: CompanionState): CompanionState {
    if (s.alliances.values.all { a -> a.timed.any { it.group == VIKING_GROUP } }) return s
    return s.copy(alliances = LinkedHashMap(s.alliances.mapValues { (_, a) -> if (a.timed.any { it.group == VIKING_GROUP }) a else a.copy(timed = a.timed + vikingItems()) }))
}

/** A single-player event where each character picks a time slot (Eternity's Reach). */
data class SoloEvent(val id: String, val name: String, val slots: List<Int>) {
    val key: String get() = "s:$id"
}

val DEFAULT_ETERNITY_SLOTS = listOf(2, 5, 11, 14, 16, 18, 21)

/** Single-player events with their slots (from events.json when it publishes them). */
fun soloEvents(s: CompanionState): List<SoloEvent> =
    listOf(SoloEvent("eternity", "Eternity's Reach", s.shared.slots?.eternity?.takeIf { it.isNotEmpty() } ?: DEFAULT_ETERNITY_SLOTS))

/** Possible hours of the Friday battles (from events.json when it publishes them). */
fun battleSlots(s: CompanionState): List<Int> = s.shared.slots?.battles?.takeIf { it.isNotEmpty() } ?: BATTLE_SLOTS

/** Sanctuary & Fortress battles: every Friday, at the slot most voted at sign-up. */
const val BATTLE_KEY = "b:sanctuary"
const val BATTLE_TITLE = "Sanctuary & Fortress"
const val BATTLE_EMOJI = "🏰"
val BATTLE_SLOTS = listOf(3, 9, 13, 14, 17)
fun isBattleDay(n: Int): Boolean = wday(n) == 4

/** "#1130", "K1130", " 1130 " -> "1130"; other names unchanged. */
fun normKingdom(name: String): String = Regex("^\\s*[#Kk]?\\s*(\\d+)\\s*$").find(name)?.groupValues?.get(1) ?: name

/** Renames kingdoms like "K1130" or "#1130" to "1130" (and the alliances that use them). */
fun withKingdomNames(s: CompanionState): CompanionState {
    if (s.kingdoms.keys.all { normKingdom(it) == it }) return s
    val ks = LinkedHashMap<String, Kingdom>()
    val renamed = HashMap<String, String>()
    for ((name, k) in s.kingdoms) {
        val n = normKingdom(name)
        val target = if (n != name && n !in s.kingdoms && n !in ks) n else name
        ks[target] = k
        if (target != name) renamed[name] = target
    }
    return s.copy(kingdoms = ks, alliances = LinkedHashMap(s.alliances.mapValues { (_, a) -> renamed[a.kingdom]?.let { a.copy(kingdom = it) } ?: a }))
}

/** Keeps each global event's on/off from the current list, like the agenda does. */
fun mergeOnOff(current: Shared, incoming: Shared): Shared {
    val onOff = current.cycle.associate { it.id to it.on }
    return incoming.copy(cycle = incoming.cycle.map { e -> onOff[e.id]?.let { e.copy(on = it) } ?: e })
}

/** Applies the game settings of the published file: Mystic Trials, legion registration days and battle days. */
fun applySharedConfig(s: CompanionState): CompanionState {
    var st = s
    st.shared.trials?.takeIf { it.isNotEmpty() }?.let { st = st.copy(trials = it) }
    val legions = st.shared.legions?.takeIf { it.isNotEmpty() } ?: return st
    val groups = legions.map { ChoiceGroup(it.group, it.ev, it.regFrom, it.regTo) }
    val byGroup = legions.associateBy { it.group }
    val alliances = LinkedHashMap(st.alliances.mapValues { (_, a) ->
        a.copy(timed = a.timed.map { t ->
            val l = byGroup[t.group]
            if (l != null && t.rule.type == "event") t.copy(rule = t.rule.copy(ev = l.ev, from = l.battleDay, to = l.battleDay)) else t
        })
    })
    return st.copy(choiceGroups = groups, alliances = alliances)
}

/** A newer events.json: global events (keeping on/off) and its game settings. */
fun withShared(s: CompanionState, incoming: Shared, version: Int): CompanionState =
    applySharedConfig(s.copy(shared = mergeOnOff(s.shared, incoming), sharedVersion = version))

/**
 * Resets event formats to the published ones: global events become [shared] exactly; each kingdom's default
 * events get the default name, length, period and switches back, keeping their dates. Custom events stay.
 */
fun resetFormats(s: CompanionState, shared: Shared, version: Int): CompanionState {
    val kingdoms = LinkedHashMap<String, Kingdom>()
    for ((name, k) in s.kingdoms) {
        val defs = withVariant(defaultKingdom(k.variant), k.variant == "old").cycle
        val ids = defs.map { it.id }.toSet()
        val mine = k.cycle.associateBy { it.id }
        val cycle = defs.map { d -> mine[d.id]?.let { d.copy(start = mod(it.start, maxOf(1, d.every))) } ?: d } + k.cycle.filter { it.id !in ids }
        kingdoms[name] = k.copy(cycle = cycle)
    }
    return applySharedConfig(s.copy(shared = shared, sharedVersion = version, kingdoms = kingdoms))
}

/** First run: no kingdom, alliance or character; the user creates them. */
fun freshState(shared: Shared, sharedVersion: Int): CompanionState = CompanionState(
    sharedVersion = sharedVersion,
    shared = shared,
    kingdoms = emptyMap(),
    alliances = emptyMap(),
)

const val BEAR_GROUP = "Bear Traps"

/** Group-level reminder key: one setting for every item of a group (e.g. both Bear Traps). */
fun groupKey(group: String): String = "grp:$group"

/** Moves per-item reminder settings ("t:trap1") of grouped items to their group key, once. */
fun withGroupLeads(s: CompanionState): CompanionState {
    val leads = s.settings.leads.toMutableMap()
    var changed = false
    for (a in s.alliances.values) for (t in a.timed) {
        if (t.group.isBlank()) continue
        val v = leads.remove("t:${t.id}") ?: continue
        changed = true
        leads.putIfAbsent(groupKey(t.group), v)
    }
    return if (changed) s.copy(settings = s.settings.copy(leads = leads)) else s
}
