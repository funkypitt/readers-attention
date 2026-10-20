package com.freedomfighter.readersattention.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import com.freedomfighter.readersattention.AndroidRes
import com.freedomfighter.readersattention.App
import com.freedomfighter.readersattention.MainActivity
import com.freedomfighter.readersattention.R
import com.freedomfighter.readersattention.StateProvider
import com.freedomfighter.readersattention.Words
import com.freedomfighter.readersattention.data.Palettes
import com.freedomfighter.readersattention.data.Usage
import com.freedomfighter.readersattention.service.BatchService
import kotlin.concurrent.thread

/**
 * The home-screen widget: today in two lines, "reflex 23 · 1 h 12" and "tools 2 h 28".
 * A tap opens the app. Refreshed when the app is left and every half hour by the system.
 */
object AttentionWidget {
    /** The app's theme, read from the same setting: background, text, text dimmed. */
    fun colors(context: Context): Triple<Int, Int, Int> {
        val theme = Palettes.parse(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", null))
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val p = Palettes.of(theme, systemDark)
        val fg = p.fg.toInt()
        return Triple(p.bg.toInt(), fg, Color.argb(140, Color.red(fg), Color.green(fg), Color.blue(fg)))
    }

    /** The two lines; computed off the main thread by [refresh]. */
    private fun lines(context: Context): Pair<String, String> {
        val res = AndroidRes(context)
        if (!Usage.allowed(context)) return context.getString(R.string.app_title) to context.getString(R.string.allow_usage)
        val app = context.applicationContext as App
        val (events, bounds) = App.events(context, 1)
        val day = App.days(context, events, bounds, app.prefs.settings.value).first()
        return context.getString(R.string.widget_reflex, day.reflexOpenings, Words.duration(day.reflexMillis, res)) to
            BatchService.nextLine(context, app.prefs.settings.value).let { n -> context.getString(R.string.widget_tools, Words.duration(day.toolsMillis, res)).let { if (n == null) it else context.getString(R.string.pair, it, n) } }
    }

    fun render(context: Context, mgr: AppWidgetManager, ids: IntArray, lines: Pair<String, String>) {
        val (bg, fg, dim) = colors(context)
        ids.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_attention)
            v.setInt(R.id.widget_root, "setBackgroundColor", bg)
            v.setTextColor(R.id.widget_title, fg)
            v.setTextColor(R.id.widget_sub, dim)
            v.setTextViewText(R.id.widget_title, lines.first)
            v.setTextViewText(R.id.widget_sub, lines.second)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(id, v)
        }
    }

    fun refresh(context: Context, done: () -> Unit = {}) {
        val ctx = context.applicationContext
        StateProvider.notifyChanged(ctx) // the launcher's tile reads today again
        val mgr = AppWidgetManager.getInstance(ctx) ?: return done()
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, AttentionWidgetProvider::class.java))
        if (ids.isEmpty()) return done()
        thread {
            val l = runCatching { lines(ctx) }.getOrElse { ctx.getString(R.string.app_title) to "" }
            render(ctx, mgr, ids, l)
            done()
        }
    }
}

class AttentionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        AttentionWidget.refresh(context) { pending.finish() }
    }
}
