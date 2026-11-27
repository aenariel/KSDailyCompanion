package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aenariel.ksdailycompanion.model.*
import java.time.ZoneId

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A button showing the current choice; tapping opens a menu. */
@Composable
fun <T> Picker(label: String, current: T, options: List<Pair<T, String>>, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + (options.firstOrNull { it.first == current }?.second ?: "—"), modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((v, text) in options) DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(v) })
        }
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text)
            if (sub != null) Hint(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun TextDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, numeric: Boolean = false, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default)
                Spacer(Modifier.height(6.dp))
                Hint(hint)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "20", "20 UTC", "7:30" -> "20:00 UTC"; null if not an hour. */
fun normTime(text: String): String? = parseTime(text)?.takeIf { it < 24 * 60 }?.let { timeString(it) }

/** "12:00 UTC · 13:00 local" for today's date (local time depends on the date because of summer time). */
fun timePreview(time: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    parseTime(time)?.let { fmtBoth(dayOf(now) * DAY_MS + it * MIN_MS, zone) } ?: time

fun ruleSummary(r: Rule, v: View): String = when (r.type) {
    "every" -> if (r.every == 1) "every day" else "every ${r.every} days from ${r.date}"
    "date" -> "on ${r.date}"
    "weekday" -> "on " + r.days.sorted().joinToString(", ") { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrElse(it) { "?" } }
    "event" -> {
        val name = v.event(r.ev)?.name ?: r.ev
        val to = if (r.to != 0) r.to else r.from
        "$name, day ${r.from}" + (if (to != r.from) "–$to" else "")
    }
    else -> r.type
}

@Composable
fun ColorDot(color: Int, size: Int = 12, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.size(size.dp).background(androidx.compose.ui.graphics.Color(color), androidx.compose.foundation.shape.CircleShape)
    )
}

/** Row of colour circles; the chosen one has a ring. */
@Composable
fun ColorPalette(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (c in PALETTE) {
            val ring = if (c == selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent
            Box(
                Modifier.size(28.dp).border(2.dp, ring, androidx.compose.foundation.shape.CircleShape).padding(4.dp)
                    .background(androidx.compose.ui.graphics.Color(c), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onPick(c) }
            )
        }
    }
}

/** Kingdom numbers only: "#1130" or "1130" -> "1130"; null when not a number. */
fun kingdomNumber(text: String): String? = text.trim().removePrefix("#").trim().takeIf { it.isNotEmpty() && it.length <= 6 && it.all { c -> c.isDigit() } }

/** Alliance tags are exactly 3 letters or digits. */
fun allianceTag(text: String): String? = text.trim().takeIf { it.length == 3 && it.all { c -> c.isLetterOrDigit() } }

/** Confirm-by-second-tap delete button. */
@Composable
fun DeleteButton(label: String, onDelete: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    TextButton(onClick = { if (armed) onDelete() else armed = true }) {
        Text(if (armed) "Tap again to delete" else label, color = MaterialTheme.colorScheme.error)
    }
}
