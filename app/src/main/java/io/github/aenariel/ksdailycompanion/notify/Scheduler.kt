package io.github.aenariel.ksdailycompanion.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aenariel.ksdailycompanion.data.Log
import io.github.aenariel.ksdailycompanion.data.Store
import io.github.aenariel.ksdailycompanion.model.*

/**
 * One alarm at a time: it is set for the next notification. When it fires, everything due since the last
 * firing is posted and the next alarm is set. Alarms are inexact (no special permission); a few minutes late is fine.
 */
object Scheduler {
    const val ACTION_FIRE = "io.github.aenariel.ksdailycompanion.FIRE"
    /** Never replay more than this after the phone was off or the app was stopped. */
    private const val REPLAY_MS = 6 * 3_600_000L

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Lower bound for what is still due: the last firing, but not older than [REPLAY_MS]. */
    fun dueFrom(ctx: Context, now: Long): Long {
        var after = Store.lastFired(ctx)
        if (after == 0L || after > now) { after = now; Store.setLastFired(ctx, now) }
        return maxOf(after, now - REPLAY_MS)
    }

    fun replan(ctx: Context, reason: String) {
        val t0 = System.currentTimeMillis()
        try {
            val s = Store.load(ctx)
            val now = System.currentTimeMillis()
            val after = dueFrom(ctx, now)
            val next = nextFire(s, after)
            val at = next ?: (now + DAY_MS)   // nothing in the next 8 days: look again tomorrow
            val am = ctx.getSystemService(AlarmManager::class.java)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            Store.setScheduledFor(ctx, at)
            Log.i("Replan ($reason): next alarm ${fmtUtc(at)} ${dayLabel(dayOf(at))}${if (next == null) " (nothing due, re-check)" else ""} (${System.currentTimeMillis() - t0} ms)")
        } catch (e: Exception) {
            Log.e("Replan ($reason) failed", e)
        }
    }

    /** Posts everything due now and sets the next alarm. */
    fun fire(ctx: Context, reason: String) {
        val s = Store.load(ctx)
        val now = System.currentTimeMillis()
        val after = dueFrom(ctx, now)
        val due = dueBetween(s, after, now)
        val late = Store.scheduledFor(ctx).let { if (it in 1..now) (now - it) / 1000 else 0 }
        Log.i("Fire ($reason): ${due.size} due between ${fmtUtc(after)} and ${fmtUtc(now)}, alarm ${late}s late")
        Notifier.post(ctx, s, due, now)
        Store.setLastFired(ctx, now)
        replan(ctx, "after fire")
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Log.init(ctx)
        // Network for the events check: off the main thread, keeping the broadcast alive until done
        val pending = goAsync()
        Thread {
            try {
                Scheduler.fire(ctx, intent.action ?: "alarm")
                Store.syncEventsIfDue(ctx, "alarm")
            } catch (e: Exception) {
                Log.e("Alarm handling failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Boot, app update, time or time-zone change: set the alarm again. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Log.init(ctx)
        Scheduler.fire(ctx, intent.action?.substringAfterLast('.') ?: "system")
    }
}

/** "Legion 1 / Legion 2 / Not playing" buttons on the registration notification. */
class PickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Log.init(ctx)
        val charId = intent.getStringExtra(Notifier.EXTRA_CHAR) ?: return
        val key = intent.getStringExtra(Notifier.EXTRA_OCC) ?: return
        val item = intent.getStringExtra(Notifier.EXTRA_ITEM) ?: return
        val notifId = intent.getIntExtra(Notifier.EXTRA_NOTIF, 0)
        val s = Store.load(ctx)
        val picks = s.picks.toMutableMap()
        picks[charId] = (picks[charId] ?: emptyMap()) + (key to item)
        Store.save(ctx, s.copy(picks = picks))
        Log.i("Pick from notification: ${s.characters.firstOrNull { it.id == charId }?.name ?: charId} $key -> $item")
        Notifier.cancel(ctx, notifId)
        Scheduler.replan(ctx, "pick")
        Notifier.confirmPick(ctx, Store.load(ctx), charId, key, item, notifId)
    }
}
