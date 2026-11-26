package io.github.aenariel.ksdailycompanion.data

import android.content.Context
import io.github.aenariel.ksdailycompanion.model.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Keeps the whole state in one JSON file (files/state.json). */
object Store {
    private const val FILE = "state.json"
    private const val PREFS = "runtime"
    private var cached: CompanionState? = null

    @Synchronized fun load(ctx: Context): CompanionState {
        cached?.let { return it }
        Log.init(ctx)
        val t0 = System.currentTimeMillis()
        val (bundledVer, bundled) = bundledShared(ctx)
        val f = File(ctx.filesDir, FILE)
        var s = if (f.exists()) {
            try { Codec.state(JSONObject(f.readText()), bundled) } catch (e: Exception) {
                Log.e("state.json unreadable, keeping a copy as state.bad.json and starting fresh", e)
                f.copyTo(File(ctx.filesDir, "state.bad.json"), overwrite = true)
                freshState(bundled, bundledVer)
            }
        } else {
            Log.i("No saved state: starting fresh with bundled events v$bundledVer")
            freshState(bundled, bundledVer)
        }
        if (bundledVer > s.sharedVersion) {
            Log.i("Bundled events v$bundledVer newer than saved v${s.sharedVersion}: updating")
            s = s.copy(shared = Codec.mergeShared(s.shared, bundled), sharedVersion = bundledVer)
        }
        Log.i("Loaded state: ${s.characters.size} characters, ${s.alliances.size} alliances, ${s.kingdoms.size} kingdoms, events v${s.sharedVersion} (${System.currentTimeMillis() - t0} ms)")
        cached = s
        return s
    }

    @Synchronized fun save(ctx: Context, s: CompanionState) {
        cached = s
        val f = File(ctx.filesDir, FILE)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        try {
            tmp.writeText(Codec.state(s).toString(1))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            Log.i("Saved state (${f.length()} bytes)")
        } catch (e: Exception) {
            Log.e("Saving state failed", e)
        }
    }

    /** Re-read the file next time (after a receiver changed it in another component). */
    @Synchronized fun invalidate() { cached = null }

    fun bundledShared(ctx: Context): Pair<Int, Shared> {
        val text = ctx.assets.open("events.json").bufferedReader().use { it.readText() }
        return Codec.eventsFile(text) ?: error("bundled events.json is invalid")
    }

    /** Blocking: call off the main thread. Returns the new state if the online events are newer, plus a message. */
    fun fetchEvents(s: CompanionState): Pair<CompanionState?, String> {
        val t0 = System.currentTimeMillis()
        Log.i("Fetching events from ${s.settings.eventsUrl}")
        return try {
            val c = URL(s.settings.eventsUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code != 200) {
                Log.w("Events fetch: HTTP $code"); null to "Events: HTTP $code"
            } else {
                val text = c.inputStream.bufferedReader().use { it.readText() }
                val parsed = Codec.eventsFile(text)
                when {
                    parsed == null -> { Log.w("Events fetch: file not understood"); null to "Events file not understood" }
                    parsed.first <= s.sharedVersion -> {
                        Log.i("Events online v${parsed.first}, have v${s.sharedVersion}: nothing to do (${System.currentTimeMillis() - t0} ms)")
                        null to "Events up to date (v${s.sharedVersion})"
                    }
                    else -> {
                        Log.i("Events updated v${s.sharedVersion} -> v${parsed.first}, ${parsed.second.cycle.size} events (${System.currentTimeMillis() - t0} ms)")
                        s.copy(shared = Codec.mergeShared(s.shared, parsed.second), sharedVersion = parsed.first) to "Events updated to v${parsed.first}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("Events fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            null to "Could not reach the events file (offline?)"
        }
    }

    // Runtime values that are not part of the user's data
    fun lastFired(ctx: Context): Long = ctx.getSharedPreferences(PREFS, 0).getLong("lastFired", 0L)
    fun setLastFired(ctx: Context, ms: Long) { ctx.getSharedPreferences(PREFS, 0).edit().putLong("lastFired", ms).apply() }
    fun scheduledFor(ctx: Context): Long = ctx.getSharedPreferences(PREFS, 0).getLong("scheduledFor", 0L)
    fun setScheduledFor(ctx: Context, ms: Long) { ctx.getSharedPreferences(PREFS, 0).edit().putLong("scheduledFor", ms).apply() }
}
