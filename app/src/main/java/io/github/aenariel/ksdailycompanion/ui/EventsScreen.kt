package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.notify.Notifier

@Composable
fun EventsScreen(model: AppModel, modifier: Modifier) {
    val s = model.state
    var selected by rememberSaveable { mutableStateOf(s.alliances.keys.firstOrNull() ?: "") }
    if (selected !in s.alliances) selected = s.alliances.keys.firstOrNull() ?: ""
    val al = s.alliances[selected]
    var editing by remember { mutableStateOf<TimedItem?>(null) }
    var naming by remember { mutableStateOf<String?>(null) }   // "newAlliance", "rename", "newKingdom"
    var confirmDelete by remember { mutableStateOf(false) }
    var showFixed by rememberSaveable { mutableStateOf(false) }
    val choiceNames = s.choiceGroups.map { it.group }.toSet()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Events and times", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Hint("Times are set per alliance, always in UTC. The local time is shown next to them.")
        Spacer(Modifier.height(8.dp))
        Picker("Alliance", selected, s.alliances.keys.map { it to it }) { selected = it; confirmDelete = false }
        Row {
            TextButton(onClick = { naming = "newAlliance" }) { Text("New") }
            if (al != null) TextButton(onClick = { naming = "rename" }) { Text("Rename") }
            if (al != null && s.alliances.size > 1) TextButton(onClick = {
                val users = s.characters.filter { it.alliance == selected }
                when {
                    users.isNotEmpty() -> model.toast("Move ${users.joinToString { it.name }} to another alliance first")
                    !confirmDelete -> confirmDelete = true
                    else -> { model.update("delete alliance $selected") { it.copy(alliances = it.alliances - selected) }; confirmDelete = false }
                }
            }) { Text(if (confirmDelete) "Tap again to delete" else "Delete", color = MaterialTheme.colorScheme.error) }
        }
        if (al == null) return@Column

        Picker("Kingdom", al.kingdom, s.kingdoms.keys.map { it to it }) { k ->
            model.update("alliance $selected kingdom -> $k") { it.copy(alliances = it.alliances + (selected to al.copy(kingdom = k))) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { naming = "newKingdom" }) { Text("New kingdom") }
            val k = s.kingdoms[al.kingdom]
            if (k != null) {
                Spacer(Modifier.weight(1f))
                Text("Gen 4+", style = MaterialTheme.typography.bodySmall)
                Switch(checked = k.variant == "old", onCheckedChange = { old ->
                    val d = defaultKingdom(if (old) "old" else "young")
                    model.update("kingdom ${al.kingdom} variant old=$old") { it.copy(kingdoms = it.kingdoms + (al.kingdom to d)) }
                }, modifier = Modifier.padding(start = 6.dp))
            }
        }

        val v = viewFor(s, al.kingdom)
        SectionTitle("Legions")
        Hint("Your alliance's legion times. Characters choose one per week; change a time here when the alliance changes it.")
        for (g in s.choiceGroups) {
            val items = al.timed.filter { it.group == g.group }
            Text(g.group, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            for (it in items) ItemRow(it, v, model.now) { editing = it }
            TextButton(onClick = {
                val base = items.firstOrNull()
                val n = items.size + 1
                editing = TimedItem(newId(), true, base?.emoji ?: "", "${g.group.substringBefore(' ')} (Legion $n)", base?.time ?: "12:00 UTC", g.group,
                    base?.rule ?: Rule("event", ev = g.ev, from = 7, to = 7))
            }) { Text("Add a legion") }
        }

        SectionTitle("Other times")
        Hint("Bear Traps and anything else at a fixed hour: every N days, on weekdays, or on a day of a game event.")
        for (it in al.timed.filter { it.group !in choiceNames }) ItemRow(it, v, model.now) { editing = it }
        TextButton(onClick = {
            editing = TimedItem(newId(), true, "", "", "12:00 UTC", "", Rule("every", date = diso(dayOf(model.now)), every = 1))
        }) { Text("Add a time") }

        SectionTitle("Game events")
        Hint("Fixed events v${s.sharedVersion} (cycle from ${s.shared.anchor}), shared with KSDailyAgenda. Switched-off events are hidden from the agenda.")
        Row {
            TextButton(onClick = { model.refreshEvents(quiet = false) }) { Text("Check for updates") }
            TextButton(onClick = { showFixed = !showFixed }) { Text(if (showFixed) "Hide list" else "Show list") }
        }
        if (showFixed) {
            val kingdomIds = (s.kingdoms[al.kingdom]?.cycle ?: emptyList()).map { it.id }.toSet()
            for (ev in v.cycle) {
                val sub = "every ${ev.every} days, ${ev.len} day(s)" + (if (ev.startTime.isNotBlank()) ", starts ${ev.startTime}" else "") +
                    (if (ev.id in kingdomIds) " · this kingdom" else "")
                SwitchRow("${ev.emoji.trim()} ${ev.name}".trim(), ev.on, sub) { on ->
                    model.update("event ${ev.id} on=$on") { st ->
                        if (ev.id in kingdomIds) {
                            val k = st.kingdoms[al.kingdom]!!
                            st.copy(kingdoms = st.kingdoms + (al.kingdom to k.copy(cycle = k.cycle.map { if (it.id == ev.id) it.copy(on = on) else it })))
                        } else st.copy(shared = st.shared.copy(cycle = st.shared.cycle.map { if (it.id == ev.id) it.copy(on = on) else it }))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { item -> if (al != null) ItemDialog(model, selected, al, item) { editing = null } }

    when (naming) {
        "newAlliance" -> TextDialog("New alliance", "", "Name, e.g. \"Ffs #1130\".", { naming = null }) { name ->
            val n = name.trim()
            when {
                n.isEmpty() -> model.toast("Type a name")
                n in s.alliances -> model.toast("$n already exists")
                else -> {
                    val kingdom = al?.kingdom ?: s.kingdoms.keys.firstOrNull() ?: "My kingdom"
                    model.update("new alliance $n") { st ->
                        st.copy(alliances = st.alliances + (n to defaultAlliance(kingdom)),
                            kingdoms = if (kingdom in st.kingdoms) st.kingdoms else st.kingdoms + (kingdom to defaultKingdom("young")))
                    }
                    selected = n
                }
            }
            naming = null
        }
        "rename" -> TextDialog("Rename alliance", selected, "Characters in it follow the new name.", { naming = null }) { name ->
            val n = name.trim()
            if (n.isNotEmpty() && n != selected && n !in s.alliances && al != null) {
                val old = selected
                model.update("rename alliance $old -> $n") { st ->
                    st.copy(alliances = LinkedHashMap(st.alliances.mapKeys { if (it.key == old) n else it.key }),
                        characters = st.characters.map { if (it.alliance == old) it.copy(alliance = n) else it })
                }
                selected = n
            } else if (n in s.alliances && n != selected) model.toast("$n already exists")
            naming = null
        }
        "newKingdom" -> TextDialog("New kingdom", "", "Name, e.g. \"#1130\". Its own events (Fishing, Desert Trial…) start from the defaults.", { naming = null }) { name ->
            val n = name.trim()
            if (n.isNotEmpty() && n !in s.kingdoms && al != null) model.update("new kingdom $n") { st ->
                st.copy(kingdoms = st.kingdoms + (n to defaultKingdom("young")), alliances = st.alliances + (selected to al.copy(kingdom = n)))
            } else if (n in s.kingdoms) model.toast("$n already exists")
            naming = null
        }
    }
}

@Composable
private fun ItemRow(t: TimedItem, v: View, now: Long, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${t.emoji.trim()} ${t.label}".trim() + if (t.on) "" else " (off)")
                Hint(ruleSummary(t.rule, v) + if (t.group.isNotBlank()) " · ${t.group}" else "")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(normTime(t.time) ?: t.time, fontWeight = FontWeight.SemiBold)
                Hint(timePreview(t.time, now).substringAfter(" · "))
            }
        }
    }
}

@Composable
private fun ItemDialog(model: AppModel, allianceName: String, al: Alliance, start: TimedItem, onClose: () -> Unit) {
    val s = model.state
    val isNew = al.timed.none { it.id == start.id }
    var t by remember { mutableStateOf(start) }
    var timeText by remember { mutableStateOf(normTime(start.time) ?: start.time) }
    var everyText by remember { mutableStateOf(start.rule.every.toString()) }
    var fromText by remember { mutableStateOf(start.rule.from.toString()) }
    var toText by remember { mutableStateOf((if (start.rule.to != 0) start.rule.to else start.rule.from).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val v = viewFor(s, al.kingdom)
    val time = normTime(timeText)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun built(): TimedItem? {
        val tm = time ?: return null
        val r = when (t.rule.type) {
            "every" -> t.rule.copy(every = everyText.toIntOrNull()?.coerceAtLeast(1) ?: return null).takeIf { dnum(it.date) != null } ?: return null
            "event" -> {
                val f = fromText.toIntOrNull()?.coerceAtLeast(1) ?: return null
                val to = toText.toIntOrNull()?.coerceAtLeast(f) ?: f
                t.rule.copy(from = f, to = to).takeIf { v.event(it.ev) != null } ?: return null
            }
            "date" -> t.rule.takeIf { dnum(it.date) != null } ?: return null
            else -> t.rule.takeIf { it.days.isNotEmpty() } ?: return null
        }
        return t.copy(time = tm, rule = r, label = t.label.trim())
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "New time" else t.label.ifBlank { "Time" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(t.label, { t = t.copy(label = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(t.emoji, { t = t.copy(emoji = it) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(90.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(timeText, { timeText = it }, label = { Text("Time (UTC)") }, singleLine = true, isError = time == null, modifier = Modifier.weight(1f))
                }
                Hint(if (time == null) "Type an hour, e.g. 20 or 20:30" else "Today: " + timePreview(time, model.now))
                Spacer(Modifier.height(8.dp))
                Picker("Repeats", t.rule.type, listOf("every" to "Every N days", "event" to "On a day of a game event", "weekday" to "On weekdays", "date" to "Once")) { type ->
                    t = t.copy(rule = when (type) {
                        "every" -> Rule("every", date = t.rule.date.ifBlank { diso(dayOf(model.now)) }, every = 2)
                        "event" -> Rule("event", ev = t.rule.ev.ifBlank { v.cycle.firstOrNull()?.id ?: "" }, from = 1, to = 1)
                        "weekday" -> Rule("weekday", days = listOf(wday(dayOf(model.now))))
                        else -> Rule("date", date = diso(dayOf(model.now)))
                    })
                    everyText = t.rule.every.toString(); fromText = t.rule.from.toString(); toText = (if (t.rule.to != 0) t.rule.to else t.rule.from).toString()
                }
                when (t.rule.type) {
                    "every" -> Row {
                        OutlinedTextField(everyText, { everyText = it }, label = { Text("Every (days)") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(t.rule.date, { t = t.copy(rule = t.rule.copy(date = it.trim())) }, label = { Text("A day it runs") },
                            singleLine = true, isError = dnum(t.rule.date) == null, modifier = Modifier.weight(1.4f))
                    }
                    "event" -> {
                        Picker("Event", t.rule.ev, v.cycle.map { it.id to "${it.emoji.trim()} ${it.name}".trim() }) { t = t.copy(rule = t.rule.copy(ev = it)) }
                        Row {
                            OutlinedTextField(fromText, { fromText = it }, label = { Text("From day") }, singleLine = true, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(toText, { toText = it }, label = { Text("To day") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Hint("Day 1 is the event's first day. Swordland battle: day 7 (Sunday); Tri-Alliance: day 6.")
                    }
                    "date" -> OutlinedTextField(t.rule.date, { t = t.copy(rule = t.rule.copy(date = it.trim())) }, label = { Text("Date (yyyy-mm-dd)") },
                        singleLine = true, isError = dnum(t.rule.date) == null, modifier = Modifier.fillMaxWidth())
                    else -> Row {
                        for ((i, d) in days.withIndex()) {
                            val on = i in t.rule.days
                            TextButton(onClick = { t = t.copy(rule = t.rule.copy(days = if (on) t.rule.days - i else t.rule.days + i)) }, contentPadding = PaddingValues(2.dp),
                                modifier = Modifier.weight(1f)) { Text(if (on) "[$d]" else d, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(t.group, { t = t.copy(group = it) }, label = { Text("Group (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Hint("Items of a legion group (" + s.choiceGroups.joinToString { it.group } + ") are the legions to choose from.")
                SwitchRow("Active", t.on) { t = t.copy(on = it) }
                if (!isNew) TextButton(onClick = {
                    if (!confirmDelete) confirmDelete = true else {
                        model.update("delete ${t.label} in $allianceName") { st ->
                            val a = st.alliances[allianceName] ?: return@update st
                            st.copy(alliances = st.alliances + (allianceName to a.copy(timed = a.timed.filter { it.id != t.id })))
                        }
                        onClose()
                    }
                }) { Text(if (confirmDelete) "Tap again to delete" else "Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val ready = built()
            TextButton(enabled = ready != null && ready.label.isNotBlank(), onClick = {
                val item = built() ?: return@TextButton
                model.update("${if (isNew) "add" else "edit"} ${item.label} (${item.time}) in $allianceName") { st ->
                    val a = st.alliances[allianceName] ?: return@update st
                    val list = if (isNew) a.timed + item else a.timed.map { if (it.id == item.id) item else it }
                    st.copy(alliances = st.alliances + (allianceName to a.copy(timed = list)))
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Short legion name for menus. */
fun legionName(t: TimedItem): String = Notifier.shortLabel(t.label)
