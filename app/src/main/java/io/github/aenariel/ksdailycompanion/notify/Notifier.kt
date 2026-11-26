package io.github.aenariel.ksdailycompanion.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.aenariel.ksdailycompanion.R
import io.github.aenariel.ksdailycompanion.data.Log
import io.github.aenariel.ksdailycompanion.model.*
import io.github.aenariel.ksdailycompanion.ui.MainActivity
import java.time.ZoneId

object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_LEGION = "legion"
    const val CH_DIGEST = "digest"
    const val EXTRA_CHAR = "char"
    const val EXTRA_OCC = "occ"
    const val EXTRA_ITEM = "item"
    const val EXTRA_NOTIF = "notif"
    const val ACTION_PICK = "io.github.aenariel.ksdailycompanion.PICK"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Event reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Before events start (Bear Traps, legions, Castle Battle…)" })
        nm.createNotificationChannel(NotificationChannel(CH_LEGION, "Legion registration", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Asks which legion each character is in" })
        nm.createNotificationChannel(NotificationChannel(CH_DIGEST, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Today's events, once a day" })
    }

    fun allowed(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun builder(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentIntent(openApp(ctx))
        .setAutoCancel(true)

    @SuppressLint("MissingPermission")   // checked by allowed()
    private fun show(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (!allowed(ctx)) { Log.w("Notifications are not allowed: skipped #$id"); return }
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (e: SecurityException) { Log.e("notify #$id refused", e) }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    fun post(ctx: Context, s: CompanionState, due: List<Due>, now: Long) {
        val zone = ZoneId.systemDefault()
        for (d in due) when (d) {
            is Due.Reminder -> reminder(ctx, d, now, zone)
            is Due.Prompt -> prompt(ctx, d, zone, s)
            is Due.Digest -> digest(ctx, s, d, zone)
        }
    }

    private fun reminder(ctx: Context, d: Due.Reminder, now: Long, zone: ZoneId) {
        val o = d.occ
        if (o.atMs < now - 5 * MIN_MS) { Log.i("Skip reminder for ${o.title}: already started"); return }
        val left = ((o.atMs - now + 30_000) / MIN_MS).toInt()
        val title = listOf(o.emoji.trim(), o.title, fmtIn(left)).filter { it.isNotEmpty() }.joinToString(" ")
        val text = d.charNames.joinToString(", ") + " · " + fmtBoth(o.atMs, zone)
        Log.i("Notify reminder: $title — $text (lead ${fmtLead(d.lead)})")
        show(ctx, d.id.hashCode(), builder(ctx, CH_REMINDERS).setContentTitle(title).setContentText(text)
            .setWhen(o.atMs).setShowWhen(true).setCategory(NotificationCompat.CATEGORY_REMINDER))
    }

    /** "Swordland (Legion 2)" -> "Legion 2". */
    fun shortLabel(label: String): String = Regex("\\(([^)]*)\\)").find(label)?.groupValues?.get(1) ?: label

    private fun pickIntent(ctx: Context, charId: String, occ: String, item: String, notifId: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, "$charId|$occ|$item".hashCode(),
        Intent(ctx, PickReceiver::class.java).setAction(ACTION_PICK)
            .putExtra(EXTRA_CHAR, charId).putExtra(EXTRA_OCC, occ).putExtra(EXTRA_ITEM, item).putExtra(EXTRA_NOTIF, notifId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prompt(ctx: Context, d: Due.Prompt, zone: ZoneId, s: CompanionState) {
        val notifId = "p|${d.charId}|${d.occKey}".hashCode()
        val usual = d.usual?.let { u -> if (u == NONE) "not playing" else d.options.firstOrNull { it.id == u }?.let { shortLabel(it.label) } }
        val times = d.options.joinToString("\n") { o ->
            val m = parseTime(o.time)
            shortLabel(o.label) + ": " + (m?.let { fmtBoth(battleMs(s, d, o) ?: (it * MIN_MS), zone) } ?: o.time)
        }
        val title = "${d.options.first().emoji.trim()} ${d.group.group}: which legion?".trim()
        val text = "${d.charName} — registration is open." + (usual?.let { " Usual: $it." } ?: "")
        val b = builder(ctx, CH_LEGION).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$times" + if (d.options.size > 2) "\nMore legions: open the app." else ""))
        for (o in d.options.take(2)) b.addAction(0, shortLabel(o.label), pickIntent(ctx, d.charId, d.occKey, o.id, notifId))
        b.addAction(0, "Not playing", pickIntent(ctx, d.charId, d.occKey, NONE, notifId))
        Log.i("Notify legion question: ${d.charName} ${d.occKey}, ${d.options.size} options")
        show(ctx, notifId, b)
    }

    /** When an option's battle starts for this occurrence (first day the item's rule hits within the event). */
    private fun battleMs(s: CompanionState, d: Due.Prompt, o: TimedItem): Long? {
        val ch = s.characters.firstOrNull { it.id == d.charId } ?: return null
        val al = s.alliances[ch.alliance] ?: return null
        val v = viewFor(s, al.kingdom)
        val start = dnum(d.occKey.substringAfterLast('@')) ?: return null
        val ev = v.event(d.group.ev) ?: return null
        val m = parseTime(o.time) ?: return null
        for (n in start until start + maxOf(1, ev.len)) if (ruleHits(v, o.rule, n)) return n * DAY_MS + m * MIN_MS
        return null
    }

    fun confirmPick(ctx: Context, s: CompanionState, charId: String, occ: String, item: String, notifId: Int) {
        val ch = s.characters.firstOrNull { it.id == charId } ?: return
        val group = occ.substringBeforeLast('@')
        val text = if (item == NONE) "${ch.name}: not playing $group" else {
            val o = s.alliances[ch.alliance]?.timed?.firstOrNull { it.id == item }
            val cg = s.choiceGroups.firstOrNull { it.group == group }
            val at = if (o != null && cg != null) battleMs(s, Due.Prompt("", 0, charId, ch.name, cg, occ, listOf(o), null), o) else null
            "${ch.name}: ${o?.label ?: item}" + (at?.let { " · ${dayLabel(dayOf(it))} ${fmtBoth(it, ZoneId.systemDefault())}" } ?: "")
        }
        show(ctx, notifId, builder(ctx, CH_LEGION).setContentTitle("Saved").setContentText(text).setSilent(true).setTimeoutAfter(15_000))
    }

    private fun digest(ctx: Context, s: CompanionState, d: Due.Digest, zone: ZoneId) {
        val lines = ArrayList<String>()
        val kingdoms = s.characters.filter { it.on }.mapNotNull { s.alliances[it.alliance]?.kingdom }.distinct()
        for (k in kingdoms) {
            val ev = dayEvents(viewFor(s, k), d.day).map { dayEventLabel(it) }
            if (ev.isNotEmpty()) lines += (if (kingdoms.size > 1) "$k: " else "") + ev.joinToString(", ")
        }
        val occ = occurrences(s, d.day, d.day)
        val merged = LinkedHashMap<String, MutableList<String>>()
        for (o in occ) {
            val label = when (o.kind) {
                Kind.TIMED -> "${fmtBoth(o.atMs, zone)} · ${o.emoji.trim()} ${o.title}"
                Kind.CHOICE_PENDING -> "${o.emoji.trim()} ${o.title}: legion not set"
                Kind.CHOICE_NONE -> continue
            }
            merged.getOrPut(label) { ArrayList() } += o.charName
        }
        for ((label, names) in merged) lines += "$label (${names.joinToString(", ")})"
        if (lines.isEmpty()) { Log.i("Digest for ${diso(d.day)}: nothing today"); return }
        val title = "Today in Kingshot · ${dayLabel(d.day)}"
        Log.i("Notify digest: ${lines.size} lines")
        show(ctx, d.id.hashCode(), builder(ctx, CH_DIGEST).setContentTitle(title).setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n"))))
    }

    fun test(ctx: Context) {
        val now = System.currentTimeMillis()
        show(ctx, 4242, builder(ctx, CH_REMINDERS).setContentTitle("Test notification")
            .setContentText("Notifications work · ${fmtBoth(now, ZoneId.systemDefault())}"))
        Log.i("Test notification posted (allowed=${allowed(ctx)})")
    }
}
