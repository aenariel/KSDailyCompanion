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

data class Shared(val anchor: String, val cycle: List<CycleEvent>, val special: List<SpecialEvent>)

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
)

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
    val eventsUrl: String = DEFAULT_EVENTS_URL,
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
)

const val SCHEMA = 1
const val NONE = "none"
const val DEFAULT_EVENTS_URL = "https://aenariel.github.io/KSDailyAgenda/events.json"

val DEFAULT_CHOICE_GROUPS = listOf(
    ChoiceGroup("Swordland Showdown", "swordland", 3, 4),
    ChoiceGroup("Tri-Alliance Clash", "triclash", 3, 4),
)

/** Kingdom-specific events, as in the agenda's allDefaultEvents() (the shared ones come from events.json). */
fun defaultKingdom(variant: String): Kingdom {
    val old = variant == "old"
    return Kingdom(
        variant = if (old) "old" else "young",
        cycle = listOf(
            CycleEvent("fishing", "🎣", "Fishing Tournament", 1, 3, 28, on = !old),
            CycleEvent("desert", if (old) "🏆" else "🐺", if (old) "Champion's Way" else "Desert Trial", 2, 3, 14),
            CycleEvent("divination", "🔮", "Mystic Divination", 4, 2, 28, on = old),
            CycleEvent("cesares", "👹", "Cesares Fury", 12, 3, 28),
        ),
    )
}

fun defaultLegionTimes(): List<TimedItem> = listOf(
    TimedItem("sl-l1", true, "🏯", "Swordland (Legion 1)", "0 UTC", "Swordland Showdown", Rule("event", ev = "swordland", from = 7, to = 7)),
    TimedItem("sl-l2", true, "🏯", "Swordland (Legion 2)", "12 UTC", "Swordland Showdown", Rule("event", ev = "swordland", from = 7, to = 7)),
    TimedItem("tri-l1", true, "🏤", "Tri-Alliance (Legion 1)", "0 UTC", "Tri-Alliance Clash", Rule("event", ev = "triclash", from = 6, to = 6)),
    TimedItem("tri-l2", true, "🏤", "Tri-Alliance (Legion 2)", "12 UTC", "Tri-Alliance Clash", Rule("event", ev = "triclash", from = 6, to = 6)),
)

fun defaultAlliance(kingdom: String): Alliance = Alliance(
    kingdom,
    listOf(
        TimedItem("trap1", true, "🐻", "Trap 1", "20 UTC", "Bear Traps", Rule("every", date = "2026-11-12", every = 2)),
        TimedItem("trap2", true, "🐻", "Trap 2", "12 UTC", "Bear Traps", Rule("every", date = "2026-11-12", every = 2)),
    ) + defaultLegionTimes(),
)

fun freshState(shared: Shared, sharedVersion: Int): CompanionState = CompanionState(
    sharedVersion = sharedVersion,
    shared = shared,
    kingdoms = mapOf("My kingdom" to defaultKingdom("young")),
    alliances = mapOf("My alliance" to defaultAlliance("My kingdom")),
)
