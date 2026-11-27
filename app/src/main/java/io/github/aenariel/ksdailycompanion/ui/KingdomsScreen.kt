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

private val DEFAULT_KINGDOM_IDS = defaultKingdom("old").cycle.map { it.id }.toSet()

/** Kingdoms hold the dates of their own rotating events; every alliance in the kingdom follows them. */
@Composable
fun KingdomsScreen(model: AppModel, modifier: Modifier) {
    val s = model.state
    val today = dayOf(model.now)
    var editing by remember { mutableStateOf<Pair<String?, Kingdom>?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        ScreenHeader(Tab.KINGDOMS, hint = "Tap to edit, long-press for more. Each kingdom has the dates of its own rotating events; every alliance in it follows them.")
        for ((name, k) in s.kingdoms) {
            val v = kingdomView(s, k)
            val alliances = s.alliances.filterValues { it.kingdom == name }.keys
            MenuCard(onClick = { editing = name to k }, menu = listOf(
                "Edit" to { editing = name to k },
                "Duplicate" to { editing = null to k },
                "Delete" to { deleting = name },
            )) {
                Column(Modifier.weight(1f)) {
                    Text(kingdomTag(name) + if (k.variant == "old") " · Gen 4+" else "", fontWeight = FontWeight.SemiBold)
                    Hint("Alliances: " + alliances.joinToString(", ").ifEmpty { "none" })
                    CharactersLine(s.characters.filter { s.alliances[it.alliance]?.kingdom == name })
                    Spacer(Modifier.height(6.dp))
                    for (ev in k.cycle.filter { it.on }) Hint(withEmoji(ev.emoji, ev.name) + " · " + runText(v, ev, today), Modifier.padding(start = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { editing = null to defaultKingdom("young") }) { Text("Add a kingdom") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { (name, k) -> KingdomDialog(model, name, k) { editing = null } }
    deleting?.let { name ->
        val used = s.alliances.filterValues { it.kingdom == name }.keys
        ConfirmDialog("Delete ${kingdomTag(name)}?", "Its event dates are removed.", "Delete",
            error = if (used.isEmpty()) null else "Move ${used.joinToString()} to another kingdom first.",
            onConfirm = { if (used.isEmpty()) { model.update("delete kingdom $name") { it.copy(kingdoms = it.kingdoms - name) }; deleting = null } },
            onClose = { deleting = null })
    }
}

private fun kingdomView(s: CompanionState, k: Kingdom) = View(s.shared.anchor, k.cycle, k.special)

/** "day 2/3, ends Sat 28/11" or "next Wed 09/12". */
private fun runText(v: View, ev: CycleEvent, today: Int): String {
    val h = currentOrNext(v, ev, today)
    return if (h.dayIdx > 0) "now, day ${h.dayIdx}/${h.end - h.start + 1}, ends ${dayLabel(h.end)}" else "next ${dayLabel(h.start)}"
}

@Composable
private fun KingdomDialog(model: AppModel, original: String?, start: Kingdom, onClose: () -> Unit) {
    val s = model.state
    val today = dayOf(model.now)
    var number by remember { mutableStateOf(original?.let { kingdomNumber(it) } ?: "") }
    var k by remember { mutableStateOf(start) }
    var editingEvent by remember { mutableStateOf<CycleEvent?>(null) }
    var editingSpecial by remember { mutableStateOf<SpecialEvent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = kingdomNumber(number)
    val taken = valid != null && valid != original && valid in s.kingdoms
    val alliances = if (original == null) emptyList() else s.alliances.filterValues { it.kingdom == original }.keys.toList()
    val v = kingdomView(s, k)

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            DialogTitle(Tab.KINGDOMS.icon, if (original == null) "New kingdom" else kingdomTag(original), onDelete = if (original == null) null else ({
                if (alliances.isNotEmpty()) error = "Move ${alliances.joinToString()} to another kingdom first."
                else { model.update("delete kingdom $original") { it.copy(kingdoms = it.kingdoms - original) }; onClose() }
            }))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
              ErrorText(error)
              SectionCard("Kingdom") {
                OutlinedTextField(number, { t -> number = t.filter { it.isDigit() }.take(6) }, label = { Text("Kingdom number") },
                    prefix = { Text("#") }, singleLine = true, isError = valid == null || taken, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                Hint(when {
                    taken -> "#$valid already exists"
                    valid == null -> "Numbers only"
                    else -> "Alliances in it: " + alliances.joinToString(", ").ifEmpty { "none" }
                })
                SwitchRow("Gen 4+", k.variant == "old", "Desert Trial becomes Champion's Way; dates stay") { k = withVariant(k, it) }
              }
              SectionCard("Rotating events") {
                Hint("Tap to set the date it starts; it repeats from there. Switch off what this kingdom doesn't have.")
                for (ev in k.cycle) {
                    Row(Modifier.fillMaxWidth().clickable { editingEvent = ev }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(withEmoji(ev.emoji, ev.name))
                            Hint("${ev.len} day(s) every ${ev.every} · " + runText(v, ev, today))
                        }
                        Switch(checked = ev.on, onCheckedChange = { on -> k = k.copy(cycle = k.cycle.map { if (it.id == ev.id) it.copy(on = on) else it }) })
                    }
                }
                TextButton(onClick = { editingEvent = CycleEvent(newId(), "", "", 0, 1, 28) }) { Text("Add a rotating event") }
              }
              SectionCard("One-off events") {
                Hint("Events with fixed dates in this kingdom (e.g. Buccaneer Bounty).")
                for (sp in k.special) {
                    Row(Modifier.fillMaxWidth().clickable { editingSpecial = sp }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(withEmoji(sp.emoji, sp.name))
                            Hint(sp.from + if (sp.to.isNotBlank() && sp.to != sp.from) " – ${sp.to}" else "")
                        }
                        Switch(checked = sp.on, onCheckedChange = { on -> k = k.copy(special = k.special.map { if (it.id == sp.id) it.copy(on = on) else it }) })
                    }
                }
                TextButton(onClick = { editingSpecial = SpecialEvent(newId(), "", "", diso(today), diso(today + 6)) }) { Text("Add a one-off event") }
              }
            }
        },
        confirmButton = {
            TextButton(enabled = valid != null && !taken, onClick = {
                val n = valid ?: return@TextButton
                model.update(if (original == null) "add kingdom #$n" else "edit kingdom $original -> #$n") { st ->
                    val map = LinkedHashMap<String, Kingdom>()
                    for ((name, x) in st.kingdoms) if (name == original) map[n] = k else map[name] = x
                    if (original == null) map[n] = k
                    st.copy(kingdoms = map, alliances = st.alliances.mapValues { (_, a) -> if (original != null && a.kingdom == original) a.copy(kingdom = n) else a })
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )

    editingEvent?.let { ev ->
        val isNew = k.cycle.none { it.id == ev.id }
        RotatingEventDialog(v, ev, isNew, today,
            onSave = { x -> k = k.copy(cycle = if (isNew) k.cycle + x else k.cycle.map { if (it.id == x.id) x else it }) },
            onDelete = if (isNew || ev.id in DEFAULT_KINGDOM_IDS) null else ({ k = k.copy(cycle = k.cycle.filter { it.id != ev.id }) }),
            onClose = { editingEvent = null })
    }
    editingSpecial?.let { sp ->
        val isNew = k.special.none { it.id == sp.id }
        SpecialDialog(sp, isNew,
            onSave = { x -> k = k.copy(special = if (isNew) k.special + x else k.special.map { if (it.id == x.id) x else it }) },
            onDelete = { k = k.copy(special = k.special.filter { it.id != sp.id }) },
            onClose = { editingSpecial = null })
    }
}

@Composable
private fun RotatingEventDialog(v: View, start: CycleEvent, isNew: Boolean, today: Int, onSave: (CycleEvent) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    var e by remember { mutableStateOf(start) }
    var dateText by remember { mutableStateOf(diso(currentOrNext(v, start, today).start)) }
    var lenText by remember { mutableStateOf(start.len.toString()) }
    var everyText by remember { mutableStateOf(start.every.toString()) }
    var daysText by remember { mutableStateOf(start.days.joinToString(",")) }
    val date = dnum(dateText)
    val shownDays = daysText.split(',', ';', ' ').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sorted()
    val len = lenText.toIntOrNull()?.takeIf { it in 1..60 }
    val every = everyText.toIntOrNull()?.takeIf { it in 1..365 }
    val built = if (date != null && len != null && every != null && e.name.isNotBlank())
        e.copy(name = e.name.trim(), len = len, every = every, start = startFromDate(v, date, every), days = shownDays) else null

    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(displayEmoji(start.emoji).ifBlank { "🔁" }, if (isNew) "New rotating event" else start.name, onDelete = onDelete?.let { d -> { d(); onClose() } }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(e.name, { e = e.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e.emoji, { e = e.copy(emoji = it) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(120.dp))
                OutlinedTextField(dateText, { dateText = it.trim() }, label = { Text("Starts on (yyyy-mm-dd)") }, singleLine = true, isError = date == null, modifier = Modifier.fillMaxWidth())
                Hint("Any day it starts: past or next, the cycle is the same. Game days are UTC.")
                Row {
                    OutlinedTextField(lenText, { lenText = it }, label = { Text("Lasts (days)") }, singleLine = true, isError = len == null, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(everyText, { everyText = it }, label = { Text("Every (days)") }, singleLine = true, isError = every == null, modifier = Modifier.weight(1f))
                }
                if (built != null) {
                    val h = currentOrNext(v, built, today)
                    Hint(if (h.dayIdx > 0) "Running now: day ${h.dayIdx}, ends ${dayLabel(h.end)}" else "Next: ${dayLabel(h.start)} – ${dayLabel(h.end)}")
                }
                OutlinedTextField(daysText, { daysText = it }, label = { Text("Show on days (e.g. 1,3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Hint("Days of the event to show; empty = every day.")
                SwitchRow("Mark the last day", e.lastDay, "\"Last day\" on its final day") { e = e.copy(lastDay = it) }
                SwitchRow("Active", e.on) { e = e.copy(on = it) }
            }
        },
        confirmButton = { TextButton(enabled = built != null, onClick = { built?.let(onSave); onClose() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun SpecialDialog(start: SpecialEvent, isNew: Boolean, onSave: (SpecialEvent) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var e by remember { mutableStateOf(start) }
    val from = dnum(e.from)
    val to = if (e.to.isBlank()) from else dnum(e.to)
    val ok = e.name.isNotBlank() && from != null && to != null && to >= from
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(displayEmoji(start.emoji).ifBlank { "📌" }, if (isNew) "New one-off event" else start.name, onDelete = if (isNew) null else ({ onDelete(); onClose() })) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(e.name, { e = e.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e.emoji, { e = e.copy(emoji = it) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(120.dp))
                Row {
                    OutlinedTextField(e.from, { e = e.copy(from = it.trim()) }, label = { Text("From") }, singleLine = true, isError = from == null, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(e.to, { e = e.copy(to = it.trim()) }, label = { Text("To") }, singleLine = true, isError = to == null || (from != null && to < from), modifier = Modifier.weight(1f))
                }
                Hint("Dates as yyyy-mm-dd, UTC game days.")
                SwitchRow("Active", e.on) { e = e.copy(on = it) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(e.copy(name = e.name.trim())); onClose() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
