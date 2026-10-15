package com.freedomfighter.readersattention.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.freedomfighter.readersattention.AndroidRes
import com.freedomfighter.readersattention.App
import com.freedomfighter.readersattention.R
import com.freedomfighter.readersattention.StateProvider
import com.freedomfighter.readersattention.Words
import com.freedomfighter.readersattention.data.FontChoice
import com.freedomfighter.readersattention.data.PAUSE_BREATH
import com.freedomfighter.readersattention.data.PAUSE_FIRM
import com.freedomfighter.readersattention.data.PAUSE_OFF
import com.freedomfighter.readersattention.data.Sessions
import com.freedomfighter.readersattention.data.TextSize
import com.freedomfighter.readersattention.data.ThemeMode
import com.freedomfighter.readersattention.data.Usage
import java.time.LocalTime
import kotlin.concurrent.thread

/**
 * The only thing this service hears is "a window came to the front", with its package. From
 * that it offers two things, both off until the person turns them on in the settings:
 * a pause before a reflex app (a full-screen overlay, a question, a count before "go on")
 * and a word after a while in one (one line at the top for a few seconds). It reads no
 * content, performs no gesture, and never blocks: "go on" always comes.
 */
class WatchService : AccessibilityService() {
    companion object {
        const val PASS_MS = 10 * 60_000L
        const val COUNT_S = 6
        const val COUNT_EVENING_S = 20
        const val COUNT_FIRM_S = 12
        const val WORD_SHOWN_MS = 4_000L

        /** True when the service is among the enabled accessibility services. */
        fun enabled(context: Context): Boolean {
            val me = ComponentName(context, WatchService::class.java).flattenToString()
            val short = ComponentName(context, WatchService::class.java).flattenToShortString()
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(me, true) || it.equals(short, true) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs get() = (application as App).prefs
    private val res by lazy { AndroidRes(this) }
    private val excluded by lazy { Usage.excluded(this) }
    private var front: String? = null
    private var frontSince = 0L
    private val passed = HashMap<String, Long>()
    private var pause: View? = null
    private var pausePkg: String? = null
    private var word: View? = null
    private var wordRunnable: Runnable? = null
    private var countRunnable: Runnable? = null
    private val wm get() = getSystemService(WINDOW_SERVICE) as WindowManager

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { front = null; removePause(); cancelWord() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        removePause(); cancelWord(); removeWord()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own overlays
        val cls = event.className?.toString() ?: return
        if (pkg == front) return // dialogs, menus and windows of the app already in front
        if (!isActivity(pkg, cls) && !launchable(pkg)) return // the keyboard, a system dialog: the front app has not changed
        front = pkg; frontSince = System.currentTimeMillis()
        StateProvider.notifyChanged(this) // an app was left: the launcher's tile may be looked at next
        if (pausePkg != null && pausePkg != pkg) removePause()
        cancelWord()
        if (pkg in excluded) return
        val s = prefs.settings.value
        if (!prefs.isReflex(pkg, Usage.category(this, pkg))) return
        val level = s.pauseFor(pkg)
        if (level != PAUSE_OFF && frontSince - (passed[pkg] ?: 0L) > PASS_MS && pause == null) showPause(pkg, level)
        if (s.wordMinutes > 0) scheduleWord(pkg, s.wordMinutes)
    }

    private fun isActivity(pkg: String, cls: String): Boolean =
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0); true }.getOrDefault(false)

