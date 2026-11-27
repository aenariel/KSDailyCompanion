package io.github.aenariel.ksdailycompanion.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.notify.Notifier
import java.time.ZoneId

private const val ALL = "*"

@Composable
fun AgendaScreen(model: AppModel, modifier: Modifier, onGo: (Tab) -> Unit) {
    val s = model.state
    val now = model.now
    val zone = ZoneId.systemDefault()
    val today = dayOf(now)
    var who by remember { mutableStateOf(ALL) }
    var choosing by remember { mutableStateOf<Occurrence?>(null) }
    var battleFor by remember { mutableStateOf<Pair<String, Int>?>(null) }   // alliance, day
    var soloFor by remember { mutableStateOf<Occurrence?>(null) }
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.reload()
        if (!granted) openNotificationSettings(ctx)   // refused before: only the system settings can turn it on
    }

    val shown = s.copy(characters = s.characters.filter { who == ALL || it.id == who })
    val occ = remember(shown, today) { occurrences(shown, today, today + 6) }
    val kingdoms = shown.characters.filter { it.on }.mapNotNull { s.alliances[it.alliance]?.kingdom }.distinct()
    val byId = s.characters.associateBy { it.id }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            ScreenHeader(Tab.AGENDA, "Next 7 days", "Game days are UTC days. Times: UTC · your local time (${zone.id}).")
            if (!model.notificationsAllowed) SectionCard("Notifications are off") {
                Text("Reminders can't show until notifications are allowed for this app.")
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                }) { Text("Allow notifications") }
            }
            if (s.characters.isEmpty()) SectionCard("Getting started") {
                val steps = listOf(
                    Triple(Tab.KINGDOMS, "Add your kingdom", s.kingdoms.isNotEmpty()),
                    Triple(Tab.ALLIANCES, "Add your alliance: Bear Trap and legion times", s.alliances.isNotEmpty()),
                    Triple(Tab.CHARACTERS, "Add your characters", false),
                )
                for ((i, st) in steps.withIndex()) {
                    val (tab, text, done) = st
                    ValueRow("${i + 1}. ${tab.icon} $text", if (done) "✓" else "Open") { onGo(tab) }
                }
                Hint("Or import a KSDailyAgenda backup in Settings.")
            } else if (s.characters.size > 1) {
                Picker("Showing", who, listOf(ALL to "All characters") + s.characters.map { it.id to charLabel(s, it) }) { who = it }
            }
        }
        for (n in today..today + 6) {
            val rows = occ.filter { it.day == n }
            item(key = "day$n") {
                SectionCard((if (n == today) "Today · " else if (n == today + 1) "Tomorrow · " else "") + dayLabel(n)) {
                    val global = globalEvents(s, n)
                    if (global.isNotEmpty()) {
                        GroupLabel("🌍 Global")
                        for (e in global) EventRow(e)
                    }
                    for (k in kingdoms) {
                        val ev = kingdomEvents(s, k, n)
                        if (ev.isEmpty()) continue
                        GroupLabel("🏰 " + kingdomTag(k))
                        for (e in ev) EventRow(e)
                    }
                    if (rows.isNotEmpty() || s.characters.isNotEmpty()) GroupLabel("🕒 Scheduled")
                    if (rows.isEmpty() && s.characters.isNotEmpty()) Hint("Nothing with a time.", Modifier.padding(vertical = 4.dp))
                    for ((o, chars) in mergeRows(rows, byId)) OccurrenceRow(o, chars, now, zone) {
                        when {
                            o.key == BATTLE_KEY -> battleFor = o.alliance to o.day
                            o.solo != null -> soloFor = o
                            else -> choosing = o
                        }
                    }
                    val trials = trialsOn(s, n)
                    if (trials.isNotEmpty()) {
                        GroupLabel("🔮 Mystic Trials")
                        TrialsTable(trials)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    choosing?.let { o -> LegionDialog(model, o) { choosing = null } }
    battleFor?.let { (al, day) -> BattleDialog(model, al, day) { battleFor = null } }
    soloFor?.let { o -> SoloDialog(model, o) { soloFor = null } }
}

/** Small accent label inside a day card: GLOBAL, #1130, TIMES. */
@Composable
internal fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** Halfway between the row colour and a highlight colour: marks without shouting. */
private fun soft(base: Color, tint: Color): Color = androidx.compose.ui.graphics.lerp(base, tint, 0.5f)

/** An event without a time: same layout as the time rows. Last day and one-day events use the accent; KvK the second accent.
 *  With little room (narrow screen or large text) the status goes on its own line above the name. */
@Composable
internal fun EventRow(e: DayEvent) {
    val marked = eventMarked(e)
    val hl = kvkHighlight()
    // Marked events: a softened background, no bar (the bar is the characters' colour on scheduled rows)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg = when { e.highlight -> soft(base, hl.container); marked -> soft(base, MaterialTheme.colorScheme.primaryContainer); else -> base }
    val fg = when { e.highlight -> hl.onContainer; marked -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurface }
    val note = eventEndNote(e)
    @Composable fun status() = Text(eventStatus(e), style = MaterialTheme.typography.labelLarge,
        fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal, color = if (marked || e.highlight) fg else MaterialTheme.colorScheme.onSurfaceVariant)
    @Composable fun name(m: Modifier) = Text(withEmoji(e.emoji, e.name), modifier = m, color = fg,
        fontWeight = if (marked || e.highlight) FontWeight.SemiBold else FontWeight.Normal)
    Surface(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp), color = bg) {
        BoxWithConstraints {
            val compact = isCompact(maxWidth)
            if (compact) Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    status()
                    if (note != null) Text(" · $note", style = MaterialTheme.typography.bodySmall, color = fg)
                }
                name(Modifier)
            } else Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    status()
                    if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = fg)
                }
                name(Modifier.weight(1f))
            }
        }
    }
}

