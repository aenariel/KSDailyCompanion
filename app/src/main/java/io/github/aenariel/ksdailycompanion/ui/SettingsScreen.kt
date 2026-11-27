package io.github.aenariel.ksdailycompanion.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aenariel.ksdailycompanion.data.Codec
import io.github.aenariel.ksdailycompanion.data.Log
import io.github.aenariel.ksdailycompanion.data.Store
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.notify.Notifier
import java.time.ZoneId

fun openNotificationSettings(ctx: Context) {
    val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
    startSafely(ctx, i, appDetails(ctx))
}

private fun appDetails(ctx: Context) = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

private fun startSafely(ctx: Context, vararg intents: Intent) {
    for (i in intents) try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Log.i("Opened ${i.component?.className ?: i.action}"); return
    } catch (e: Exception) { Log.w("Could not open ${i.component?.className ?: i.action}: ${e.javaClass.simpleName}") }
}

/** Keys that can have their own reminder times, with a readable name: Bear Traps first, then legions, other times, events. */
private fun leadKeys(s: CompanionState): List<Pair<String, String>> {
    val out = LinkedHashMap<String, String>()
    val choice = s.choiceGroups.map { it.group }.toSet()
    out[groupKey(BEAR_GROUP)] = "🐻 $BEAR_GROUP"
    for (g in s.choiceGroups) out["g:${g.group}"] = withEmoji(s.alliances.values.flatMap { it.timed }.firstOrNull { it.group == g.group }?.emoji
        ?: viewFor(s, s.kingdoms.keys.firstOrNull() ?: "").event(g.ev)?.emoji ?: "", shortName(g.group))
    out[BATTLE_KEY] = withEmoji(BATTLE_EMOJI, BATTLE_TITLE)
    for (se in soloEvents(s)) out[se.key] = withEmoji(s.shared.cycle.firstOrNull { it.id == se.id }?.emoji ?: "", se.name)
    for (a in s.alliances.values) for (t in a.timed) {
        if (t.group in choice) continue
        if (t.group.isNotBlank()) out.putIfAbsent(groupKey(t.group), withEmoji(t.emoji, shortName(t.group)))
        else out.putIfAbsent("t:${t.id}", withEmoji(t.emoji, t.label))
    }
    for (k in s.kingdoms.keys.ifEmpty { setOf("") }) for (ev in viewFor(s, k).cycle) if (ev.startTime.isNotBlank()) out.putIfAbsent("c:${ev.id}", withEmoji(ev.emoji, ev.name))
    return out.toList()
}

