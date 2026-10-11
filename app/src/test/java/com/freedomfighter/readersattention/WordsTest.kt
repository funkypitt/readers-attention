package com.freedomfighter.readersattention

import com.freedomfighter.readersattention.data.AppDay
import com.freedomfighter.readersattention.data.Day
import com.freedomfighter.readersattention.data.Session
import com.freedomfighter.readersattention.data.WeekAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The English sentences, from the real strings.xml: the resource ids are mapped back to their
 * names through the generated R class, and the templates formatted as Android would.
 */
class WordsTest {
    private val xml: Res = object : Res {
        private val strings = HashMap<String, String>()
        private val plurals = HashMap<String, Map<String, String>>()
        private val arrays = HashMap<String, List<String>>()
        init {
            val f = listOf("app/src/main/res/values/strings.xml", "src/main/res/values/strings.xml").map { File(it) }.first { it.exists() }
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i) as? Element ?: continue
                when (n.tagName) {
                    "string" -> strings[n.getAttribute("name")] = clean(n.textContent)
                    "string-array" -> {
                        val items = n.getElementsByTagName("item")
                        arrays[n.getAttribute("name")] = (0 until items.length).map { clean((items.item(it) as Element).textContent) }
                    }
                    "plurals" -> {
                        val items = n.getElementsByTagName("item")
                        plurals[n.getAttribute("name")] = (0 until items.length).map { items.item(it) as Element }.associate { it.getAttribute("quantity") to clean(it.textContent) }
                    }
                }
            }
        }
        private fun clean(s: String) = s.trim().replace("\\'", "'").replace("\\\"", "\"")
        private fun name(cls: Class<*>, id: Int) = cls.fields.first { it.getInt(null) == id }.name
        override fun s(id: Int, vararg args: Any) = String.format(Locale.US, strings.getValue(name(R.string::class.java, id)), *args)
        override fun a(id: Int): Array<String> = arrays.getValue(name(R.array::class.java, id)).toTypedArray()
        override fun q(id: Int, n: Int, vararg args: Any): String {
            val p = plurals.getValue(name(R.plurals::class.java, id))
            return String.format(Locale.US, (if (n == 1) p["one"] else null) ?: p.getValue("other"), *args)
        }
    }

    private val utc: ZoneId = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 10, 11).atStartOfDay(utc).toInstant().toEpochMilli()
    private val h = 3600_000L
    private val m = 60_000L
    private fun at(hh: Int, mm: Int) = day0 + hh * h + mm * m
    private val names = mapOf("com.instagram.android" to "instagram", "com.android.chrome" to "chrome")

    private fun day(reflex: List<AppDay> = emptyList(), tools: List<AppDay> = emptyList(), screen: Long = 0, unlocks: Int = 0, first: Int = 0, evening: Long = 0) =
        Day(day0, day0 + 24 * h, reflex, tools, screen, unlocks, first, evening)

    @Test fun durations() {
        assertEquals("1 h 12", Words.duration(72 * m, xml))
        assertEquals("2 h", Words.duration(120 * m, xml))
        assertEquals("1 h 05", Words.duration(65 * m, xml))
        assertEquals("42 min", Words.duration(42 * m + 30_000, xml))
        assertEquals("18 s", Words.duration(18_400, xml))
        assertEquals("0 s", Words.duration(0, xml))
    }

    @Test fun `the reflex and screen lines`() {
        val insta = AppDay("com.instagram.android", (0 until 22).map { Session("com.instagram.android", at(8 + it % 12, it), at(8 + it % 12, it) + 3 * m) } + Session("com.instagram.android", at(7, 0), at(7, 6)))
        val d = day(reflex = listOf(insta), screen = 220 * m, unlocks = 41)
        assertEquals("23 openings · 1 h 12", Words.reflexLine(d, xml))
        assertEquals("screen 3 h 40 · 41 unlocks", Words.screenLine(d, xml))
        assertEquals("1 opening · 5 min", Words.reflexLine(day(reflex = listOf(AppDay("x", listOf(Session("x", at(1, 0), at(1, 5)))))), xml))
        assertEquals("screen 0 s · 1 unlock", Words.screenLine(day(unlocks = 1), xml))
    }

    @Test fun `the sentences, each when it applies`() {
        val sessions = (0 until 14).map { Session("com.instagram.android", at(9 + it % 10, it), at(9 + it % 10, it) + 30_000) } +
            Session("com.instagram.android", at(22, 10), at(22, 44))
        val d = day(reflex = listOf(AppDay("com.instagram.android", sessions)), screen = 220 * m, unlocks = 41, first = 9, evening = 48 * m)
        val s = Words.sentences(d, xml, { names.getValue(it) }, 22 * 60, utc, variant = 0)
        assertEquals(listOf(
            "after unlocking, a reflex app came first 9 times out of 41",
            "longest stretch: instagram, 34 min, from 22:10",
            "after 22:00: 48 min of reflex apps",
            "checked instagram 14 times for less than a minute"
        ), s)
        // the singular, and the hour as set
        val one = day(reflex = listOf(AppDay("com.instagram.android", listOf(Session("com.instagram.android", at(21, 0), at(21, 6))))), unlocks = 3, first = 1, evening = 5 * m)
        assertEquals(listOf(
            "after unlocking, a reflex app came first once out of 3",
            "longest stretch: instagram, 6 min, from 21:00",
            "after 20:30: 5 min of reflex apps"
        ), Words.sentences(one, xml, { names.getValue(it) }, 20 * 60 + 30, utc, variant = 0))
    }

    @Test fun `nothing is said when nothing applies`() {
        val quick = day(reflex = listOf(AppDay("com.instagram.android", listOf(Session("com.instagram.android", at(9, 0), at(9, 2))))), unlocks = 5)
        assertTrue(Words.sentences(quick, xml, { names.getValue(it) }, 22 * 60, utc, variant = 1).isEmpty())
    }

    @Test fun `app rows, the tools row, the day's openings`() {
        val a = AppDay("com.instagram.android", listOf(Session("com.instagram.android", at(22, 10), at(22, 28)), Session("com.instagram.android", at(8, 0), at(8, 0) + 24 * m)))
        assertEquals("2 openings · 42 min · longest 24 min", Words.appLine(a, xml))
        val tools = (1..9).map { AppDay("t$it", listOf(Session("t$it", at(1, 0), at(1, 0) + 148 * m / 9))) }
        assertEquals("2 h 27 · 9 apps", Words.toolsLine(day(tools = tools), xml))
        assertEquals("22:10 · 18 min", Words.sessionLine(a.sessions[0], null, xml, utc))
        assertEquals("22:10 · 18 min · nothing in particular", Words.sessionLine(a.sessions[0], xml.s(R.string.answer_4), xml, utc))
    }

    @Test fun `the week`() {
        val today = LocalDate.of(2026, 10, 11)
        val d = day(reflex = listOf(AppDay("x", (0 until 22).map { Session("x", at(10, it), at(10, it) + 3 * m) } + Session("x", at(7, 0), at(7, 6)))))
        assertEquals("today · 23 openings · 1 h 12", Words.weekRow(d, today, xml, utc))
        val yesterday = d.copy(start = day0 - 24 * h, end = day0)
        assertEquals("yesterday · 23 openings · 1 h 12", Words.weekRow(yesterday, today, xml, utc))
        val friday = d.copy(start = day0 - 2 * 24 * h, end = day0 - 24 * h)
        assertEquals("friday · 23 openings · 1 h 12", Words.weekRow(friday, today, xml, utc, Locale.ENGLISH))
        assertEquals("last week: 31 a day · 1 h 40", Words.weekAverage(WeekAverage(31, 100 * m, 7), xml))
        assertEquals(null, Words.weekAverage(WeekAverage(0, 0, 0), xml))
    }

    @Test fun `the phrasing shifts with the day, the numbers do not`() {
        val sessions = (0 until 14).map { Session("com.instagram.android", at(9 + it % 10, it), at(9 + it % 10, it) + 30_000) } +
            Session("com.instagram.android", at(22, 10), at(22, 44))
        val d = day(reflex = listOf(AppDay("com.instagram.android", sessions)), screen = 220 * m, unlocks = 41, first = 9, evening = 48 * m)
        val variants = (0 until 3).map { Words.sentences(d, xml, { names.getValue(it) }, 22 * 60, utc, variant = it) }
        variants.forEach { assertEquals(4, it.size) }
        for (i in 0 until 4) assertEquals(3, variants.map { it[i] }.toSet().size)
        assertEquals(variants[0], Words.sentences(d, xml, { names.getValue(it) }, 22 * 60, utc, variant = 3))
        variants.forEach { s -> assertTrue(s[0].contains("9") && s[0].contains("41")); assertTrue(s[1].contains("34 min") && s[1].contains("22:10")); assertTrue(s[2].contains("48 min")); assertTrue(s[3].contains("14")) }
        assertEquals("what for?", Words.whatFor(xml, 0))
        assertEquals(4, (0 until 4).map { Words.whatFor(xml, it) }.toSet().size)
        assertEquals("what for?", Words.whatFor(xml, 4))
    }

    @Test fun `the pause and the word`() {
        val a = AppDay("com.instagram.android", (0 until 14).map { Session("com.instagram.android", at(9, it), at(9, it) + 3 * m) })
        assertEquals("today: 14 openings · 42 min", Words.pauseToday(a, xml))
        assertEquals("today: 0 openings · 0 s", Words.pauseToday(null, xml))
        assertEquals("20 minutes in instagram", Words.minutesIn(20, "instagram", xml))
        assertEquals("22:00", Words.minutesWord(22 * 60))
        assertEquals("00:00", Words.minutesWord(0))
    }
}
