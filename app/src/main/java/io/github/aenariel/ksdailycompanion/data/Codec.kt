package io.github.aenariel.ksdailycompanion.data

import io.github.aenariel.ksdailycompanion.model.*
import org.json.JSONArray
import org.json.JSONObject

// JSON <-> model. Field names follow KSDailyAgenda, so its events.json and backups read directly.

private inline fun <T> JSONArray?.mapObj(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out += f(it) }
    return out
}

private fun JSONObject.str(k: String, d: String = ""): String = if (has(k) && !isNull(k)) optString(k, d) else d

private fun arr(list: List<Any?>): JSONArray = JSONArray().also { a -> list.forEach { a.put(it) } }

object Codec {
    fun cycle(o: JSONObject) = CycleEvent(
        id = o.str("id"), emoji = o.str("emoji"), name = o.str("name"),
        start = o.optInt("start"), len = o.optInt("len", 1), every = o.optInt("every", 28),
        on = o.optBoolean("on", true), showDay = o.optBoolean("showDay"), maxDay = o.optInt("maxDay"),
        endTime = o.str("endTime"), startTime = o.str("startTime"),
    )

    fun cycle(e: CycleEvent): JSONObject = JSONObject()
        .put("id", e.id).put("emoji", e.emoji).put("name", e.name).put("start", e.start).put("len", e.len).put("every", e.every)
        .put("on", e.on).put("showDay", e.showDay).put("maxDay", e.maxDay).put("endTime", e.endTime).put("startTime", e.startTime)

    fun special(o: JSONObject) = SpecialEvent(o.str("id"), o.str("emoji"), o.str("name"), o.str("from"), o.str("to"), o.optBoolean("on", true), o.optBoolean("showDay"))
    fun special(s: SpecialEvent): JSONObject = JSONObject()
        .put("id", s.id).put("emoji", s.emoji).put("name", s.name).put("from", s.from).put("to", s.to).put("on", s.on).put("showDay", s.showDay)

    fun rule(o: JSONObject?): Rule {
        if (o == null) return Rule("date")
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        return Rule(o.str("type", "date"), o.str("date"), o.optInt("every", 1), o.str("ev"), o.optInt("from", 1), o.optInt("to", 0), o.str("unless"), days)
    }

    fun rule(r: Rule): JSONObject = JSONObject().put("type", r.type).apply {
        when (r.type) {
            "every" -> put("date", r.date).put("every", r.every)
            "date" -> put("date", r.date)
            "weekday" -> put("days", arr(r.days))
            "event" -> { put("ev", r.ev).put("from", r.from).put("to", r.to); if (r.unless.isNotBlank()) put("unless", r.unless) }
        }
    }

    fun timed(o: JSONObject) = TimedItem(o.str("id", newId()), o.optBoolean("on", true), o.str("emoji"), o.str("label"), o.str("time"), o.str("group"), rule(o.optJSONObject("rule")))
    fun timed(t: TimedItem): JSONObject = JSONObject()
        .put("id", t.id).put("on", t.on).put("emoji", t.emoji).put("label", t.label).put("time", t.time).put("group", t.group).put("rule", rule(t.rule))

    fun shared(o: JSONObject) = Shared(o.str("anchor", "2026-11-09"), o.optJSONArray("cycle").mapObj(::cycle), o.optJSONArray("special").mapObj(::special))
    fun shared(s: Shared): JSONObject = JSONObject().put("anchor", s.anchor).put("cycle", arr(s.cycle.map(::cycle))).put("special", arr(s.special.map(::special)))

    fun kingdom(o: JSONObject) = Kingdom(o.str("variant", "young"), o.optJSONArray("cycle").mapObj(::cycle), o.optJSONArray("special").mapObj(::special))
    fun kingdom(k: Kingdom): JSONObject = JSONObject().put("variant", k.variant).put("cycle", arr(k.cycle.map(::cycle))).put("special", arr(k.special.map(::special)))

    fun alliance(o: JSONObject) = Alliance(o.str("kingdom"), o.optJSONArray("timed").mapObj(::timed))
    fun alliance(a: Alliance): JSONObject = JSONObject().put("kingdom", a.kingdom).put("timed", arr(a.timed.map(::timed)))

    private fun strMap(o: JSONObject?): Map<String, String> =
        o?.keys()?.asSequence()?.associateWith { o.optString(it) } ?: emptyMap()

