package com.freedomfighter.readersattention

import android.content.Context
import com.freedomfighter.readersattention.data.AppDay
import com.freedomfighter.readersattention.data.Day
import com.freedomfighter.readersattention.data.Session
import com.freedomfighter.readersattention.data.WeekAverage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The strings, behind an interface so the sentences are tested on the JVM from strings.xml. */
interface Res {
    fun s(id: Int, vararg args: Any): String
    fun q(id: Int, n: Int, vararg args: Any): String
    fun a(id: Int): Array<String>
}

class AndroidRes(private val context: Context) : Res {
    override fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    override fun q(id: Int, n: Int, vararg args: Any) = context.resources.getQuantityString(id, n, *args)
    override fun a(id: Int): Array<String> = context.resources.getStringArray(id)
}

/** Everything the screens say, built from a [Day]. Names come from the caller. */
object Words {
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** "1 h 12", "2 h", "42 min", "18 s". */
    fun duration(ms: Long, r: Res): String {
        val s = ms / 1000; val m = s / 60; val h = m / 60
        return when {
            h > 0 && m % 60 > 0 -> r.s(R.string.dur_h_min, h, m % 60)
            h > 0 -> r.s(R.string.dur_h, h)
            m > 0 -> r.s(R.string.dur_min, m)
            else -> r.s(R.string.dur_s, s)
        }
    }

    fun time(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = hm.format(Instant.ofEpochMilli(t).atZone(zone))

    fun minutesWord(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun openings(n: Int, r: Res) = r.q(R.plurals.openings, n, n)

    /** "23 openings · 1 h 12" */
    fun reflexLine(d: Day, r: Res) = r.s(R.string.pair, openings(d.reflexOpenings, r), duration(d.reflexMillis, r))

    /** "screen 3 h 40 · 41 unlocks" */
    fun screenLine(d: Day, r: Res) = r.s(R.string.screen_line, duration(d.screenMillis, r), r.q(R.plurals.unlocks, d.unlocks, d.unlocks))

    // Each sentence has a few phrasings of the same meaning; the day picks one, so the wording
    // shifts and the sentences keep being read. Rarity is kept: never more sentences than these.
    private val firstIds = listOf(R.plurals.first_after_unlock, R.plurals.first_after_unlock_2, R.plurals.first_after_unlock_3)
    private val longestIds = listOf(R.string.longest, R.string.longest_2, R.string.longest_3)
    private val eveningIds = listOf(R.string.evening, R.string.evening_2, R.string.evening_3)
    private val checksIds = listOf(R.plurals.checks, R.plurals.checks_2, R.plurals.checks_3)

    /** The phrasing of the day: a number that changes once a day. */
    fun variant(zone: ZoneId = ZoneId.systemDefault()): Int = LocalDate.now(zone).dayOfYear

    private fun <T> List<T>.pick(variant: Int) = this[Math.floorMod(variant, size)]

    /** The sentences that name the habit, each only when it applies. */
    fun sentences(d: Day, r: Res, name: (String) -> String, eveningMinutes: Int, zone: ZoneId = ZoneId.systemDefault(), variant: Int = variant(zone)): List<String> {
        val out = ArrayList<String>()
        if (d.reflexFirst > 0 && d.unlocks > 0) out += r.q(firstIds.pick(variant), d.reflexFirst, d.reflexFirst, d.unlocks)
        d.longestReflex?.takeIf { it.length >= 5 * 60_000L }?.let { out += r.s(longestIds.pick(variant), name(it.pkg), duration(it.length, r), time(it.start, zone)) }
        if (d.eveningReflexMillis >= 60_000L) out += r.s(eveningIds.pick(variant), minutesWord(eveningMinutes), duration(d.eveningReflexMillis, r))
        d.mostChecked?.takeIf { it.second >= 3 }?.let { (p, n) -> out += r.q(checksIds.pick(variant), n, name(p), n) }
        return out
    }

    /** The pause's question, one phrasing a day. */
    fun whatFor(r: Res, variant: Int = variant()): String = r.a(R.array.what_for).toList().pick(variant)

    /** "14 openings · 42 min · longest 18 min" */
    fun appLine(a: AppDay, r: Res) = r.s(R.string.app_line, openings(a.openings, r), duration(a.millis, r), duration(a.longest?.length ?: 0, r))

    /** "2 h 28 · 9 apps" */
    fun toolsLine(d: Day, r: Res) = r.s(R.string.pair, duration(d.toolsMillis, r), r.q(R.plurals.apps, d.tools.size, d.tools.size))

    /** "22:10 · 18 min" or "22:10 · 18 min · nothing in particular" */
    fun sessionLine(s: Session, answer: String?, r: Res, zone: ZoneId = ZoneId.systemDefault()): String {
        val base = r.s(R.string.pair, time(s.start, zone), duration(s.length, r))
        return if (answer == null) base else r.s(R.string.pair, base, answer)
    }

    /** "today", "yesterday", then the weekday. */
    fun dayName(start: Long, today: LocalDate, r: Res, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val d = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        return when (d) {
            today -> r.s(R.string.today)
            today.minusDays(1) -> r.s(R.string.yesterday)
            else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
        }
    }

    /** "today · 23 openings · 1 h 12" */
    fun weekRow(d: Day, today: LocalDate, r: Res, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()) =
        r.s(R.string.pair, dayName(d.start, today, r, zone, locale), reflexLine(d, r))

    /** "last week: 31 a day · 1 h 40" */
    fun weekAverage(a: WeekAverage, r: Res): String? =
        if (a.days == 0) null else r.s(R.string.week_avg, a.openings, duration(a.millis, r))

    /** "today: 14 openings · 42 min" */
    fun pauseToday(a: AppDay?, r: Res) = r.s(R.string.pause_today, openings(a?.openings ?: 0, r), duration(a?.millis ?: 0, r))

    /** "20 minutes in instagram" */
    fun minutesIn(minutes: Int, appName: String, r: Res) = r.s(R.string.minutes_in, minutes, appName)

    val answerIds = listOf(R.string.answer_1, R.string.answer_2, R.string.answer_3, R.string.answer_4)
}
