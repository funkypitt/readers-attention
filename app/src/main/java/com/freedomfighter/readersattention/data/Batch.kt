package com.freedomfighter.readersattention.data

/**
 * Notifications delivered together, at a few times of the day. Pure: the clock, the
 * delivery times (minutes from midnight) and what is known of a notification come in,
 * the decision and the delay come out.
 *
 * A notification that arrives between two delivery times is held until the next one; one
 * that arrives at a delivery time (a minute before to two minutes after, which is when the
 * held ones come back) goes through at once.
 */
object Batch {
    const val DEFAULT_SLOTS = "540,780,1080" // 09:00, 13:00, 18:00
    const val WINDOW_BEFORE_MS = 60_000L
    const val WINDOW_AFTER_MS = 120_000L
    private const val DAY_MS = 24 * 3600_000L

    /** The notification categories that never wait (Notification.CATEGORY_*). */
    val urgentCategories = setOf("call", "alarm", "reminder", "navigation", "transport", "sys", "service", "err")

    /** The packages that never wait, besides the dialer found at run time. */
    val systemPackages = setOf("android", "com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.android.incallui")

    fun parseSlots(s: String?): List<Int> =
        (s ?: DEFAULT_SLOTS).split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }.distinct().sorted()

    fun formatSlots(slots: List<Int>): String = slots.distinct().sorted().joinToString(",")

    /** "13:00" → 780, null when it is not a time. */
    fun parseTime(s: String): Int? {
        val m = Regex("""^\s*(\d{1,2})\s*[:h.]\s*(\d{2})\s*$""").find(s) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** True when [nowMs] (milliseconds since midnight) is at one of the delivery times. */
    fun atSlot(nowMs: Long, slots: List<Int>): Boolean =
        slots.any { s -> val ms = s * 60_000L; nowMs >= ms - WINDOW_BEFORE_MS && nowMs < ms + WINDOW_AFTER_MS }

    /**
     * The next delivery time, in milliseconds since this midnight (past 24 h when it is
     * tomorrow's). At a delivery time, the one after it. Null without delivery times.
     */
    fun nextSlotMs(nowMs: Long, slots: List<Int>): Long? {
        if (slots.isEmpty()) return null
        val sorted = slots.sorted()
        // at a slot, that slot is behind the window: the first one whose window has not started
        val after = sorted.map { it * 60_000L }.firstOrNull { ms -> ms - WINDOW_BEFORE_MS > nowMs }
        return after ?: (sorted.first() * 60_000L + DAY_MS)
    }

    /** Minutes from midnight of the next delivery time, for the screens. */
    fun nextSlotMinutes(nowMs: Long, slots: List<Int>): Int? = nextSlotMs(nowMs, slots)?.let { ((it % DAY_MS) / 60_000L).toInt() }

    /** How long a notification arriving now waits; 0 when it goes through at once. */
    fun snoozeMs(nowMs: Long, slots: List<Int>): Long {
        if (atSlot(nowMs, slots)) return 0
        val next = nextSlotMs(nowMs, slots) ?: return 0
        return (next - nowMs).coerceAtLeast(0)
    }

    /** True when a notification is left alone whatever the time. */
    fun exempt(pkg: String, category: String?, ongoing: Boolean, ownPackage: String, dialer: String?, delivered: Set<String>): Boolean =
        ongoing || pkg == ownPackage || pkg in systemPackages || (dialer != null && pkg == dialer) ||
            pkg in delivered || (category != null && category in urgentCategories)
}
