package com.freedomfighter.readersattention.data

/**
 * Pure Kotlin: from the system's usage events to the day in words. Nothing here touches
 * Android, so the whole chain is tested from synthetic event lists.
 */

enum class Kind { RESUMED, PAUSED, STOPPED, UNLOCKED, SCREEN_ON, SCREEN_OFF, BOOT }

/** One usage event. [pkg] and [cls] are empty for the screen and keyguard events. */
data class Ev(val t: Long, val kind: Kind, val pkg: String = "", val cls: String = "")

/** One stretch of an app in front. */
data class Session(val pkg: String, val start: Long, val end: Long) {
    val length: Long get() = end - start
}

/** One app's day. */
data class AppDay(val pkg: String, val sessions: List<Session>) {
    val openings: Int get() = sessions.size
    val millis: Long get() = sessions.sumOf { it.length }
    val longest: Session? get() = sessions.maxByOrNull { it.length }
    /** The compulsive checks: openings shorter than a minute. */
    val checks: Int get() = sessions.count { it.length < Sessions.CHECK_MS }
}

/** One day, summarised. Apps are sorted by time spent, the longest first. */
data class Day(
    val start: Long,
    val end: Long,
    val reflex: List<AppDay>,
    val tools: List<AppDay>,
    val screenMillis: Long,
    val unlocks: Int,
    /** Unlocks after which a reflex app was the first in front. */
    val reflexFirst: Int,
    /** Reflex time after the evening hour. */
    val eveningReflexMillis: Long
) {
    val reflexOpenings: Int get() = reflex.sumOf { it.openings }
    val reflexMillis: Long get() = reflex.sumOf { it.millis }
    val toolsMillis: Long get() = tools.sumOf { it.millis }
    val longestReflex: Session? get() = reflex.mapNotNull { it.longest }.maxByOrNull { it.length }
    /** The app checked most often for less than a minute, with the count. */
    val mostChecked: Pair<String, Int>? get() = reflex.map { it.pkg to it.checks }.filter { it.second > 0 }.maxByOrNull { it.second }
    /** No app opened: the day does not count in the averages. */
    val empty: Boolean get() = reflex.isEmpty() && tools.isEmpty()
}

/** Averages over the previous days, for the week screen. */
data class WeekAverage(val openings: Int, val millis: Long, val days: Int)

object Sessions {
    const val CHECK_MS = 60_000L
    /** Two stretches of the same app closer than this are one session (activity changes inside the app). */
    const val MERGE_GAP_MS = 1_500L
    /** The app in front within this time after an unlock is the first one. */
    const val AFTER_UNLOCK_MS = 60_000L

    /**
     * Sessions from the raw events, over the whole range. Each activity's RESUMED opens a
     * stretch that its PAUSED or STOPPED closes; another package's RESUMED closes it too (one
     * app in front at a time: a PAUSED the system lost, after a crash or a shutdown, would
     * otherwise run for days), the screen going off or the device stopping or starting closes
     * everything, and so does [to] for whatever is still open. Overlapping or near stretches
     * of one package are merged, then everything is clipped to [from, to].
     */
    fun sessions(events: List<Ev>, from: Long, to: Long): List<Session> {
        val open = LinkedHashMap<String, Pair<String, Long>>() // pkg|cls -> (pkg, start)
        val raw = ArrayList<Session>()
        for (e in events.sortedBy { it.t }) {
            when (e.kind) {
                Kind.RESUMED -> if (e.pkg.isNotEmpty()) {
                    val others = open.filterValues { it.first != e.pkg }
                    others.forEach { (k, v) -> open.remove(k); raw += Session(v.first, v.second, e.t) }
                    open.putIfAbsent(e.pkg + "|" + e.cls, e.pkg to e.t)
                }
                Kind.PAUSED, Kind.STOPPED -> open.remove(e.pkg + "|" + e.cls)?.let { (p, s) -> raw += Session(p, s, e.t) }
                Kind.SCREEN_OFF, Kind.BOOT -> { open.values.forEach { (p, s) -> raw += Session(p, s, e.t) }; open.clear() }
                else -> {}
            }
        }
        open.values.forEach { (p, s) -> raw += Session(p, s, to) }
        val merged = ArrayList<Session>()
        for ((_, list) in raw.groupBy { it.pkg }) {
            var cur: Session? = null
            for (s in list.sortedBy { it.start }) {
                val c = cur
                cur = if (c == null) s
                else if (s.start - c.end <= MERGE_GAP_MS) Session(c.pkg, c.start, maxOf(c.end, s.end))
                else { merged += c; s }
            }
            cur?.let { merged += it }
        }
        return merged.mapNotNull { clip(it, from, to) }.sortedBy { it.start }
    }

