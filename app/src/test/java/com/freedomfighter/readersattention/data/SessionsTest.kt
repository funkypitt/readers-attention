package com.freedomfighter.readersattention.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsTest {
    private val day0 = 1_000_000_000_000L // an arbitrary midnight
    private val h = 3600_000L
    private val m = 60_000L
    private fun at(hh: Int, mm: Int, ss: Int = 0) = day0 + hh * h + mm * m + ss * 1000L

    private val reflex = setOf("insta", "tiktok")
    private val excluded = setOf("launcher", "systemui")

    private fun events(vararg e: Ev) = e.toList()
    private fun open(pkg: String, t: Long, cls: String = "Main") = Ev(t, Kind.RESUMED, pkg, cls)
    private fun close(pkg: String, t: Long, cls: String = "Main") = Ev(t, Kind.PAUSED, pkg, cls)

    private fun oneDay(evs: List<Ev>, end: Long = day0 + 24 * h, evening: Int = 22 * 60) =
        Sessions.days(evs, listOf(day0 to end), evening, excluded) { it in reflex }.first()

    @Test fun `resumed and paused make one session`() {
        val s = Sessions.sessions(events(open("insta", at(10, 0)), close("insta", at(10, 5))), day0, day0 + 24 * h)
        assertEquals(listOf(Session("insta", at(10, 0), at(10, 5))), s)
    }

    @Test fun `activity changes inside one app are one session`() {
        val evs = events(
            open("insta", at(10, 0), "A"), open("insta", at(10, 2), "B"), close("insta", at(10, 2, 1), "A"),
            Ev(at(10, 6), Kind.STOPPED, "insta", "B")
        )
        val s = Sessions.sessions(evs, day0, day0 + 24 * h)
        assertEquals(1, s.size)
        assertEquals(6 * m, s[0].length)
    }

    @Test fun `a two-second visit is still an opening`() {
        val d = oneDay(events(open("insta", at(9, 0)), close("insta", at(9, 0, 2))))
        assertEquals(1, d.reflexOpenings)
        assertEquals(2000L, d.reflexMillis)
    }

    @Test fun `screen off closes an open session and the end closes the rest`() {
        val evs = events(open("insta", at(20, 0)), Ev(at(20, 10), Kind.SCREEN_OFF), open("maps", at(23, 50)))
        val d = oneDay(evs)
        assertEquals(10 * m, d.reflexMillis)
        assertEquals(10 * m, d.tools.first { it.pkg == "maps" }.millis)
    }

    @Test fun `a stretch the system never closed ends at the next app, the shutdown or the start`() {
        val evs = events(
            open("insta", day0 - 2 * 24 * h), Ev(day0 - 2 * 24 * h + 10 * m, Kind.BOOT),
            open("maps", at(9, 0)), open("mail", at(9, 10)), close("mail", at(9, 20)),
            open("insta", at(9, 30), "A"), open("insta", at(9, 31), "B"), close("insta", at(9, 32), "A"), close("insta", at(9, 40), "B")
        )
        val d = oneDay(evs)
        assertEquals(0, d.reflex.count { it.pkg == "insta" && it.sessions.any { s -> s.start == day0 } })
        assertEquals(10 * m, d.tools.first { it.pkg == "maps" }.millis)
        assertEquals(10 * m, d.reflexMillis)
        assertEquals(30 * m, d.screenMillis)
    }

    @Test fun `sessions are clipped to the day and the screen time is a union`() {
        val evs = events(
            open("insta", day0 - 30 * m), close("insta", at(0, 30)),
            open("maps", at(12, 0)), close("maps", at(12, 30)),
            open("launcher", at(12, 20)), close("launcher", at(12, 40))
        )
        val d = oneDay(evs)
        assertEquals(30 * m, d.reflexMillis)
        // 0:00-0:30 insta, 12:00-12:20 maps then 12:20-12:40 launcher: the screen was in use 70 min
        assertEquals(70 * m, d.screenMillis)
        assertEquals(20 * m, d.tools.first { it.pkg == "maps" }.millis)
        assertTrue(d.tools.none { it.pkg == "launcher" })
    }

    @Test fun `checks are the openings under a minute`() {
        val evs = ArrayList<Ev>()
        repeat(4) { i -> evs += open("insta", at(8 + i, 0)); evs += close("insta", at(8 + i, 0, 30)) }
        evs += open("insta", at(15, 0)); evs += close("insta", at(15, 20))
        val d = oneDay(evs)
        assertEquals(5, d.reflexOpenings)
        assertEquals("insta" to 4, d.mostChecked)
        assertEquals(20 * m, d.longestReflex?.length)
    }

    @Test fun `first app after unlock`() {
        val evs = events(
            Ev(at(7, 0), Kind.UNLOCKED), open("insta", at(7, 0, 2)), close("insta", at(7, 3)),
            Ev(at(8, 0), Kind.UNLOCKED), open("maps", at(8, 0, 1)), close("maps", at(8, 1)), open("insta", at(8, 1, 1)), close("insta", at(8, 2)),
            Ev(at(9, 0), Kind.UNLOCKED), open("launcher", at(9, 0, 1)), close("launcher", at(9, 0, 5)), open("tiktok", at(9, 0, 6)), close("tiktok", at(9, 5))
        )
        val d = oneDay(evs)
        assertEquals(3, d.unlocks)
        assertEquals(2, d.reflexFirst)
    }

    @Test fun `screen on counts as an unlock when the keyguard is silent`() {
        val evs = events(Ev(at(7, 0), Kind.SCREEN_ON), Ev(at(9, 0), Kind.SCREEN_ON), open("insta", at(9, 0, 1)), close("insta", at(9, 1)))
        val d = oneDay(evs)
        assertEquals(2, d.unlocks)
        assertEquals(1, d.reflexFirst)
    }

    @Test fun `evening minutes count only the part after the hour`() {
        val evs = events(open("insta", at(21, 50)), close("insta", at(22, 20)), open("maps", at(23, 0)), close("maps", at(23, 30)))
        val d = oneDay(evs)
        assertEquals(20 * m, d.eveningReflexMillis)
        assertEquals(0L, oneDay(evs, evening = 23 * 60 + 30).eveningReflexMillis)
    }

    @Test fun `week average skips the empty days`() {
        val evs = events(
            open("insta", day0 - 24 * h + 10 * h), close("insta", day0 - 24 * h + 11 * h),
            open("insta", day0 - 3 * 24 * h + 10 * h), close("insta", day0 - 3 * 24 * h + 10 * h + 20 * m),
            open("insta", day0 - 3 * 24 * h + 12 * h), close("insta", day0 - 3 * 24 * h + 12 * h + 20 * m)
        )
        val bounds = (0 until 8).map { i -> (day0 - i * 24 * h) to (day0 - (i - 1) * 24 * h) }
        val days = Sessions.days(evs, bounds, 22 * 60, excluded) { it in reflex }
        assertEquals(8, days.size)
        assertTrue(days[0].empty)
        val a = Sessions.average(days.drop(1))
        assertEquals(2, a.days)
        assertEquals(2, a.openings) // (1 + 2) / 2 rounded
        assertEquals(50 * m, a.millis)
        assertNull(Sessions.average(emptyList()).takeIf { it.days > 0 })
    }

    @Test fun `apps are sorted by time and split by class`() {
        val evs = events(
            open("maps", at(10, 0)), close("maps", at(10, 5)),
            open("mail", at(11, 0)), close("mail", at(11, 30)),
            open("tiktok", at(12, 0)), close("tiktok", at(12, 1)),
            open("insta", at(13, 0)), close("insta", at(13, 10))
        )
        val d = oneDay(evs)
        assertEquals(listOf("insta", "tiktok"), d.reflex.map { it.pkg })
        assertEquals(listOf("mail", "maps"), d.tools.map { it.pkg })
        assertEquals(35 * m, d.toolsMillis)
    }
}
