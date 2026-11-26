package io.github.aenariel.ksdailycompanion.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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

/** Keys that can have their own reminder times, with a readable name. */
private fun leadKeys(s: CompanionState): List<Pair<String, String>> {
    val out = LinkedHashMap<String, String>()
    val choice = s.choiceGroups.map { it.group }.toSet()
    for (g in s.choiceGroups) out["g:${g.group}"] = g.group
    for (a in s.alliances.values) for (t in a.timed) if (t.group !in choice) out.putIfAbsent("t:${t.id}", "${t.emoji.trim()} ${t.label}".trim())
    for (k in s.kingdoms.keys.ifEmpty { setOf("") }) for (ev in viewFor(s, k).cycle) if (ev.startTime.isNotBlank()) out.putIfAbsent("c:${ev.id}", "${ev.emoji.trim()} ${ev.name}".trim())
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
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))

        SectionTitle("Reminders")
        SettingRow("Default: before each event", fmtLeads(st.defaultLeads)) { dialog = "leads" }
        Hint("Per event (tap to change; empty = default):")
        for ((key, label) in leadKeys(s)) {
            val own = st.leads[key]
            SettingRow(label, own?.let { fmtLeads(it) } ?: "default") { dialog = "lead:$key" }
        }

        SectionTitle("Legion registration")
        SettingRow("Ask which legion at", timePreview(timeString(st.promptMin), model.now, zone)) { dialog = "prompt" }
        Hint("On registration days, for each character without a pick for that week.")

        SectionTitle("Daily summary")
        SwitchRow("Send a summary of the day", st.digestOn) { on -> setSettings("digest on=$on") { it.copy(digestOn = on) } }
        if (st.digestOn) SettingRow("At", timePreview(timeString(st.digestMin), model.now, zone)) { dialog = "digest" }

        SectionTitle("Notifications on this phone")
        Text(if (model.notificationsAllowed) "Allowed" else "Not allowed — reminders will not show")
        Row {
            TextButton(onClick = { openNotificationSettings(ctx) }) { Text("Notification settings") }
            TextButton(onClick = { Notifier.test(ctx) }) { Text("Send a test") }
        }
        Hint("Xiaomi/HyperOS and some other phones stop apps in the background. Turn on Autostart and set Battery saver to \"No restrictions\" for this app.")
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

        SectionTitle("Backup and import")
        Hint("Export saves characters, alliances, times, picks and settings. Import also reads a KSDailyAgenda backup (\"Download settings\" or \"Show everything\"): its alliances and kingdoms are added, the rest stays.")
        Row {
            TextButton(onClick = { exporter.launch("ks-companion-${diso(dayOf(model.now))}.json") }) { Text("Export") }
            TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import file") }
            TextButton(onClick = { dialog = "paste" }) { Text("Paste") }
        }

        SectionTitle("Log")
        Row {
            TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide log" else "Show log") }
            if (showLog) TextButton(onClick = { Log.clear(); showLog = false }) { Text("Clear") }
        }
        if (showLog) SelectionContainer {
            Text(Log.read().take(200).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Hint("Events file: ${st.eventsUrl}")
        Spacer(Modifier.height(24.dp))
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
        d == "prompt" || d == "digest" -> {
            val cur = if (d == "prompt") st.promptMin else st.digestMin
            TextDialog(if (d == "prompt") "Ask at (UTC)" else "Summary at (UTC)", timeString(cur), "Hour in UTC, e.g. 12 or 12:30.", { dialog = null }) { text ->
                val m = parseTime(text)?.takeIf { it < 24 * 60 }
                if (m == null) model.toast("Type an hour, e.g. 12:30")
                else setSettings("$d time ${timeString(m)}") { if (d == "prompt") it.copy(promptMin = m) else it.copy(digestMin = m) }
                dialog = null
            }
        }
        d == "paste" -> PasteDialog({ dialog = null }) { importText(it); dialog = null }
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