    // An app brought back from the background names a view in its first event, not its activity;
    // a package one can open from the launcher coming to the front counts as that app.
    private val launchableCache = HashMap<String, Boolean>()
    private fun launchable(pkg: String): Boolean = launchableCache.getOrPut(pkg) {
        runCatching { packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
    }

    // ---- the look, the same as the app's ----

    private fun dark(): Boolean = when (prefs.settings.value.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    private fun bg() = if (dark()) Color.BLACK else Color.WHITE
    private fun fg() = if (dark()) Color.WHITE else Color.BLACK
    private fun dim() = if (dark()) Color.argb(140, 255, 255, 255) else Color.argb(140, 0, 0, 0)
    private fun tileSp() = when (prefs.settings.value.textSize) { TextSize.SMALL -> 24f; TextSize.MEDIUM -> 28f; TextSize.LARGE -> 32f }
    private fun face(): Typeface = when (prefs.settings.value.font) {
        FontChoice.SANS -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        FontChoice.SERIF -> Typeface.SERIF
        FontChoice.MONO -> Typeface.MONOSPACE
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, sp: Float, color: Int, padV: Int = 14): TextView = TextView(this).apply {
        text = s; typeface = face(); setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setPadding(dp(28), dp(padV), dp(28), dp(padV))
    }

    private fun evening(): Boolean {
        val m = LocalTime.now().let { it.hour * 60 + it.minute }
        val e = prefs.settings.value.eveningMinutes
        return if (e == 0) true else m >= e || m < 4 * 60
    }

    // ---- the pause ----

    /**
     * Light: the reasons to tap, then "go on" after six seconds (twenty in the evening).
     * Firm: the reason typed in a few words, and twelve seconds. Breathing: five breaths
     * guided by a ring, about a minute, then the reasons to tap and "go on" at once. The cost
     * of the pause is what makes it work, so the level is the person's to set, for all the
     * reflex apps or for one. "not now" is always the first row.
     */
    private fun showPause(pkg: String, level: Int) {
        val firm = level == PAUSE_FIRM
        val breath = level == PAUSE_BREATH
        val name = Usage.name(this, pkg)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val tile = tileSp()
        col.addView(text(name, tile * 1.3f, fg(), 6).apply { setPadding(dp(28), dp(36), dp(28), dp(2)) })
        val todayLine = text(Words.pauseToday(null, res), tile * 0.62f, dim(), 4)
        col.addView(todayLine)
        // the breaths: the ring, the word of the phase and the breath's number under it
        var ring: BreathView? = null
        var breathWord: TextView? = null
        var breathCount: TextView? = null
        if (breath) {
            val box = android.widget.FrameLayout(this)
            ring = BreathView(this, fg(), resources.displayMetrics.density)
            box.addView(ring, android.widget.FrameLayout.LayoutParams(dp(200), dp(200), Gravity.CENTER))
            col.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)).apply { setMargins(0, dp(12), 0, 0) })
            breathWord = text(getString(R.string.breathe_in), tile, fg(), 4).apply { gravity = Gravity.CENTER }
            col.addView(breathWord)
            breathCount = text(getString(R.string.breath_n, 1, BreathView.BREATHS), tile * 0.62f, dim(), 4).apply { gravity = Gravity.CENTER }
            col.addView(breathCount)
        }
        val question = text(Words.whatFor(res), tile * 0.8f, dim(), 10).apply { setPadding(dp(28), dp(28), dp(28), dp(4)) }
        col.addView(question)
        val now = System.currentTimeMillis()
        var answered = !firm
        var typed: EditText? = null
        val reasonRows = ArrayList<View>()
        if (firm) {
            // the reason in the person's own words; "go on" waits for a few letters
            val field = EditText(this).apply {
                typeface = face(); setTextColor(fg()); setHintTextColor(dim())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tile)
                hint = getString(R.string.type_reason)
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                setPadding(dp(28), dp(12), dp(28), dp(12))
            }
            typed = field
            col.addView(field)
            col.addView(rule().apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(28), 0, dp(28), 0) })
        } else {
            var chosen: TextView? = null
            Words.answerIds.forEachIndexed { i, id ->
                val row = text(getString(id), tile, fg(), 12)
                row.setOnClickListener {
                    chosen?.let { it.setBackgroundColor(Color.TRANSPARENT); it.setTextColor(fg()) }
                    chosen = row; row.setBackgroundColor(fg()); row.setTextColor(bg())
                    prefs.recordAnswer(pkg, now, i)
                }
                col.addView(row); reasonRows += row
            }
        }
        // while breathing, the question and the reasons wait for the end of the breaths
        if (breath) { question.visibility = View.GONE; reasonRows.forEach { it.visibility = View.GONE } }
        // "not now" first, always; then the count, and "go on" in its place
        val notNow = text(getString(R.string.not_now), tile * 0.8f, fg(), 16)
        val count = text("", tile * 0.8f, dim(), 16)
        val goOn = text(getString(R.string.go_on), tile * 0.8f, fg(), 16).apply { visibility = View.GONE }
        val waiting = text(getString(R.string.type_first), tile * 0.62f, dim(), 16).apply { visibility = View.GONE }
        var counted = false
        fun settle() {
            if (!counted) return
            val reason = typed?.text?.toString()?.trim() ?: ""
            if (firm) answered = reason.length >= 3
            count.visibility = View.GONE
            goOn.visibility = if (answered) View.VISIBLE else View.GONE
            waiting.visibility = if (answered) View.GONE else View.VISIBLE
        }
        typed?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) = settle()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        goOn.setOnClickListener {
            typed?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { prefs.recordAnswer(pkg, now, -1, it) }
            passed[pkg] = System.currentTimeMillis(); removePause()
        }
        notNow.setOnClickListener { removePause(); performGlobalAction(GLOBAL_ACTION_HOME) }
        root.addView(rule())
        root.addView(notNow); root.addView(count); root.addView(waiting); root.addView(goOn)
        val tick: Runnable
        if (breath) {
            // the ring follows the clock; when the five breaths are done, the question comes and "go on" with it
            val start = System.currentTimeMillis()
            count.visibility = View.GONE
            tick = object : Runnable {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - start
                    val at = BreathView.at(elapsed)
                    if (at == null) {
                        ring?.parent?.let { (it as View).visibility = View.GONE }; breathWord?.visibility = View.GONE; breathCount?.visibility = View.GONE
                        question.visibility = View.VISIBLE; reasonRows.forEach { it.visibility = View.VISIBLE }
                        counted = true; settle(); return
                    }
                    ring?.fraction = BreathView.size(elapsed)
                    breathWord?.text = getString(when (at.second) { BreathView.PHASE_IN -> R.string.breathe_in; BreathView.PHASE_HOLD -> R.string.breathe_hold; else -> R.string.breathe_out })
                    breathCount?.text = getString(R.string.breath_n, at.first, BreathView.BREATHS)
                    handler.postDelayed(this, 40)
                }
            }
        } else {
            val total = if (firm) COUNT_FIRM_S else if (evening()) COUNT_EVENING_S else COUNT_S
            var left = total
            tick = object : Runnable {
                override fun run() {
                    if (left > 0) { count.text = left.toString(); left--; handler.postDelayed(this, 1000) }
                    else { counted = true; settle() }
                }
            }
        }
        countRunnable = tick
        handler.post(tick)
        // the system bars and the keyboard: the content stays clear of them
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(0, b.top, 0, b.bottom); insets
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.OPAQUE
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        runCatching { wm.addView(root, lp) }.onSuccess { pause = root; pausePkg = pkg }.onFailure { return }
        // today's count for this app, read off the main thread
        thread {
            val (events, bounds) = App.events(this, 1)
            val day = Sessions.days(events, bounds, prefs.settings.value.eveningMinutes, excluded) { true }.first()
            val a = day.reflex.firstOrNull { it.pkg == pkg }
            handler.post { if (pause === root) todayLine.text = Words.pauseToday(a, res) }
        }
    }

    private fun rule() = View(this).apply {
        setBackgroundColor(dim())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun removePause() {
        countRunnable?.let { handler.removeCallbacks(it) }; countRunnable = null
        pause?.let { runCatching { wm.removeView(it) } }
        pause = null; pausePkg = null
    }

    // ---- the word after a while ----

    private fun scheduleWord(pkg: String, minutes: Int) {
        val r = object : Runnable {
            override fun run() {
                if (front != pkg) return
                val elapsed = ((System.currentTimeMillis() - frontSince) / 60_000L).toInt()
                showWord(Words.minutesIn(elapsed, Usage.name(this@WatchService, pkg), res))
                handler.postDelayed(this, minutes * 60_000L)
            }
        }
        wordRunnable = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    private fun cancelWord() { wordRunnable?.let { handler.removeCallbacks(it) }; wordRunnable = null }

    private fun showWord(s: String) {
        removeWord()
        val v = text(s, tileSp() * 0.8f, fg(), 16).apply { setBackgroundColor(bg()); gravity = Gravity.START }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        lp.gravity = Gravity.TOP or Gravity.START
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(dp(28), b.top + dp(16), dp(28), dp(16)); insets
        }
        runCatching { wm.addView(v, lp) }.onSuccess { word = v }
        handler.postDelayed({ if (word === v) removeWord() }, WORD_SHOWN_MS)
    }

    private fun removeWord() { word?.let { runCatching { wm.removeView(it) } }; word = null }
}
