package com.freedomfighter.readersattention

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import com.freedomfighter.readersattention.data.Usage

/**
 * Today for Reader's Launcher's tile (`content://…/today`, one row): whether usage access is
 * given, the reflex openings and time, the tools' time, and the two lines already in words,
 * in this app's language. Open to Reader's Launcher only, recognised by its signature; the
 * launcher is told when the app has shown a new day ([notifyChanged]).
 */
class StateProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.freedomfighter.readersattention"
        val TODAY: Uri = Uri.parse("content://$AUTHORITY/today")
        private const val LAUNCHER = "com.freedomfighter.readerslauncher"
        // the launcher's release key, and the key of its first versions (still the one Android 12 and older see)
        private val KEYS = listOf(
            "B9C722E093F963AB7812922623E1C1A72FB9998537AD7D9AEFA8B1A3F3A8120C",
            "9B6AE69650B1CCF5BFA08AD15DC8B412622B8E5A3202EA066215067897A2BE28"
        ).map { hex -> ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }

        /** Tells the launcher's tile to read today again. */
        fun notifyChanged(context: Context) {
            runCatching { context.contentResolver.notifyChange(TODAY, null) }
        }
    }

    private fun trusted(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val pm = context?.packageManager ?: return false
        val names = pm.getPackagesForUid(uid) ?: return false
        return LAUNCHER in names && KEYS.any { pm.hasSigningCertificate(LAUNCHER, it, PackageManager.CERT_INPUT_SHA256) }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null
        if (!trusted()) throw SecurityException("not Reader's Launcher")
        val cursor = MatrixCursor(arrayOf("allowed", "reflex_openings", "reflex_ms", "tools_ms", "line1", "line2"))
        val id = Binder.clearCallingIdentity()
        try {
            val res = AndroidRes(ctx)
            if (!Usage.allowed(ctx)) {
                cursor.addRow(arrayOf<Any>(0, 0, 0L, 0L, ctx.getString(R.string.app_title), ctx.getString(R.string.allow_usage)))
            } else {
                val app = ctx.applicationContext as App
                val (events, bounds) = App.events(ctx, 1)
                val day = App.days(ctx, events, bounds, app.prefs.settings.value).first()
                cursor.addRow(arrayOf<Any>(
                    1, day.reflexOpenings, day.reflexMillis, day.toolsMillis,
                    ctx.getString(R.string.widget_reflex, day.reflexOpenings, Words.duration(day.reflexMillis, res)),
                    ctx.getString(R.string.widget_tools, Words.duration(day.toolsMillis, res))
                ))
            }
        } finally { Binder.restoreCallingIdentity(id) }
        cursor.setNotificationUri(ctx.contentResolver, TODAY)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
