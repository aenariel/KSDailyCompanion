package io.github.aenariel.ksdailycompanion.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.reload()
        if (!granted) openNotificationSettings(ctx)   // refused before: only the system settings can turn it on
    }

    val shown = s.copy(characters = s.characters.filter { who == ALL || it.id == who })
    val occ = remember(shown, today) { occurrences(shown, today, today + 6) }
    val kingdoms = shown.characters.filter { it.on }.mapNotNull { s.alliances[it.alliance]?.kingdom }.distinct()
    val colors = s.characters.associate { it.id to it.color }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Text("Next 7 days", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Hint("Game days are UTC days. Times: UTC · your local time (${zone.id}).")
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
                Spacer(Modifier.height(8.dp))
                Picker("Showing", who, listOf(ALL to "All characters") + s.characters.map { it.id to charLabel(s, it) }) { who = it }
            }
        }
        for (n in today..today + 6) {
            val rows = occ.filter { it.day == n }
            item(key = "day$n") {
                SectionCard((if (n == today) "Today · " else if (n == today + 1) "Tomorrow · " else "") + dayLabel(n)) {
                    val global = globalEvents(s, n)
                    if (global.isNotEmpty()) {
                        GroupLabel("Global")
                        for (e in global) EventRow(e)
                    }
                    for (k in kingdoms) {
                        val ev = kingdomEvents(s, k, n)
                        if (ev.isEmpty()) continue
                        GroupLabel(kingdomTag(k))
                        for (e in ev) EventRow(e)
                    }
                    if (rows.isNotEmpty() || s.characters.isNotEmpty()) GroupLabel("Times")
                    if (rows.isEmpty() && s.characters.isNotEmpty()) Hint("Nothing with a time.", Modifier.padding(vertical = 4.dp))
                    for (o in rows) OccurrenceRow(o, colors[o.charId] ?: 0, now, zone, multi = s.characters.size > 1) {
                        if (o.key == BATTLE_KEY) battleFor = o.alliance to o.day else choosing = o
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    choosing?.let { o -> LegionDialog(model, o) { choosing = null } }
    battleFor?.let { (al, day) -> BattleDialog(model, al, day) { battleFor = null } }
}

/** Small accent label inside a day card: GLOBAL, #1130, TIMES. */
@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** An event without a time: same layout as the time rows; the last day stands out. */
@Composable
private fun EventRow(e: DayEvent) {
    val ends = e.endsToday
    val accent = MaterialTheme.colorScheme.primary
    Surface(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp),
        color = if (ends) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (ends) accent else Color.Transparent))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(eventStatus(e), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (ends) FontWeight.Bold else FontWeight.Normal,
                    color = if (ends) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(withEmoji(e.emoji, e.name), modifier = Modifier.weight(1f), fontWeight = if (ends) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (ends) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Friday Sanctuary & Fortress hours for one alliance: any of the voted slots. */
@Composable
private fun BattleDialog(model: AppModel, alliance: String, day: Int, onClose: () -> Unit) {
    val key = diso(day)
    var picked by remember { mutableStateOf(model.state.battles[alliance]?.get(key).orEmpty().toSet()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(BATTLE_EMOJI, "$BATTLE_TITLE · $alliance") },
        text = {
            Column {
                Hint("${dayLabel(day)}. Pick the hours of your battles (the slot most voted at sign-up). One reminder per hour.")
                Spacer(Modifier.height(6.dp))
                for (h in BATTLE_SLOTS) {
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

@Composable
private fun OccurrenceRow(o: Occurrence, color: Int, now: Long, zone: ZoneId, multi: Boolean, onChoose: () -> Unit) {
    val past = o.minute != null && o.atMs < now
    val clickable = o.choice != null || o.key == BATTLE_KEY
    Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).alpha(if (past) 0.5f else 1f).clip(RoundedCornerShape(8.dp))
        .let { if (clickable) it.clickable(onClick = onChoose) else it },
        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (color != 0) Color(color) else Color.Transparent))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    if (o.minute != null) {
                        Text(fmtUtc(o.atMs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Hint(fmtLocal(o.atMs, zone))
                    } else Text(if (o.kind == Kind.BATTLE_TBD) "TBD" else "—", style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text(withEmoji(o.emoji, o.title))
                    val sub = buildList {
                        if (multi) add(o.charName)
                        when (o.kind) {
                            Kind.CHOICE_PENDING -> add("legion not set — tap to choose")
                            Kind.CHOICE_NONE -> add("not playing — tap to change")
                            Kind.BATTLE_TBD -> add("time TBD — tap to set")
                            Kind.TIMED -> if (o.choice != null) add(if (o.choice.explicit) "tap to change" else "usual legion — tap to change")
                                else if (o.key == BATTLE_KEY) add("tap to change")
                        }
                    }
                    if (sub.isNotEmpty()) Hint(sub.joinToString(" · "))
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
        title = { DialogTitle(o.emoji.let { displayEmoji(it) }, c.group.group) },
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
