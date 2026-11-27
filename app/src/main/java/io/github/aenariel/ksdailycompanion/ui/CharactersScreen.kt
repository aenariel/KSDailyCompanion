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
    var deleting by remember { mutableStateOf<Character?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Characters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Hint("Tap to edit, long-press for more. Each character belongs to one alliance, which holds the times.")
        Spacer(Modifier.height(6.dp))
        for (c in s.characters) {
            val al = s.alliances[c.alliance]
            fun setOn(on: Boolean) = model.update("character ${c.name} on=$on") { st -> st.copy(characters = st.characters.map { if (it.id == c.id) it.copy(on = on) else it }) }
            MenuCard(onClick = { editing = c }, menu = listOf(
                "Edit" to { editing = c },
                "Duplicate" to { editing = c.copy(id = newId(), name = c.name, color = nextColor(s.characters)) },
                (if (c.on) "Turn notifications off" else "Turn notifications on") to { setOn(!c.on) },
                "Delete" to { deleting = c },
            )) {
                ColorDot(c.color, 16)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(charLabel(s, c), fontWeight = FontWeight.SemiBold)
                    if (al == null) Hint("Alliance missing: ${c.alliance}")
                    val usual = s.choiceGroups.mapNotNull { g ->
                        c.usual[g.group]?.let { u -> g.group + ": " + if (u == NONE) "not playing" else (al?.timed?.firstOrNull { it.id == u }?.let { Notifier.shortLabel(it.label) } ?: u) }
                    }
                    if (usual.isNotEmpty()) Hint("Usual: " + usual.joinToString(" · "))
                    if (!c.on) Hint("Notifications off")
                }
                Switch(checked = c.on, onCheckedChange = { setOn(it) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (s.alliances.isEmpty()) model.toast("Add an alliance first (Alliances tab)")
            else editing = Character(newId(), "", s.alliances.keys.first(), color = nextColor(s.characters))
        }) { Text("Add a character") }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { c -> CharacterDialog(model, c) { editing = null } }
    deleting?.let { c ->
        ConfirmDialog("Delete ${c.name}?", "${charLabel(s, c)} and its legion picks are removed.", "Delete",
            onConfirm = { model.update("delete character ${c.name}") { st -> st.copy(characters = st.characters.filter { it.id != c.id }, picks = st.picks - c.id) }; deleting = null },
            onClose = { deleting = null })
    }
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
        title = {
            DialogTitle(Tab.CHARACTERS.icon, if (isNew) "New character" else start.name.ifBlank { "Character" }, onDelete = if (isNew) null else ({
                model.update("delete character ${c.name}") { st -> st.copy(characters = st.characters.filter { it.id != c.id }, picks = st.picks - c.id) }
                onClose()
            }))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
              SectionCard("Character") {
                OutlinedTextField(c.name, { c = c.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Picker("Alliance", c.alliance, s.alliances.map { (n, a) -> n to "$n (${kingdomTag(a.kingdom)})" }) { c = c.copy(alliance = it) }
                Spacer(Modifier.height(10.dp))
                ColorPalette(c.color) { c = c.copy(color = it) }
              }
                if (al != null) SectionCard("Usual choices") {
                    Hint("Used when you don't pick for a given week. \"Ask\" sends a question (legions: on registration days; Eternity's Reach: the day before).")
                    for (g in s.choiceGroups) {
                        val opts = al.timed.filter { it.on && it.group == g.group }
                        if (opts.isEmpty()) continue
                        Picker(g.group, c.usual[g.group] ?: "",
                            listOf("" to "Ask") + opts.map { it.id to Notifier.shortLabel(it.label) + " · " + it.time } + listOf(NONE to "Not playing"),
                            Modifier.padding(vertical = 2.dp)) { v -> c = c.copy(usual = if (v.isEmpty()) c.usual - g.group else c.usual + (g.group to v)) }
                    }
                    for (se in soloEvents(s)) Picker(se.name, c.usual[se.name] ?: "",
                        listOf("" to "Ask") + se.slots.map { it.toString() to "%02d:00 UTC".format(it) } + listOf(NONE to "Not playing"),
                        Modifier.padding(vertical = 2.dp)) { v -> c = c.copy(usual = if (v.isEmpty()) c.usual - se.name else c.usual + (se.name to v)) }
                }
                if (al != null) SectionCard("Notify me about") {
                    // Groups with several items (e.g. Bear Traps) get one selector; the rest get a switch each
                    val timed = al.timed.filter { it.on && it.group !in choiceNames }
                    val groups = timed.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size > 1 }
                        .toList().sortedBy { if (it.first == BEAR_GROUP) 0 else 1 }.toMap()
                    for ((group, items) in groups) {
                        val keys = items.map { "t:${it.id}" }
                        val onItems = items.filter { "t:${it.id}" !in c.off }
                        val current = when {
                            onItems.size == items.size -> ALL_ITEMS
                            onItems.isEmpty() -> NO_ITEMS
                            onItems.size == 1 -> onItems[0].id
                            else -> CUSTOM
                        }
                        val opts = items.map { it.id to "${Notifier.shortLabel(it.label)} · ${normTime(it.time) ?: "not set"}" } +
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
                    rows += Triple(BATTLE_KEY, withEmoji(BATTLE_EMOJI, BATTLE_TITLE), "Fridays; set the hours in the agenda")
                    for (se in soloEvents(s)) rows += Triple(se.key, withEmoji(viewFor(s, al.kingdom).event(se.id)?.emoji ?: "", se.name), "your time slot, asked the day before")
                    for ((key, label, sub) in rows) SwitchRow(label, key !in c.off, sub) { on -> c = c.copy(off = if (on) c.off - key else c.off + key) }
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
