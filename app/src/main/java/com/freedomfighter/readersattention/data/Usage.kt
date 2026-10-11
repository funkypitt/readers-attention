package com.freedomfighter.readersattention.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.view.inputmethod.InputMethodManager
import java.time.LocalDate
import java.time.ZoneId

/** What the system knows, read on the phone: the usage events, the apps' names and categories. */
object Usage {
    /** True once "usage access" is given in the system settings. */
    fun allowed(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT)
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        else mode == AppOpsManager.MODE_ALLOWED
    }

    /** The events between [from] and [to], in our own terms. */
    fun events(context: Context, from: Long, to: Long): List<Ev> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val out = ArrayList<Ev>()
        val q = usm.queryEvents(from, to) ?: return out
        val e = UsageEvents.Event()
        while (q.hasNextEvent()) {
            q.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> Kind.PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> Kind.STOPPED
                UsageEvents.Event.KEYGUARD_HIDDEN -> Kind.UNLOCKED
                UsageEvents.Event.SCREEN_INTERACTIVE -> Kind.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> Kind.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> Kind.BOOT
                else -> null
            } ?: continue
            out += Ev(e.timeStamp, kind, e.packageName ?: "", e.className ?: "")
        }
        return out
    }

    /** (start, end) of today and the [count] - 1 days before it, today first; today ends now. */
    fun dayBounds(count: Int, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Pair<Long, Long>> {
        val today = LocalDate.now(zone)
        return (0 until count).map { i ->
            val start = today.minusDays(i.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.minusDays(i.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli()
            start to minOf(end, now)
        }
    }

    /** The packages that are not apps one opens: the launcher, the system UI, the settings, the keyboards, this app. */
    fun excluded(context: Context): Set<String> {
        val pm = context.packageManager
        val out = HashSet<String>()
        out += context.packageName
        out += listOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.intentresolver",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.launcher3"
        )
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).forEach { out += it.activityInfo.packageName }
        }
        runCatching {
            pm.queryIntentActivities(Intent(android.provider.Settings.ACTION_SETTINGS), 0).forEach { out += it.activityInfo.packageName }
        }
        runCatching {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).inputMethodList.forEach { out += it.packageName }
        }
        return out
    }

    private val names = HashMap<String, String>()
    private val categories = HashMap<String, Int>()

    /** The app's name, in lower case as everything on these screens; the package when unknown. */
    fun name(context: Context, pkg: String): String = names.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrNull()?.lowercase() ?: pkg.substringAfterLast('.')
    }

    /** The system's category of the app, [Classify.CATEGORY_UNDEFINED] when it has none. */
    fun category(context: Context, pkg: String): Int = categories.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }.getOrNull() ?: ApplicationInfo.CATEGORY_UNDEFINED
    }
}