    private fun strMap(m: Map<String, String>): JSONObject = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }

    fun character(o: JSONObject): Character {
        val off = o.optJSONArray("off")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        return Character(o.str("id", newId()), o.str("name"), o.str("alliance"), o.optBoolean("on", true), off, strMap(o.optJSONObject("usual")), o.optInt("color", 0))
    }

    fun character(c: Character): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("alliance", c.alliance).put("on", c.on)
        .put("off", arr(c.off.sorted())).put("usual", strMap(c.usual)).put("color", c.color)

    fun choice(o: JSONObject) = ChoiceGroup(o.str("group"), o.str("ev"), o.optInt("regFrom", 3), o.optInt("regTo", 4))
    fun choice(c: ChoiceGroup): JSONObject = JSONObject().put("group", c.group).put("ev", c.ev).put("regFrom", c.regFrom).put("regTo", c.regTo)

    private fun intList(a: JSONArray?): List<Int>? = a?.let { (0 until it.length()).map { i -> it.optInt(i) } }

    fun settings(o: JSONObject?): Settings {
        if (o == null) return Settings()
        val leads = HashMap<String, List<Int>>()
        o.optJSONObject("leads")?.let { l -> l.keys().forEach { k -> intList(l.optJSONArray(k))?.let { leads[k] = it } } }
        val d = Settings()
        return Settings(
            defaultLeads = intList(o.optJSONArray("defaultLeads")) ?: d.defaultLeads,
            leads = leads,
            digestOn = o.optBoolean("digestOn", d.digestOn),
            digestMin = o.optInt("digestMin", d.digestMin),
            promptMin = o.optInt("promptMin", d.promptMin),
            eventsUrl = o.str("eventsUrl", d.eventsUrl).ifBlank { d.eventsUrl },
        )
    }

    fun settings(s: Settings): JSONObject = JSONObject()
        .put("defaultLeads", arr(s.defaultLeads))
        .put("leads", JSONObject().also { o -> s.leads.forEach { (k, v) -> o.put(k, arr(v)) } })
        .put("digestOn", s.digestOn).put("digestMin", s.digestMin).put("promptMin", s.promptMin).put("eventsUrl", s.eventsUrl)

    fun state(o: JSONObject, fallbackShared: Shared): CompanionState {
        val kingdoms = LinkedHashMap<String, Kingdom>()
        o.optJSONObject("kingdoms")?.let { k -> k.keys().forEach { n -> k.optJSONObject(n)?.let { kingdoms[n] = kingdom(it) } } }
        val alliances = LinkedHashMap<String, Alliance>()
        o.optJSONObject("alliances")?.let { a -> a.keys().forEach { n -> a.optJSONObject(n)?.let { alliances[n] = alliance(it) } } }
        val picks = LinkedHashMap<String, Map<String, String>>()
        o.optJSONObject("picks")?.let { p -> p.keys().forEach { c -> picks[c] = strMap(p.optJSONObject(c)) } }
        val groups = o.optJSONArray("choiceGroups").mapObj(::choice)
        return CompanionState(
            schema = o.optInt("schema", SCHEMA),
            sharedVersion = o.optInt("sharedVersion"),
            shared = o.optJSONObject("shared")?.let(::shared) ?: fallbackShared,
            kingdoms = kingdoms,
            alliances = alliances,
            characters = o.optJSONArray("characters").mapObj(::character),
            picks = picks,
            choiceGroups = groups.ifEmpty { DEFAULT_CHOICE_GROUPS },
            settings = settings(o.optJSONObject("settings")),
        )
    }

    fun state(s: CompanionState): JSONObject = JSONObject()
        .put("schema", s.schema).put("sharedVersion", s.sharedVersion).put("shared", shared(s.shared))
        .put("kingdoms", JSONObject().also { o -> s.kingdoms.forEach { (k, v) -> o.put(k, kingdom(v)) } })
        .put("alliances", JSONObject().also { o -> s.alliances.forEach { (k, v) -> o.put(k, alliance(v)) } })
        .put("characters", arr(s.characters.map(::character)))
        .put("picks", JSONObject().also { o -> s.picks.forEach { (k, v) -> o.put(k, strMap(v)) } })
        .put("choiceGroups", arr(s.choiceGroups.map(::choice)))
        .put("settings", settings(s.settings))

    /** events.json of KSDailyAgenda: {version, shared:{anchor, cycle, special}}. */
    fun eventsFile(text: String): Pair<Int, Shared>? = try {
        val o = JSONObject(text)
        val sh = o.optJSONObject("shared")
        if (sh == null || sh.optJSONArray("cycle") == null) null else o.optInt("version") to shared(sh)
    } catch (e: Exception) { null }

    /** Keeps each event's on/off from the current list, like the agenda does. */
    fun mergeShared(current: Shared, incoming: Shared): Shared {
        val onOff = current.cycle.associate { it.id to it.on }
        return incoming.copy(cycle = incoming.cycle.map { e -> onOff[e.id]?.let { e.copy(on = it) } ?: e })
    }

    class ImportResult(val state: CompanionState, val message: String)

    /**
     * Reads a companion export ("ks-companion"), an agenda backup ("ks-agenda-all") or a single agenda alliance ("ks-agenda").
     * Companion export: replaces everything. Agenda files: adds or replaces kingdoms and alliances by name; characters, picks and settings stay.
     */
    fun import(text: String, cur: CompanionState): ImportResult {
        val o = try { JSONObject(text.trim()) } catch (e: Exception) { throw IllegalArgumentException("Not valid JSON") }
        when (o.optString("type")) {
            "ks-companion" -> {
                val st = o.optJSONObject("state") ?: throw IllegalArgumentException("Companion file without state")
                val s = state(st, cur.shared)
                return ImportResult(withColors(s), "Replaced everything: ${s.characters.size} characters, ${s.alliances.size} alliances")
            }
            "ks-agenda-all" -> {
                val st = o.optJSONObject("state") ?: throw IllegalArgumentException("Agenda backup without state")
                val schema = st.optInt("schema")
                if (schema != 15) Log.w("Agenda backup schema $schema (this app reads 15); importing what matches")
                val ks = LinkedHashMap(cur.kingdoms)
                st.optJSONObject("kingdoms")?.let { k -> k.keys().forEach { n -> k.optJSONObject(n)?.let { ks[n] = kingdom(it) } } }
                val al = LinkedHashMap(cur.alliances)
                var n = 0
                st.optJSONObject("alliances")?.let { a -> a.keys().forEach { name -> a.optJSONObject(name)?.let { al[name] = legionsOn(alliance(it), cur); n++ } } }
                var sh = cur.shared
                var ver = cur.sharedVersion
                val sv = st.optInt("sharedVersion")
                val incoming = st.optJSONObject("shared")
                if (incoming != null && sv > ver) { sh = mergeShared(cur.shared, shared(incoming)); ver = sv }
                val next = cleanDefaults(cur.copy(kingdoms = ks, alliances = al, shared = sh, sharedVersion = ver))
                return ImportResult(next, "Imported $n alliances and ${st.optJSONObject("kingdoms")?.length() ?: 0} kingdoms from the agenda")
            }
            "ks-agenda" -> {
                val a = o.optJSONObject("a") ?: throw IllegalArgumentException("Agenda alliance without data")
                val kName = o.str("kingdom", "Imported kingdom")
                val aName = o.str("alliance", "Imported alliance")
                val ks = LinkedHashMap(cur.kingdoms)
                o.optJSONObject("k")?.let { ks[kName] = kingdom(it) }
                val al = LinkedHashMap(cur.alliances)
                al[aName] = legionsOn(alliance(a), cur).copy(kingdom = kName)
                return ImportResult(cleanDefaults(cur.copy(kingdoms = ks, alliances = al)), "Imported alliance $aName ($kName)")
            }
            else -> throw IllegalArgumentException("Unknown file type: expected a KSCompanion export or a KSDailyAgenda backup")
        }
    }

    /** In the agenda "on" means "shown in the message"; here legion times are always needed to choose from. */
    private fun legionsOn(a: Alliance, cur: CompanionState): Alliance {
        val groups = cur.choiceGroups.map { it.group }.toSet()
        return a.copy(timed = a.timed.map { if (it.group in groups) it.copy(on = true) else it })
    }

    /** Drops the untouched placeholder kingdom/alliance of a fresh install once real ones arrive. */
    private fun cleanDefaults(s: CompanionState): CompanionState {
        var st = s
        val used = st.characters.map { it.alliance }.toSet()
        if (st.alliances.size > 1 && "My alliance" in st.alliances && "My alliance" !in used && st.alliances["My alliance"] == defaultAlliance("My kingdom"))
            st = st.copy(alliances = st.alliances - "My alliance")
        if (st.kingdoms.size > 1 && "My kingdom" in st.kingdoms && st.alliances.values.none { it.kingdom == "My kingdom" })
            st = st.copy(kingdoms = st.kingdoms - "My kingdom")
        return st
    }

    fun export(s: CompanionState): String =
        JSONObject().put("type", "ks-companion").put("exported", java.time.LocalDate.now().toString()).put("state", state(s)).toString(1)
}
