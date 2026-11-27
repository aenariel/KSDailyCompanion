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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.notify.Notifier
import java.time.ZoneId

private const val ALL = "*"

@Composable
fun AgendaScreen(model: AppModel, modifier: Modifier, onSetup: () -> Unit) {
    val s = model.state
    val now = model.now
    val zone = ZoneId.systemDefault()
    val today = dayOf(now)
    var who by remember { mutableStateOf(ALL) }
    var choosing by remember { mutableStateOf<Occurrence?>(null) }
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
            if (!model.notificationsAllowed) Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Notifications are off for this app.")
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                    }) { Text("Allow notifications") }
                }
            }
            if (s.characters.isEmpty()) Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("No characters yet.")
                    Hint("Add your characters and pick their alliance. Set the alliance's Bear Trap and legion times under Alliances.")
                    TextButton(onClick = onSetup) { Text("Add a character") }
                }
            } else if (s.characters.size > 1) {
                Spacer(Modifier.height(8.dp))
                Picker("Showing", who, listOf(ALL to "All characters") + s.characters.map { it.id to charLabel(s, it) }) { who = it }
            }
        }
        for (n in today..today + 6) {
            val rows = occ.filter { it.day == n }
            item(key = "day$n") {
                Text((if (n == today) "Today · " else if (n == today + 1) "Tomorrow · " else "") + dayLabel(n),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                for (k in kingdoms) {
                    val ev = dayEvents(viewFor(s, k), n)
                    if (ev.isEmpty()) continue
                    if (kingdoms.size > 1) Text(kingdomTag(k), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    for (e in ev) Hint(dayEventLabel(e), Modifier.padding(start = 8.dp))
                }
                if (rows.isEmpty()) Hint("Nothing with a time.", Modifier.padding(top = 4.dp))
            }
            items(rows, key = { "${it.charId}|${it.key}|${it.day}|${it.minute}|${it.title}" }) { o ->
                OccurrenceRow(o, colors[o.charId] ?: 0, now, zone, multi = s.characters.size > 1) { choosing = o }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    choosing?.let { o -> LegionDialog(model, o) { choosing = null } }
}

@Composable
private fun OccurrenceRow(o: Occurrence, color: Int, now: Long, zone: ZoneId, multi: Boolean, onChoose: () -> Unit) {
    val past = o.minute != null && o.atMs < now
    val clickable = o.choice != null
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).alpha(if (past) 0.5f else 1f).let { if (clickable) it.clickable(onClick = onChoose) else it }) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (color != 0) Color(color) else Color.Transparent))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    if (o.minute != null) {
                        Text(fmtUtc(o.atMs), fontWeight = FontWeight.SemiBold)
                        Hint(fmtLocal(o.atMs, zone))
                    } else Text("—")
                }
                Column(Modifier.weight(1f)) {
                    Text(withEmoji(o.emoji, o.title))
                    val sub = buildList {
                        if (multi) add(o.charName)
                        when (o.kind) {
                            Kind.CHOICE_PENDING -> add("legion not set — tap to choose")
                            Kind.CHOICE_NONE -> add("not playing — tap to change")
                            Kind.TIMED -> if (o.choice != null) add(if (o.choice.explicit) "tap to change" else "usual legion — tap to change")
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
        title = { Text(c.group.group) },
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
