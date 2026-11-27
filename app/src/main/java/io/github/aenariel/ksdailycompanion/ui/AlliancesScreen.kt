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

/** Alliances hold the times (Bear Traps, legions…); the event dates come from their kingdom. */
@Composable
fun AlliancesScreen(model: AppModel, modifier: Modifier) {
    val s = model.state
    var editing by remember { mutableStateOf<Pair<String?, Alliance>?>(null) }   // original name (null = new), data
    var deleting by remember { mutableStateOf<String?>(null) }
    val choiceNames = s.choiceGroups.map { it.group }.toSet()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        ScreenHeader(Tab.ALLIANCES, hint = "Tap to edit, long-press for more. Each alliance has its Bear Trap and legion times (UTC); event dates come from its kingdom.")
        for ((name, a) in s.alliances) {
            val users = s.characters.filter { it.alliance == name }
            MenuCard(onClick = { editing = name to a }, menu = listOf(
                "Edit" to { editing = name to a },
                "Duplicate" to { editing = null to a },
                "Delete" to { deleting = name },
            )) {
                Column(Modifier.weight(1f)) {
                    Text(allianceLabel(name, a.kingdom), fontWeight = FontWeight.SemiBold)
                    CharactersLine(users)
                    Spacer(Modifier.height(6.dp))
                    val ind = Modifier.padding(start = 8.dp)
                    val traps = a.timed.filter { it.on && it.group == BEAR_GROUP }
                    if (traps.isNotEmpty()) Hint("🐻 " + traps.joinToString(" · ") { Notifier.shortLabel(it.label) + " " + hhmm(it.time) }, ind)
                    val vv = a.timed.filter { it.on && it.group == VIKING_GROUP && it.time.isNotBlank() }
                    if (vv.isNotEmpty()) Hint("🪓 " + shortName(VIKING_GROUP) + ": " + vv.joinToString(" · ") { it.label + " " + hhmm(it.time) }, ind)
                    for ((g, items) in a.timed.filter { it.on && it.group in choiceNames }.groupBy { it.group })
                        Hint(withEmoji(items[0].emoji, items.joinToString(" · ") { legionName(it) + " " + hhmm(it.time) }), ind)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val k = s.kingdoms.keys.firstOrNull()
            if (k == null) model.toast("Add a kingdom first (Kingdoms tab)") else editing = null to defaultAlliance(k)
        }) { Text("Add an alliance") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { (name, a) -> AllianceDialog(model, name, a) { editing = null } }
    deleting?.let { name ->
        val users = s.characters.filter { it.alliance == name }
        ConfirmDialog("Delete $name?", "Its times are removed.", "Delete",
            error = if (users.isEmpty()) null else "Move ${users.joinToString { it.name }} to another alliance first.",
            onConfirm = { if (users.isEmpty()) { model.update("delete alliance $name") { it.copy(alliances = it.alliances - name, battles = it.battles - name) }; deleting = null } },
            onClose = { deleting = null })
    }
}

/** "20 UTC" -> "20:00". */
private fun hhmm(t: String) = (normTime(t) ?: t).removeSuffix(" UTC")

/** Next day (from [today]) an "every N days" rule runs. */
private fun nextRun(r: Rule, today: Int): Int? {
    val d = dnum(r.date) ?: return null
    return today + mod(d - today, maxOf(1, r.every))
}

@Composable
private fun AllianceDialog(model: AppModel, original: String?, start: Alliance, onClose: () -> Unit) {
    val s = model.state
    val today = dayOf(model.now)
    var tag by remember { mutableStateOf(original ?: "") }
    var a by remember { mutableStateOf(start) }
    var editingTime by remember { mutableStateOf<TimedItem?>(null) }
    var editingOther by remember { mutableStateOf<TimedItem?>(null) }
    var editingDays by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val choiceNames = s.choiceGroups.map { it.group }.toSet()
    val users = if (original == null) emptyList() else s.characters.filter { it.alliance == original }
    val validTag = allianceTag(tag)
    val taken = validTag != null && validTag != original && validTag in s.alliances
    val v = viewFor(s, a.kingdom)
    fun replace(x: TimedItem) { a = a.copy(timed = if (a.timed.any { it.id == x.id }) a.timed.map { if (it.id == x.id) x else it } else a.timed + x) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            DialogTitle(Tab.ALLIANCES.icon, original ?: "New alliance", onDelete = if (original == null) null else ({
                if (users.isNotEmpty()) error = "Move ${users.joinToString { it.name }} to another alliance first."
                else { model.update("delete alliance $original") { it.copy(alliances = it.alliances - original, battles = it.battles - original) }; onClose() }
            }))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ErrorText(error)
                SectionCard("Alliance") {
                    OutlinedTextField(tag, { tag = it.take(3) }, label = { Text("Tag") }, singleLine = true, isError = validTag == null || taken, modifier = Modifier.fillMaxWidth())
                    Hint(when {
                        taken -> "$validTag already exists"
                        validTag == null -> "3 letters or digits, as in the game (e.g. Ffs)"
                        else -> "Characters in it: " + (users.joinToString { it.name }.ifEmpty { "none" })
                    })
                    Spacer(Modifier.height(8.dp))
                    Picker("Kingdom", a.kingdom, s.kingdoms.keys.map { it to kingdomTag(it) }) { a = a.copy(kingdom = it) }
                }

                SectionCard("🐻 Bear Traps") {
                    val traps = a.timed.filter { it.group == BEAR_GROUP }
                    if (traps.isEmpty()) TextButton(onClick = { a = a.copy(timed = defaultAlliance(a.kingdom).timed.filter { it.group == BEAR_GROUP } + a.timed) }) { Text("Add Bear Traps") }
                    else {
                        val r = traps[0].rule
                        val next = if (r.type == "every") nextRun(r, today) else null
                        ValueRow(if (r.type == "every") "Every ${r.every} days" else ruleSummary(r, v), next?.let { "next " + dayLabel(it) } ?: "set") { editingDays = true }
                        for (t in traps) ValueRow(Notifier.shortLabel(t.label) + if (t.on) "" else " (off)", hhmm(t.time) + " UTC",
                            timePreview(t.time, model.now).substringAfter(" · ")) { editingTime = t }
                    }
                }

                SectionCard("🪓 ${shortName(VIKING_GROUP)}") {
                    val vv = a.timed.filter { it.group == VIKING_GROUP }
                    if (vv.isEmpty()) TextButton(onClick = { a = a.copy(timed = a.timed + vikingItems()) }) { Text("Add Viking Vengeance times") }
                    for (t in vv) ValueRow(t.label + if (t.on) "" else " (off)", if (t.time.isBlank()) "not set" else hhmm(t.time) + " UTC",
                        if (t.time.isBlank()) "no reminder until set" else timePreview(t.time, model.now).substringAfter(" · ")) { editingTime = t }
                    Hint("Alliance event on days 1 and 3 of Viking Vengeance. Changing Day 1 also changes Day 3 when they were the same.")
                }

                for (g in s.choiceGroups) {
                    val items = a.timed.filter { it.group == g.group }
                    val emoji = displayEmoji(items.firstOrNull()?.emoji ?: v.event(g.ev)?.emoji ?: "")
                    SectionCard(listOf(emoji, g.group).filter { it.isNotBlank() }.joinToString(" ")) {
                        for (t in items.take(2)) ValueRow(legionName(t) + if (t.on) "" else " (off)", hhmm(t.time) + " UTC",
                            timePreview(t.time, model.now).substringAfter(" · ")) { editingTime = t }
                        for (i in items.size until 2) TextButton(onClick = {
                            replace(TimedItem(newId(), true, items.firstOrNull()?.emoji ?: "", "${g.group.substringBefore(' ')} (Legion ${i + 1})", "12:00 UTC",
                                g.group, items.firstOrNull()?.rule ?: Rule("event", ev = g.ev, from = 7, to = 7)))
                        }) { Text("Add Legion ${i + 1}") }
                    }
                }

                SectionCard("Other times") {
                    Hint("Anything else at a fixed hour: every N days, on weekdays, or on a day of a game event.")
                    for (t in a.timed.filter { it.group != BEAR_GROUP && it.group != VIKING_GROUP && it.group !in choiceNames }) ItemRow(t, v, model.now) { editingOther = t }
                    TextButton(onClick = {
                        editingOther = TimedItem(newId(), true, "", "", "12:00 UTC", "", Rule("every", date = diso(today), every = 1))
                    }) { Text("Add a time") }
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
                    val battles = if (original != null && original != n && original in st.battles) st.battles - original + (n to st.battles.getValue(original)) else st.battles
                    st.copy(alliances = map, battles = battles, characters = st.characters.map { if (original != null && it.alliance == original) it.copy(alliance = n) else it })
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )

    editingTime?.let { t ->
        TimeDialog(model, t, allowEmpty = t.group == VIKING_GROUP, onSave = { x ->
            // Day 3 of Viking Vengeance follows Day 1 when they had the same hour
            val follow = if (x.id == "vv1") a.timed.firstOrNull { it.id == "vv3" && normTime(it.time) == normTime(t.time) } else null
            replace(x)
            if (follow != null) replace(follow.copy(time = x.time))
        }, onClose = { editingTime = null })
    }
    if (editingDays) a.timed.firstOrNull { it.group == BEAR_GROUP }?.let { first ->
        TrapDaysDialog(first.rule, today, onSave = { r -> a = a.copy(timed = a.timed.map { if (it.group == BEAR_GROUP) it.copy(rule = r) else it }) }, onClose = { editingDays = false })
    }
    editingOther?.let { item ->
        val isNew = a.timed.none { it.id == item.id }
        ItemDialog(model, v, item, isNew,
            onSave = { replace(it) },
            onDelete = { a = a.copy(timed = a.timed.filter { it.id != item.id }) },
            onClose = { editingOther = null })
    }
}

/** Hour of one Bear Trap or legion. */
@Composable
private fun TimeDialog(model: AppModel, start: TimedItem, allowEmpty: Boolean = false, onSave: (TimedItem) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(if (start.time.isBlank()) "" else hhmm(start.time)) }
    var on by remember { mutableStateOf(start.on) }
    val time = if (allowEmpty && text.isBlank()) "" else normTime(text)
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(displayEmoji(start.emoji), if (start.group == VIKING_GROUP) "${shortName(VIKING_GROUP)} · ${start.label}" else Notifier.shortLabel(start.label)) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Time (UTC)") }, singleLine = true, isError = time == null, modifier = Modifier.fillMaxWidth())
                Hint(when {
                    time == null -> "Type an hour, e.g. 20 or 20:30"
                    time.isEmpty() -> "Not set: no reminder"
                    else -> "Today: " + timePreview(time, model.now)
                })
                SwitchRow("Active", on) { on = it }
            }
        },
        confirmButton = { TextButton(enabled = time != null, onClick = { onSave(start.copy(time = time!!, on = on)); onClose() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Which days the Bear Traps run: one setting for all of them. */
@Composable
private fun TrapDaysDialog(start: Rule, today: Int, onSave: (Rule) -> Unit, onClose: () -> Unit) {
    val first = if (start.type == "every") nextRun(start, today) else null
    var dateText by remember { mutableStateOf(diso(first ?: today)) }
    var everyText by remember { mutableStateOf((if (start.type == "every") start.every else 2).toString()) }
    val d = dnum(dateText)
    val every = everyText.toIntOrNull()?.takeIf { it in 1..30 }
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle("🐻", "Bear Trap days") },
        text = {
            Column {
                OutlinedTextField(dateText, { dateText = it.trim() }, label = { Text("A day the traps run (yyyy-mm-dd)") }, singleLine = true, isError = d == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(everyText, { everyText = it }, label = { Text("Every (days)") }, singleLine = true, isError = every == null, modifier = Modifier.fillMaxWidth())
                if (d != null && every != null) Hint("Next: " + dayLabel(today + mod(d - today, every)) + ", then every $every days. Game days are UTC.")
            }
        },
        confirmButton = {
            TextButton(enabled = d != null && every != null, onClick = { onSave(Rule("every", date = dateText, every = every!!)); onClose() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun ItemRow(t: TimedItem, v: View, now: Long, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        @Composable fun info() {
            Text(withEmoji(t.emoji, t.label) + if (t.on) "" else " (off)")
            Hint(ruleSummary(t.rule, v) + if (t.group.isNotBlank()) " · ${t.group}" else "")
        }
        val time = normTime(t.time) ?: t.time
        val local = timePreview(t.time, now).substringAfter(" · ")
        if (isCompact(maxWidth)) Column {
            info()
            Text("$time · $local", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { info() }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(time, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Hint(local)
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
        title = { DialogTitle(displayEmoji(start.emoji).ifBlank { "🕒" }, if (isNew) "New time" else start.label.ifBlank { "Time" }, onDelete = if (isNew) null else ({ onDelete(); onClose() })) },
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
                Hint("Items with the same group get one selector per character instead of a switch each.")
                SwitchRow("Active", t.on) { t = t.copy(on = it) }
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
