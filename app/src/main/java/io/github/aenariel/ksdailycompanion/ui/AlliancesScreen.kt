package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.notify.Notifier

/** Alliances hold the times (legions, Bear Traps…); the event dates come from their kingdom. */
@Composable
fun AlliancesScreen(model: AppModel, modifier: Modifier) {
    val s = model.state
    var editing by remember { mutableStateOf<Pair<String?, Alliance>?>(null) }   // original name (null = new), data
    val choiceNames = s.choiceGroups.map { it.group }.toSet()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Alliances", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Hint("Each alliance has its legion and Bear Trap times, in UTC. Event dates come from its kingdom (Kingdoms tab).")
        for ((name, a) in s.alliances) {
            val users = s.characters.filter { it.alliance == name }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = name to a }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$name · ${kingdomTag(a.kingdom)}", fontWeight = FontWeight.SemiBold)
                        val legions = a.timed.filter { it.on && it.group in choiceNames }.groupBy { it.group }
                            .map { (g, items) -> g.substringBefore(' ') + " " + items.joinToString(" / ") { (normTime(it.time) ?: it.time).removeSuffix(" UTC") } }
                        val other = a.timed.filter { it.on && it.group !in choiceNames }.joinToString(", ") { "${it.label} ${(normTime(it.time) ?: it.time).removeSuffix(" UTC")}" }
                        if (legions.isNotEmpty()) Hint(legions.joinToString(" · ") + " UTC")
                        if (other.isNotEmpty()) Hint("$other UTC")
                        if (users.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                            for (u in users) { ColorDot(u.color, 10); Hint(" ${u.name}  ") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val k = s.kingdoms.keys.firstOrNull()
            if (k == null) model.toast("Create a kingdom first (Kingdoms tab)") else editing = null to defaultAlliance(k)
        }) { Text("Add an alliance") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { (name, a) -> AllianceDialog(model, name, a) { editing = null } }
}

@Composable
private fun AllianceDialog(model: AppModel, original: String?, start: Alliance, onClose: () -> Unit) {
    val s = model.state
    var tag by remember { mutableStateOf(original ?: "") }
    var a by remember { mutableStateOf(start) }
    var editing by remember { mutableStateOf<TimedItem?>(null) }
    val choiceNames = s.choiceGroups.map { it.group }.toSet()
    val users = if (original == null) emptyList() else s.characters.filter { it.alliance == original }
    val validTag = allianceTag(tag)
    val taken = validTag != null && validTag != original && validTag in s.alliances
    val v = viewFor(s, a.kingdom)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (original == null) "New alliance" else original) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(tag, { tag = it.take(3) }, label = { Text("Tag") }, singleLine = true, isError = validTag == null || taken, modifier = Modifier.fillMaxWidth())
                Hint(when {
                    taken -> "$validTag already exists"
                    validTag == null -> "3 letters or digits, as in the game (e.g. Ffs)"
                    else -> "Characters in it: " + (users.joinToString { it.name }.ifEmpty { "none" })
                })
                Spacer(Modifier.height(8.dp))
                Picker("Kingdom", a.kingdom, s.kingdoms.keys.map { it to kingdomTag(it) }) { a = a.copy(kingdom = it) }

                SectionTitle("Legions")
                Hint("Characters choose one per week; change a time here when the alliance changes it.")
                for (g in s.choiceGroups) {
                    val items = a.timed.filter { it.group == g.group }
                    Text(g.group, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    for (it in items) ItemRow(it, v, model.now) { editing = it }
                    TextButton(onClick = {
                        val base = items.firstOrNull()
                        editing = TimedItem(newId(), true, base?.emoji ?: "", "${g.group.substringBefore(' ')} (Legion ${items.size + 1})",
                            base?.time ?: "12:00 UTC", g.group, base?.rule ?: Rule("event", ev = g.ev, from = 7, to = 7))
                    }) { Text("Add a legion") }
                }

                SectionTitle("Other times")
                Hint("Bear Traps and anything else at a fixed hour: every N days, on weekdays, or on a day of a game event.")
                for (it in a.timed.filter { it.group !in choiceNames }) ItemRow(it, v, model.now) { editing = it }
                TextButton(onClick = {
                    editing = TimedItem(newId(), true, "", "", "12:00 UTC", "", Rule("every", date = diso(dayOf(model.now)), every = 1))
                }) { Text("Add a time") }

                if (original != null) {
                    Spacer(Modifier.height(8.dp))
                    DeleteButton("Delete alliance") {
                        if (users.isNotEmpty()) model.toast("Move ${users.joinToString { it.name }} to another alliance first")
                        else { model.update("delete alliance $original") { it.copy(alliances = it.alliances - original) }; onClose() }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = validTag != null && !taken && a.kingdom in s.kingdoms, onClick = {
                val n = validTag ?: return@TextButton
                model.update(if (original == null) "add alliance $n" else "edit alliance $original -> $n") { st ->
                    val map = LinkedHashMap<String, Alliance>()
                    for ((k, x) in st.alliances) if (k == original) map[n] = a else map[k] = x
                    if (original == null) map[n] = a
                    st.copy(alliances = map, characters = st.characters.map { if (original != null && it.alliance == original) it.copy(alliance = n) else it })
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )

    editing?.let { item ->
        val isNew = a.timed.none { it.id == item.id }
        ItemDialog(model, v, item, isNew,
            onSave = { x -> a = a.copy(timed = if (isNew) a.timed + x else a.timed.map { if (it.id == x.id) x else it }) },
            onDelete = { a = a.copy(timed = a.timed.filter { it.id != item.id }) },
            onClose = { editing = null })
    }
}

@Composable
private fun ItemRow(t: TimedItem, v: View, now: Long, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(withEmoji(t.emoji, t.label) + if (t.on) "" else " (off)")
                Hint(ruleSummary(t.rule, v) + if (t.group.isNotBlank()) " · ${t.group}" else "")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(normTime(t.time) ?: t.time, fontWeight = FontWeight.SemiBold)
                Hint(timePreview(t.time, now).substringAfter(" · "))
            }
        }
    }
}

/** Edits one alliance time; changes go back to the alliance dialog, saved with it. */
@Composable
private fun ItemDialog(model: AppModel, v: View, start: TimedItem, isNew: Boolean, onSave: (TimedItem) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val s = model.state
    var t by remember { mutableStateOf(start) }
    var timeText by remember { mutableStateOf(normTime(start.time) ?: start.time) }
    var everyText by remember { mutableStateOf(start.rule.every.toString()) }
    var fromText by remember { mutableStateOf(start.rule.from.toString()) }
    var toText by remember { mutableStateOf((if (start.rule.to != 0) start.rule.to else start.rule.from).toString()) }
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
        return t.copy(time = tm, rule = r, label = t.label.trim()).takeIf { it.label.isNotBlank() }
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
                        Picker("Event", t.rule.ev, v.cycle.map { it.id to withEmoji(it.emoji, it.name) }) { t = t.copy(rule = t.rule.copy(ev = it)) }
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
                Hint("Same group = one selector per character (e.g. Bear Traps). Legion groups: " + s.choiceGroups.joinToString { it.group } + ".")
                SwitchRow("Active", t.on) { t = t.copy(on = it) }
                if (!isNew) DeleteButton("Delete") { onDelete(); onClose() }
            }
        },
        confirmButton = {
            val ready = built()
            TextButton(enabled = ready != null, onClick = { built()?.let { onSave(it) }; onClose() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Short legion name for menus. */
fun legionName(t: TimedItem): String = Notifier.shortLabel(t.label)
