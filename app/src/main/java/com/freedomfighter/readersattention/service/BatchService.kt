package com.freedomfighter.readersattention.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.TileService
import android.telecom.TelecomManager
import com.freedomfighter.readersattention.App
import com.freedomfighter.readersattention.R
import com.freedomfighter.readersattention.StateProvider
import com.freedomfighter.readersattention.Words
import com.freedomfighter.readersattention.data.Batch
import com.freedomfighter.readersattention.data.Settings as AppSettings
import java.time.LocalTime

/**
 * Notifications delivered together. While batching is on, each notification that arrives
 * between two delivery times is put to sleep until the next one; at a delivery time the
 * system brings them back and they go through. Calls, alarms, reminders, navigation, what is
 * ongoing, the system, the dialer and the apps the person marked are never touched. Nothing
 * is read beyond the app, the kind and the flags; nothing is removed, only delayed.
 */
class BatchService : NotificationListenerService() {
    companion object {
        /** True when the person has given this app notification access. */
        fun enabled(context: Context): Boolean {
            val me = ComponentName(context, BatchService::class.java)
            val list = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return list.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
        }

        /** True when notifications are being held: the option is on and the access given. */
        fun active(context: Context, s: AppSettings): Boolean = s.batching && s.slots.isNotEmpty() && enabled(context)

        /** "notifications at 13:00", or null when nothing is held. */
        fun nextLine(context: Context, s: AppSettings): String? {
            if (!active(context, s)) return null
            val now = LocalTime.now().toNanoOfDay() / 1_000_000L
            val m = Batch.nextSlotMinutes(now, s.slots) ?: return null
            return context.getString(R.string.next_delivery, Words.minutesWord(m))
        }

        /** Turns batching on or off and tells the tile and the launcher. */
        fun set(context: Context, on: Boolean) {
            (context.applicationContext as App).prefs.setBatching(on)
            TileService.requestListeningState(context, ComponentName(context, BatchTileService::class.java))
            StateProvider.notifyChanged(context)
        }
    }

    private val prefs get() = (application as App).prefs
    private val dialer: String? by lazy { runCatching { (getSystemService(TELECOM_SERVICE) as TelecomManager).defaultDialerPackage }.getOrNull() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val s = prefs.settings.value
        if (!s.batching || s.slots.isEmpty()) return
        val n = sbn.notification
        val ongoing = sbn.isOngoing || (n.flags and (android.app.Notification.FLAG_FOREGROUND_SERVICE or android.app.Notification.FLAG_NO_CLEAR)) != 0
        if (Batch.exempt(sbn.packageName, n.category, ongoing, packageName, dialer, s.delivered)) return
        val now = LocalTime.now().toNanoOfDay() / 1_000_000L
        val wait = Batch.snoozeMs(now, s.slots)
        if (wait <= 0) return
        runCatching { snoozeNotification(sbn.key, wait) }
    }
}
