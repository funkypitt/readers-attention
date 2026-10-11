package com.freedomfighter.readersattention

import android.app.Application
import android.content.Context
import com.freedomfighter.readersattention.data.Day
import com.freedomfighter.readersattention.data.Ev
import com.freedomfighter.readersattention.data.Prefs
import com.freedomfighter.readersattention.data.Sessions
import com.freedomfighter.readersattention.data.Settings
import com.freedomfighter.readersattention.data.Usage
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    /** Bumped each time the app comes back to the front, so the screens read the day again. */
    val version = MutableStateFlow(0)
    override fun onCreate() { super.onCreate(); prefs }

    companion object {
        /** How many days back the system is asked; the week screen shows today and seven days. */
        const val DAYS = 8

        /** The raw events for [count] days, from a little before the first midnight so a session open at midnight is seen. */
        fun events(context: Context, count: Int): Pair<List<Ev>, List<Pair<Long, Long>>> {
            val bounds = Usage.dayBounds(count)
            val from = bounds.last().first - 6 * 3600_000L
            return Usage.events(context, from, bounds.first().second) to bounds
        }

        /** The days summarised with the current classes; pure once the events are in hand. */
        fun days(context: Context, events: List<Ev>, bounds: List<Pair<Long, Long>>, s: Settings): List<Day> {
            val excluded = Usage.excluded(context)
            return Sessions.days(events, bounds, s.eveningMinutes, excluded) { p -> (context.applicationContext as App).prefs.isReflex(p, Usage.category(context, p)) }
        }
    }
}
