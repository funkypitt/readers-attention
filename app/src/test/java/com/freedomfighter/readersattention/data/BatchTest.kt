package com.freedomfighter.readersattention.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTest {
    private val slots = Batch.parseSlots(Batch.DEFAULT_SLOTS) // 09:00, 13:00, 18:00
    private fun t(h: Int, m: Int, s: Int = 0) = ((h * 60 + m) * 60 + s) * 1000L

    @Test fun `the default times`() = assertEquals(listOf(540, 780, 1080), slots)

    @Test fun `between two times a notification waits for the next`() {
        assertEquals(t(13, 0) - t(10, 30), Batch.snoozeMs(t(10, 30), slots))
        assertEquals(780, Batch.nextSlotMinutes(t(10, 30), slots))
    }

    @Test fun `after the last time it waits for tomorrow's first`() {
        assertEquals(t(24, 0) + t(9, 0) - t(22, 15), Batch.snoozeMs(t(22, 15), slots))
        assertEquals(540, Batch.nextSlotMinutes(t(22, 15), slots))
        // just after midnight, the first of the day is still ahead
        assertEquals(t(9, 0) - t(0, 5), Batch.snoozeMs(t(0, 5), slots))
    }

    @Test fun `at a delivery time it goes through, and the next shown is the one after`() {
        assertTrue(Batch.atSlot(t(13, 0), slots))
        assertTrue(Batch.atSlot(t(12, 59, 30), slots))
        assertTrue(Batch.atSlot(t(13, 1, 59), slots))
        assertFalse(Batch.atSlot(t(13, 2), slots))
        assertEquals(0L, Batch.snoozeMs(t(13, 0, 20), slots))
        assertEquals(1080, Batch.nextSlotMinutes(t(13, 0, 20), slots))
        // just before the window: waits until the time itself
        assertEquals(90_000L, Batch.snoozeMs(t(12, 58, 30), slots))
    }

    @Test fun `without delivery times nothing waits`() {
        assertEquals(0L, Batch.snoozeMs(t(10, 0), emptyList()))
        assertNull(Batch.nextSlotMinutes(t(10, 0), emptyList()))
    }

    @Test fun `times are parsed and kept in order`() {
        assertEquals(listOf(420, 1200), Batch.parseSlots("1200, 420, 420, 9999, x"))
        assertEquals("420,1200", Batch.formatSlots(listOf(1200, 420)))
        assertEquals(780, Batch.parseTime("13:00"))
        assertEquals(555, Batch.parseTime(" 9h15 "))
        assertNull(Batch.parseTime("25:00"))
        assertNull(Batch.parseTime("noon"))
    }

    @Test fun `what never waits`() {
        val d = setOf("com.example.family")
        assertTrue(Batch.exempt("com.whatsapp", "call", false, "me", "com.google.android.dialer", d))
        assertTrue(Batch.exempt("com.example.clock", "alarm", false, "me", null, d))
        assertTrue(Batch.exempt("com.example.maps", "navigation", false, "me", null, d))
        assertTrue(Batch.exempt("com.example.player", "msg", true, "me", null, d)) // ongoing
        assertTrue(Batch.exempt("android", null, false, "me", null, d))
        assertTrue(Batch.exempt("com.android.systemui", null, false, "me", null, d))
        assertTrue(Batch.exempt("com.google.android.dialer", null, false, "me", "com.google.android.dialer", d))
        assertTrue(Batch.exempt("com.example.family", "msg", false, "me", null, d))
        assertTrue(Batch.exempt("me", null, false, "me", null, d))
        assertFalse(Batch.exempt("com.whatsapp", "msg", false, "me", "com.google.android.dialer", d))
        assertFalse(Batch.exempt("com.example.mail", null, false, "me", null, d))
    }
}