/** Same thing for several characters of one alliance in one row; legion and Eternity picks stay per character. */
internal fun mergeRows(rows: List<Occurrence>, byId: Map<String, Character>): List<Pair<Occurrence, List<Character>>> {
    val out = LinkedHashMap<String, Pair<Occurrence, MutableList<Character>>>()
    for (o in rows) {
        val personal = o.choice != null || o.solo != null
        val k = if (personal) "${o.charId}|${o.key}|${o.minute}|${o.kind}" else "${o.key}|${o.minute}|${o.kind}|${o.title}|${o.where}"
        val ch = byId[o.charId] ?: continue
        out.getOrPut(k) { o to ArrayList() }.second += ch
    }
    return out.values.toList()
}

/** Mystic Trials: a small table with thin lines, troop ratios in columns (short headings when there is little room). */
@Composable
internal fun TrialsTable(trials: List<Trial>) {
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints {
        val compact = isCompact(maxWidth)
        val heads = if (compact) listOf("Inf.", "Cav.", "Arch.") else listOf("Infantry", "Cavalry", "Archers")
        val nameW = if (compact) 2.2f else 2.6f
        Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, line), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Spacer(Modifier.weight(nameW))
                for (h in heads) Text(h, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
            for (t in trials) {
                HorizontalDivider(thickness = 0.5.dp, color = line)
                val parts = t.ratio.split('/').map { it.trim() }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(withEmoji(t.emoji, t.name), Modifier.weight(nameW), style = MaterialTheme.typography.bodyMedium)
                    for (i in 0..2) Text(parts.getOrNull(i) ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Eternity's Reach: the slot this character plays (or not playing), for one occurrence. */
@Composable
private fun SoloDialog(model: AppModel, o: Occurrence, onClose: () -> Unit) {
    val info = o.solo ?: return
    val ch = model.state.characters.firstOrNull { it.id == o.charId }
    val usual = ch?.usual?.get(info.event.name)
    fun set(value: String?) {
        model.update("solo ${o.charName} ${info.occKey} -> ${value ?: "usual"}") { s ->
            val m = (s.picks[o.charId] ?: emptyMap()).toMutableMap()
            if (value == null) m.remove(info.occKey) else m[info.occKey] = value
            s.copy(picks = s.picks + (o.charId to m))
        }
        onClose()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(displayEmoji(o.emoji).ifBlank { "🔅" }, info.event.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch != null) ColorDot(ch.color)
                    Text(" " + o.charName, fontWeight = FontWeight.SemiBold)
                }
                Hint("${dayLabel(o.day)}. Pick the time slot you registered for.")
                Spacer(Modifier.height(6.dp))
                for (h in info.event.slots) {
                    val ms = o.day * DAY_MS + h * 60 * MIN_MS
                    OutlinedButton(onClick = { set(h.toString()) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text((if (info.selected == h.toString()) "✓ " else "") + fmtBoth(ms, ZoneId.systemDefault()), modifier = Modifier.weight(1f))
                    }
                }
                OutlinedButton(onClick = { set(NONE) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text((if (info.selected == NONE) "✓ " else "") + "Not playing", modifier = Modifier.weight(1f))
                }
                if (info.explicit) TextButton(onClick = { set(null) }) {
                    Text("Use the usual choice" + (usual?.let { u -> " (" + (if (u == NONE) "not playing" else "%02d:00 UTC".format(u.toIntOrNull() ?: 0)) + ")" } ?: " (ask)"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Friday Sanctuary & Fortress hours for one alliance: any of the voted slots. */
@Composable
private fun BattleDialog(model: AppModel, alliance: String, day: Int, onClose: () -> Unit) {
    val key = diso(day)
    var picked by remember { mutableStateOf(model.state.battles[alliance]?.get(key).orEmpty().toSet()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(BATTLE_EMOJI, "$BATTLE_TITLE ($alliance)") },
        text = {
            Column {
                Hint("${dayLabel(day)}. Pick the hours of your battles (the slot most voted at sign-up). One reminder per hour.")
                Spacer(Modifier.height(6.dp))
                for (h in battleSlots(model.state)) {
                    val ms = day * DAY_MS + h * 60 * MIN_MS
                    Row(Modifier.fillMaxWidth().clickable { picked = if (h in picked) picked - h else picked + h }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = h in picked, onCheckedChange = { on -> picked = if (on) picked + h else picked - h })
                        Text(fmtBoth(ms, ZoneId.systemDefault()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                model.update("battles $alliance $key -> ${picked.sorted()}") { st ->
                    val days = (st.battles[alliance] ?: emptyMap()).toMutableMap()
                    if (picked.isEmpty()) days.remove(key) else days[key] = picked.sorted()
                    // keep only recent weeks
                    days.keys.filter { (dnum(it) ?: 0) < day - 35 }.forEach { days.remove(it) }
                    st.copy(battles = st.battles + (alliance to days))
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A row with a time. Alliance and kingdom sit small in the top-right corner. With little room (narrow screen or
 *  large text) it stacks: time and alliance, then the title, then the characters and the button. */
@Composable
internal fun OccurrenceRow(o: Occurrence, chars: List<Character>, now: Long, zone: ZoneId, onChoose: () -> Unit) {
    val past = o.minute != null && o.atMs < now
    val clickable = o.choice != null || o.solo != null || o.key == BATTLE_KEY
    val hl = kvkHighlight()
    val status = when (o.kind) {
        Kind.CHOICE_PENDING -> if (o.solo != null) "time not set" else "legion not set"
        Kind.CHOICE_NONE -> "not playing"
        Kind.BATTLE_TBD -> "time TBD"
        Kind.TIMED -> if (o.choice != null && !o.choice.explicit) "usual legion"
            else if (o.solo != null && !o.solo.explicit) "usual time" else null
    }
    val chip = if (!clickable) null else when (o.kind) {
        Kind.CHOICE_PENDING -> if (o.solo != null) "✏️ Set time" else "✏️ Choose"
        Kind.BATTLE_TBD -> "✏️ Set time"
        else -> "✏️"
    }
    @Composable fun where() { if (o.where.isNotEmpty()) Text(o.where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    @Composable fun title(m: Modifier) = Text(withEmoji(o.emoji, o.title), m)
    Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).alpha(if (past) 0.5f else 1f).clip(RoundedCornerShape(8.dp))
        .let { if (clickable) it.clickable(onClick = onChoose) else it },
        shape = RoundedCornerShape(8.dp), color = if (o.highlight) soft(MaterialTheme.colorScheme.surfaceContainerHigh, hl.container) else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // the characters' colours: one stripe each when a row is shared
            Column(Modifier.width(6.dp).fillMaxHeight()) {
                for (c in chars) Box(Modifier.weight(1f).fillMaxWidth().background(if (c.color != 0) Color(c.color) else Color.Transparent))
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (isCompact(maxWidth)) Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            if (o.minute != null) {
                                Text(fmtUtc(o.atMs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Hint(fmtLocal(o.atMs, zone))
                            } else Text(if (o.kind == Kind.BATTLE_TBD) "TBD" else "—", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(8.dp))
                        where()
                    }
                    title(Modifier.padding(top = 4.dp))
                    CharDots(chars, Modifier.padding(top = 2.dp), status)
                    if (chip != null) Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.CenterEnd) { RowChip(chip) }
                } else Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(96.dp)) {
                        if (o.minute != null) {
                            Text(fmtUtc(o.atMs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Hint(fmtLocal(o.atMs, zone))
                        } else Text(if (o.kind == Kind.BATTLE_TBD) "TBD" else "—", style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            title(Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            where()
                        }
                        CharDots(chars, Modifier.padding(top = 2.dp), status)
                    }
                    if (chip != null) {
                        Spacer(Modifier.width(6.dp))
                        RowChip(chip)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegionDialog(model: AppModel, o: Occurrence, onClose: () -> Unit) {
    val c = o.choice ?: return
    val ch = model.state.characters.firstOrNull { it.id == o.charId }
    val usual = ch?.usual?.get(c.group.group)
    fun set(value: String?) {
        model.update("pick ${o.charName} ${c.occKey} -> ${value ?: "usual"}") { s ->
            val m = (s.picks[o.charId] ?: emptyMap()).toMutableMap()
            if (value == null) m.remove(c.occKey) else m[c.occKey] = value
            s.copy(picks = s.picks + (o.charId to m))
        }
        onClose()
    }
    val now = model.now
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(o.emoji.let { displayEmoji(it) }, shortName(c.group.group)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch != null) ColorDot(ch.color)
                    Text(" " + o.charName, fontWeight = FontWeight.SemiBold)
                }
                Hint("Week of ${c.occKey.substringAfterLast('@')}. Pick the legion you registered in.")
                Spacer(Modifier.height(8.dp))
                for (it in c.options) {
                    val selected = c.selected == it.id
                    OutlinedButton(onClick = { set(it.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text((if (selected) "✓ " else "") + Notifier.shortLabel(it.label) + " · " + timePreview(it.time, now), modifier = Modifier.weight(1f))
                    }
                }
                OutlinedButton(onClick = { set(NONE) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text((if (c.selected == NONE) "✓ " else "") + "Not playing", modifier = Modifier.weight(1f))
                }
                if (c.explicit) TextButton(onClick = { set(null) }) {
                    Text("Use the usual choice" + (usual?.let { u -> " (" + (if (u == NONE) "not playing" else c.options.firstOrNull { it.id == u }?.let { Notifier.shortLabel(it.label) } ?: u) + ")" } ?: " (ask)"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
