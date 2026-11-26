package io.github.aenariel.ksdailycompanion.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Logcat (tag KSDC) plus the last lines in a file, shown in Settings > Log. */
object Log {
    private const val TAG = "KSDC"
    private const val KEEP = 400
    private var file: File? = null
    private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.applicationContext.filesDir, "log.txt")
    }

    fun i(msg: String) { android.util.Log.i(TAG, msg); add("I", msg) }
    fun w(msg: String) { android.util.Log.w(TAG, msg); add("W", msg) }
    fun e(msg: String, t: Throwable? = null) {
        android.util.Log.e(TAG, msg, t)
        add("E", if (t == null) msg else "$msg: ${t.javaClass.simpleName}: ${t.message}")
    }

    @Synchronized private fun add(level: String, msg: String) {
        val f = file ?: return
        try {
            val line = "${stamp.format(Instant.now())} UTC $level $msg"
            val lines = if (f.exists()) f.readLines() else emptyList()
            f.writeText((lines.takeLast(KEEP - 1) + line).joinToString("\n") + "\n")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "log file write failed", e)
        }
    }

    @Synchronized fun read(): List<String> = try { file?.takeIf { it.exists() }?.readLines()?.reversed() ?: emptyList() } catch (e: Exception) { emptyList() }

    @Synchronized fun clear() { try { file?.delete() } catch (e: Exception) { } }
}
