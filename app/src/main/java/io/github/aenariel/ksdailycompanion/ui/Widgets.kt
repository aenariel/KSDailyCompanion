package io.github.aenariel.ksdailycompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Remembers which section cards are open (like KSPlanner's cards). */
object UiPrefs {
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("ui", 0)
    fun isOpen(ctx: android.content.Context, key: String, default: Boolean) = prefs(ctx).getBoolean("card:$key", default)
    fun setOpen(ctx: android.content.Context, key: String, open: Boolean) = prefs(ctx).edit().putBoolean("card:$key", open).apply()
    fun accent(ctx: android.content.Context): Accent = Accent.of(prefs(ctx).getString("accent", null))
    fun setAccent(ctx: android.content.Context, a: Accent) = prefs(ctx).edit().putString("accent", a.name).apply()
}

/**
 * Bordered section with an upper-case title and a line under it.
 * With a [key] it opens and closes on tap and remembers it.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    key: String? = null,
    defaultOpen: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember(key) { mutableStateOf(key == null || UiPrefs.isOpen(ctx, key, defaultOpen)) }
    Surface(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .then(if (key != null) Modifier.clickable { open = !open; UiPrefs.setOpen(ctx, key, open) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (key != null) Text(if (open) "▾ " else "▸ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                trailing()
            }
            if (open) {
                HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

/** Dialog title: tab icon + name, and a bin that asks once more before deleting. */
@Composable
fun DialogTitle(icon: String, text: String, onDelete: (() -> Unit)? = null) {
    var armed by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$icon $text", modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (onDelete != null) TextButton(onClick = { if (armed) onDelete() else armed = true }) {
            Text(if (armed) "Delete?" else "🗑️", color = if (armed) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                style = if (armed) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge)
        }
    }
}

/** A problem shown inside the dialog that caused it (never behind it). */
@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}

/** Label on the left, value on the right; the whole row is tappable. */
@Composable
fun ValueRow(label: String, value: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Hint(sub)
        }
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

/** List entry: tap to open, long-press for a menu. Entries named "Delete" are shown in red. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MenuCard(onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>, content: @Composable RowScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Box {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = { open = true }),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, act) in menu) DropdownMenuItem(
                text = { Text(label, color = if (label == "Delete") MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) },
                onClick = { open = false; act() },
            )
        }
    }
}

/** Yes/no question for actions started from a menu. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onClose: () -> Unit, error: String? = null) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Column { Text(text); ErrorText(error) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