    fun clip(s: Session, from: Long, to: Long): Session? {
        val a = maxOf(s.start, from); val b = minOf(s.end, to)
        return if (b > a) Session(s.pkg, a, b) else null
    }

    /** The time the screen was in use: the union of every session. */
    fun union(sessions: List<Session>): Long {
        var total = 0L; var curStart = Long.MIN_VALUE; var curEnd = Long.MIN_VALUE
        for (s in sessions.sortedBy { it.start }) {
            if (s.start > curEnd) { if (curEnd > curStart) total += curEnd - curStart; curStart = s.start; curEnd = s.end }
            else if (s.end > curEnd) curEnd = s.end
        }
        if (curEnd > curStart) total += curEnd - curStart
        return total
    }

    /**
     * One day from the sessions of the whole range and the events. [excluded] packages count
     * for the screen time and nothing else; [isReflex] tells the two classes apart.
     */
    fun day(
        all: List<Session>,
        events: List<Ev>,
        start: Long,
        end: Long,
        eveningMinutes: Int,
        excluded: Set<String>,
        isReflex: (String) -> Boolean
    ): Day {
        val inDay = all.mapNotNull { clip(it, start, end) }
        val kept = inDay.filter { it.pkg !in excluded }
        val byPkg = kept.groupBy { it.pkg }.map { (p, l) -> AppDay(p, l.sortedBy { it.start }) }.sortedByDescending { it.millis }
        val (reflex, tools) = byPkg.partition { isReflex(it.pkg) }
        val dayEvents = events.filter { it.t >= start && it.t < end }
        var unlockTimes = dayEvents.filter { it.kind == Kind.UNLOCKED }.map { it.t }
        if (unlockTimes.isEmpty()) unlockTimes = dayEvents.filter { it.kind == Kind.SCREEN_ON }.map { it.t }
        val reflexFirst = unlockTimes.count { tu ->
            kept.filter { it.end > tu && it.start < tu + AFTER_UNLOCK_MS }.minByOrNull { it.start }?.let { isReflex(it.pkg) } ?: false
        }
        val evening = start + eveningMinutes * 60_000L
        val eveningMs = reflex.sumOf { a -> a.sessions.sumOf { s -> maxOf(0L, s.end - maxOf(s.start, evening)) } }
        return Day(start, end, reflex, tools, union(inDay), unlockTimes.size, reflexFirst, eveningMs)
    }

    /** The days in order, from the given (start, end) bounds; the sessions are built once. */
    fun days(events: List<Ev>, bounds: List<Pair<Long, Long>>, eveningMinutes: Int, excluded: Set<String>, isReflex: (String) -> Boolean): List<Day> {
        if (bounds.isEmpty()) return emptyList()
        val from = bounds.minOf { it.first }; val to = bounds.maxOf { it.second }
        val all = sessions(events, from, to)
        return bounds.map { (s, e) -> day(all, events, s, e, eveningMinutes, excluded, isReflex) }
    }

    /** Average openings and reflex time of the days that had any activity. */
    fun average(days: List<Day>): WeekAverage {
        val used = days.filter { !it.empty }
        if (used.isEmpty()) return WeekAverage(0, 0, 0)
        return WeekAverage(
            Math.round(used.sumOf { it.reflexOpenings }.toDouble() / used.size).toInt(),
            used.sumOf { it.reflexMillis } / used.size,
            used.size
        )
    }
}
