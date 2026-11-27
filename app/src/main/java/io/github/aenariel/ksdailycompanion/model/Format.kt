package io.github.aenariel.ksdailycompanion.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ENGLISH)

/** "12h, 2h, 15m, 1h30m" -> minutes. A bare number means hours. Null when nothing valid was typed. */
fun parseLeads(text: String): List<Int>? {
    val out = ArrayList<Int>()
    for (raw in text.split(',', ';', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }) {
        val m = Regex("^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?$").find(raw)
        val v = when {
            raw.all { it.isDigit() } -> raw.toInt() * 60
            m != null && raw.any { it.isLetter() } ->
                (m.groupValues[1].ifEmpty { "0" }.toInt() * 1440) + (m.groupValues[2].ifEmpty { "0" }.toInt() * 60) + m.groupValues[3].ifEmpty { "0" }.toInt()
            else -> return null
        }
        out += v
    }
    return if (out.isEmpty()) null else out.distinct().sortedDescending()
}

fun fmtLead(min: Int): String = when {
    min <= 0 -> "0m"
    min % 1440 == 0 -> "${min / 1440}d"
    min % 60 == 0 -> "${min / 60}h"
    min > 60 -> "${min / 60}h${min % 60}m"
    else -> "${min}m"
}

fun fmtLeads(list: List<Int>): String = list.joinToString(", ") { fmtLead(it) }

/** "in 2h" / "now". */
fun fmtIn(min: Int): String = if (min <= 0) "now" else "in ${fmtLead(min)}"

fun fmtUtc(ms: Long): String = HM.format(Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC)) + " UTC"

/** Local hour, with "+1d" / "-1d" when the local date is not the UTC date. */
fun fmtLocal(ms: Long, zone: ZoneId): String {
    val i = Instant.ofEpochMilli(ms)
    val local = i.atZone(zone)
    val diff = local.toLocalDate().toEpochDay() - i.atOffset(ZoneOffset.UTC).toLocalDate().toEpochDay()
    val tail = when {
        diff > 0 -> " (+${diff}d)"
        diff < 0 -> " (${diff}d)"
        else -> ""
    }
    return HM.format(local) + tail + " local"
}

/** "12:00 UTC · 13:00 local". */
fun fmtBoth(ms: Long, zone: ZoneId): String = fmtUtc(ms) + " · " + fmtLocal(ms, zone)

fun dayLabel(n: Int): String = DAY_LABEL.format(java.time.LocalDate.ofEpochDay(n.toLong()))

fun newId(): String = java.util.UUID.randomUUID().toString().substring(0, 8)

/**
 * Kingshot draws some icons from private-use characters that only the game can show
 * (see keyboarding.wiki/unicode/kingshot). The app shows an emoji instead; the stored text keeps
 * the game character, so text copied to the game is unchanged. Unknown ones are dropped.
 */
val GAME_GLYPHS: Map<Char, String> = mapOf(
    '\uE044' to "⚔️",   // Alliance Brawl
    '\uE041' to "🪓",   // Viking token
    '\uE009' to "🎡",   // Wheel coin (Hero Roulette)
    '\uE010' to "🛡️",   // Truegold (Armament Competition)
    '\uE001' to "💎",   // Gem (Buccaneer Bounty)
)

fun displayEmoji(s: String): String {
    if (s.none { it in '\uE000'..'\uF8FF' }) return s
    val b = StringBuilder()
    for (c in s) if (c in '\uE000'..'\uF8FF') GAME_GLYPHS[c]?.let { b.append(it) } else b.append(c)
    return b.toString().replace(Regex("\\s+"), " ").trim()
}

/** Emoji + name for display, without empty gaps. */
fun withEmoji(emoji: String, text: String): String = listOf(displayEmoji(emoji).trim(), text).filter { it.isNotEmpty() }.joinToString(" ")
