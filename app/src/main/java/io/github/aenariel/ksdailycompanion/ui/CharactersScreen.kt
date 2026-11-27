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

@Composable
fun CharactersScreen(model: AppModel, modifier: Modifier) {
    val s = model.state
    var editing by remember { mutableStateOf<Character?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Characters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Hint("Each character belongs to one alliance; the alliance holds the times (Alliances tab).")
        for (c in s.characters) {
            val al = s.alliances[c.alliance]
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = c }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(c.color, 16)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(charLabel(s, c), fontWeight = FontWeight.SemiBold)
                        if (al == null) Hint("Alliance missing: ${c.alliance}")
                        val usual = s.choiceGroups.mapNotNull { g ->
                            c.usual[g.group]?.let { u -> g.group + ": " + if (u == NONE) "not playing" else (al?.timed?.firstOrNull { it.id == u }?.let { Notifier.shortLabel(it.label) } ?: u) }
                        }
                        if (usual.isNotEmpty()) Hint("Usual: " + usual.joinToString(" · "))
                    }
                    Switch(checked = c.on, onCheckedChange = { on -> model.update("character ${c.name} on=$on") { st -> st.copy(characters = st.characters.map { if (it.id == c.id) it.copy(on = on) else it }) } })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { editing = Character(newId(), "", s.alliances.keys.firstOrNull() ?: "", color = nextColor(s.characters)) }) { Text("Add a character") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { c -> CharacterDialog(model, c) { editing = null } }
}

private const val ALL_ITEMS = "*all"
private const val NO_ITEMS = "*none"
private const val CUSTOM = "*custom"

@Composable
private fun CharacterDialog(model: AppModel, start: Character, onClose: () -> Unit) {
    val s = model.state
    val isNew = s.characters.none { it.id == start.id }
    var c by remember { mutableStateOf(start) }
    val al = s.alliances[c.alliance]
    val choiceNames = s.choiceGroups.map { it.group }.toSet()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "New character" else c.name.ifBlank { "Character" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(c.name, { c = c.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Picker("Alliance", c.alliance, s.alliances.map { (n, a) -> n to "$n (${kingdomTag(a.kingdom)})" }) { c = c.copy(alliance = it) }
                SectionTitle("Colour")
                ColorPalette(c.color) { c = c.copy(color = it) }
                if (al != null) {
                    SectionTitle("Usual legion")
                    Hint("Used when you don't pick one for a given week. \"Ask\" sends a question on registration days.")
                    for (g in s.choiceGroups) {
                        val opts = al.timed.filter { it.on && it.group == g.group }
                        if (opts.isEmpty()) continue
                        Picker(g.group, c.usual[g.group] ?: "",
                            listOf("" to "Ask") + opts.map { it.id to Notifier.shortLabel(it.label) + " · " + it.time } + listOf(NONE to "Not playing"),
                            Modifier.padding(vertical = 2.dp)) { v -> c = c.copy(usual = if (v.isEmpty()) c.usual - g.group else c.usual + (g.group to v)) }
                    }
                    SectionTitle("Notify me about")
                    // Groups with several items (e.g. Bear Traps) get one selector; the rest get a switch each
                    val timed = al.timed.filter { it.on && it.group !in choiceNames }
                    val groups = timed.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size > 1 }
                    for ((group, items) in groups) {
                        val keys = items.map { "t:${it.id}" }
                        val onItems = items.filter { "t:${it.id}" !in c.off }
                        val current = when {
                            onItems.size == items.size -> ALL_ITEMS
                            onItems.isEmpty() -> NO_ITEMS
                            onItems.size == 1 -> onItems[0].id
                            else -> CUSTOM
                        }
                        val opts = items.map { it.id to "${it.label} · ${normTime(it.time) ?: it.time}" } +
                            listOf(ALL_ITEMS to "All", NO_ITEMS to "None") + (if (current == CUSTOM) listOf(CUSTOM to "Some") else emptyList())
                        Picker(withEmoji(items[0].emoji, group), current, opts, Modifier.padding(vertical = 2.dp)) { v ->
                            if (v != CUSTOM) {
                                val offNow = when (v) {
                                    ALL_ITEMS -> emptySet()
                                    NO_ITEMS -> keys.toSet()
                                    else -> keys.filter { it != "t:$v" }.toSet()
                                }
                                c = c.copy(off = c.off - keys.toSet() + offNow)
                            }
                        }
                    }
                    val rows = ArrayList<Triple<String, String, String>>()
                    for (t in timed.filter { it.group !in groups.keys }) rows += Triple("t:${t.id}", withEmoji(t.emoji, t.label), normTime(t.time) ?: t.time)
                    for (g in s.choiceGroups) if (al.timed.any { it.on && it.group == g.group }) rows += Triple("g:${g.group}", g.group, "legion battles and registration")
                    for (ev in viewFor(s, al.kingdom).cycle.filter { it.on && it.startTime.isNotBlank() }) rows += Triple("c:${ev.id}", withEmoji(ev.emoji, ev.name), ev.startTime)
                    for ((key, label, sub) in rows) SwitchRow(label, key !in c.off, sub) { on -> c = c.copy(off = if (on) c.off - key else c.off + key) }
                }
                if (!isNew) {
                    Spacer(Modifier.height(12.dp))
                    DeleteButton("Delete character") {
                        model.update("delete character ${c.name}") { st -> st.copy(characters = st.characters.filter { it.id != c.id }, picks = st.picks - c.id) }
                        onClose()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = c.name.isNotBlank() && c.alliance in s.alliances, onClick = {
                val fixed = c.copy(name = c.name.trim())
                model.update(if (isNew) "add character ${fixed.name}" else "edit character ${fixed.name}") { st ->
                    st.copy(characters = if (isNew) st.characters + fixed else st.characters.map { if (it.id == fixed.id) fixed else it })
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