@Composable
fun SettingsScreen(model: AppModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val s = model.state
    val st = s.settings
    val zone = ZoneId.systemDefault()
    var dialog by remember { mutableStateOf<String?>(null) }   // "leads", "lead:<key>", "prompt", "digest", "paste", "log"
    var showLog by remember { mutableStateOf(false) }
    var showFixed by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Codec.export(model.state).toByteArray()) }
            Log.i("Exported to $uri"); model.toast("Exported")
        } catch (e: Exception) { Log.e("Export failed", e); model.toast("Export failed: ${e.message}") }
    }
    fun importText(text: String) {
        try {
            val r = Codec.import(text, model.state)
            model.update("import") { r.state }
            Log.i("Import: ${r.message}"); model.toast(r.message)
        } catch (e: Exception) { Log.w("Import failed: ${e.message}"); model.toast("Import failed: ${e.message}") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            importText(text)
        } catch (e: Exception) { Log.e("Reading the import file failed", e); model.toast("Could not read the file") }
    }
    fun setSettings(why: String, f: (io.github.aenariel.ksdailycompanion.model.Settings) -> io.github.aenariel.ksdailycompanion.model.Settings) =
        model.update(why) { it.copy(settings = f(it.settings)) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        ScreenHeader(Tab.SETTINGS)

        SectionCard("Reminders", key = "set-reminders") {
            ValueRow("Default: before each event", fmtLeads(st.defaultLeads)) { dialog = "leads" }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Hint("Per event (tap to change; empty = default):", Modifier.padding(top = 6.dp))
            for ((key, label) in leadKeys(s)) {
                val own = st.leads[key]
                ValueRow(label, own?.let { fmtLeads(it) } ?: "default") { dialog = "lead:$key" }
            }
        }

        SectionCard("Questions", key = "set-legion") {
            ValueRow("Ask which legion at", timePreview(timeString(st.promptMin), model.now, zone),
                "On registration days, for each character without a pick for that week.") { dialog = "prompt" }
            ValueRow("Ask the Eternity's Reach time at", timePreview(timeString(st.soloPromptMin), model.now, zone),
                "The day before, for each character without a time.") { dialog = "solo" }
        }

        SectionCard("Appearance", key = "set-look") {
            Text("Accent colour")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (a in Accent.entries) {
                    val sel = a == model.accent
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { model.chooseAccent(a) }.padding(4.dp)) {
                        Box(Modifier.size(28.dp).border(2.dp, if (sel) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                            .padding(4.dp).background(androidx.compose.ui.graphics.Color(if (androidx.compose.foundation.isSystemInDarkTheme()) a.dark else a.light), CircleShape))
                        Text(a.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            SwitchRow("Show Mystic Trials", st.showTrials, "Trial of the day and its troop ratio, in the agenda") { on -> setSettings("trials on=$on") { it.copy(showTrials = on) } }
        }

        SectionCard("Arena", key = "set-arena") {
            SwitchRow("Remind me before the daily reset", st.arenaOn, "Arena attempts reset at 00:00 UTC") { on -> setSettings("arena on=$on") { it.copy(arenaOn = on) } }
            if (st.arenaOn) ValueRow("Before the reset", fmtLead(st.arenaLead), timePreview(timeString((24 * 60 - st.arenaLead) % (24 * 60)), model.now, zone)) { dialog = "arena" }
        }
        SectionCard("Daily summary", key = "set-digest") {
            SwitchRow("Send a summary of the day", st.digestOn) { on -> setSettings("digest on=$on") { it.copy(digestOn = on) } }
            if (st.digestOn) ValueRow("At", timePreview(timeString(st.digestMin), model.now, zone)) { dialog = "digest" }
        }

        SectionCard("Game events", key = "set-events", defaultOpen = false) {
            Hint("Fixed events v${s.sharedVersion} (cycle from ${s.shared.anchor}), shared with KSDailyAgenda and the same in every kingdom. Switched-off events are hidden from the agenda and the summary.")
            Row {
                TextButton(onClick = { model.refreshEvents(quiet = false) }) { Text("Check for updates") }
                TextButton(onClick = { showFixed = !showFixed }) { Text(if (showFixed) "Hide list" else "Show list") }
            }
            if (showFixed) for (ev in s.shared.cycle) {
                val sub = "${ev.len} day(s) every ${ev.every}" + (if (ev.startTime.isNotBlank()) ", starts ${ev.startTime}" else "")
                SwitchRow(withEmoji(ev.emoji, ev.name), ev.on, sub) { on ->
                    model.update("event ${ev.id} on=$on") { st -> st.copy(shared = st.shared.copy(cycle = st.shared.cycle.map { if (it.id == ev.id) it.copy(on = on) else it })) }
                }
            }
        }

        SectionCard("Notifications on this phone", key = "set-notif") {
            Text(if (model.notificationsAllowed) "Allowed" else "Not allowed — reminders will not show",
                color = if (model.notificationsAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = { openNotificationSettings(ctx) }) { Text("Notification settings") }
                TextButton(onClick = { Notifier.test(ctx) }) { Text("Send a test") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Hint("Xiaomi/HyperOS and some other phones stop apps in the background. Turn on Autostart and set Battery saver to \"No restrictions\" for this app.",
                Modifier.padding(top = 6.dp))
            Row {
                TextButton(onClick = {
                    startSafely(ctx,
                        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                        appDetails(ctx))
                }) { Text("Autostart") }
                TextButton(onClick = { startSafely(ctx, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(ctx)) }) { Text("Battery") }
                TextButton(onClick = { startSafely(ctx, appDetails(ctx)) }) { Text("App info") }
            }
            val next = Store.scheduledFor(ctx)
            if (next > 0) Hint("Next check: ${dayLabel(dayOf(next))} ${fmtBoth(next, zone)}")
        }

        SectionCard("Backup and import", key = "set-backup") {
            Hint("Export saves characters, alliances, kingdoms, times, picks and settings. Import also reads a KSDailyAgenda backup (\"Download settings\" or \"Show everything\"): its alliances and kingdoms are added, the rest stays.")
            Row {
                TextButton(onClick = { exporter.launch("kscompanion-${diso(dayOf(model.now))}.json") }) { Text("Export") }
                TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import file") }
                TextButton(onClick = { dialog = "paste" }) { Text("Paste") }
            }
        }

        SectionCard("Reset", key = "set-reset", defaultOpen = false) {
            ValueRow("Reset event formats", "Reset",
                "Global events exactly as published; each kingdom's events back to their default length, period and switches. Dates, alliances and characters stay.") { dialog = "reset" }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow("Erase all data", "Erase", "Characters, alliances, kingdoms, picks and settings. Export first if you may want them back.") { dialog = "erase" }
        }

        SectionCard("Log", key = "set-log", defaultOpen = false) {
            Row {
                TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide log" else "Show log") }
                if (showLog) TextButton(onClick = { Log.clear(); showLog = false }) { Text("Clear") }
            }
            if (showLog) SelectionContainer {
                Text(Log.read().take(200).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Hint("Events file: ${st.eventsUrl}")
        }

        val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { null } }
        Text("KSCompanion v${version ?: "?"}\nMade by Pingu 🐧 (#1130/#2116)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp))
    }

    val d = dialog
    when {
        d == "leads" -> TextDialog("Default reminders", fmtLeads(st.defaultLeads), "Before the start, separated by commas: 12h, 2h, 15m. A plain number is hours.", { dialog = null }) { text ->
            val v = parseLeads(text)
            if (v == null) model.toast("Not understood: use e.g. 12h, 2h, 15m") else setSettings("default leads ${fmtLeads(v)}") { it.copy(defaultLeads = v) }
            dialog = null
        }
        d != null && d.startsWith("lead:") -> {
            val key = d.removePrefix("lead:")
            TextDialog(leadKeys(s).firstOrNull { it.first == key }?.second ?: key, st.leads[key]?.let { fmtLeads(it) } ?: "",
                "Empty = default (${fmtLeads(st.defaultLeads)}). 0m = when it starts.", { dialog = null }) { text ->
                if (text.isBlank()) setSettings("leads $key default") { it.copy(leads = it.leads - key) }
                else parseLeads(text)?.let { v -> setSettings("leads $key ${fmtLeads(v)}") { it.copy(leads = it.leads + (key to v)) } } ?: model.toast("Not understood: use e.g. 12h, 2h, 15m")
                dialog = null
            }
        }
        d == "prompt" || d == "digest" || d == "solo" -> {
            val cur = when (d) { "prompt" -> st.promptMin; "solo" -> st.soloPromptMin; else -> st.digestMin }
            TextDialog(if (d == "digest") "Summary at (UTC)" else "Ask at (UTC)", timeString(cur), "Hour in UTC, e.g. 12 or 12:30.", { dialog = null }) { text ->
                val m = parseTime(text)?.takeIf { it < 24 * 60 }
                if (m == null) model.toast("Type an hour, e.g. 12:30")
                else setSettings("$d time ${timeString(m)}") {
                    when (d) { "prompt" -> it.copy(promptMin = m); "solo" -> it.copy(soloPromptMin = m); else -> it.copy(digestMin = m) }
                }
                dialog = null
            }
        }
        d == "arena" -> TextDialog("Arena: before the reset", fmtLead(st.arenaLead), "e.g. 15m, 30m or 1h (reset at 00:00 UTC).", { dialog = null }) { text ->
            val v = parseLeads(text)?.singleOrNull()?.takeIf { it in 1..720 }
            if (v == null) model.toast("Type one time, e.g. 15m") else setSettings("arena lead ${fmtLead(v)}") { it.copy(arenaLead = v) }
            dialog = null
        }
        d == "paste" -> PasteDialog({ dialog = null }) { importText(it); dialog = null }
        d == "reset" -> ConfirmDialog("Reset event formats?", "Global events are replaced by the published list; kingdom events get their default format. Dates are kept.", "Reset",
            onConfirm = { model.resetFormats(); dialog = null }, onClose = { dialog = null })
        d == "erase" -> ConfirmDialog("Erase all data?", "Everything in the app is removed. This cannot be undone.", "Erase",
            onConfirm = { model.eraseAll(); dialog = null }, onClose = { dialog = null })
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PasteDialog(onClose: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Paste a backup") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp))
                Hint("From KSDailyAgenda: Backup and reset → \"Show everything\" or \"Show this alliance\", then copy.")
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onImport(text) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
