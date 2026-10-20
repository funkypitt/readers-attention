package com.freedomfighter.readersattention.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The inks, the phone's choice between them, then the Plume palettes (see [Palettes]). */
enum class ThemeMode { DARK, LIGHT, SYSTEM, LAVANDE, SAUGE, NORDIQUE, TERRACOTTA, COQUETTE, MILITANTE, TORRIDE, NATURE }
enum class FontChoice { SERIF, SANS, MONO }
enum class TextSize { SMALL, MEDIUM, LARGE }
enum class Align { LEFT, CENTER }

/** The look, the two classes of apps as the person set them, and the interventions. */
data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val font: FontChoice = FontChoice.SANS,
    val textSize: TextSize = TextSize.MEDIUM,
    val align: Align = Align.LEFT,
    val haptics: Boolean = true,
    /** Packages the person moved to the reflex class. */
    val reflex: Set<String> = emptySet(),
    /** Packages the person moved to the tools class. */
    val tools: Set<String> = emptySet(),
    /** The pause before a reflex app: [PAUSE_OFF], [PAUSE_LIGHT] (reasons to tap, 6 s) or [PAUSE_FIRM] (12 s, the reason typed). */
    val pauseLevel: Int = PAUSE_OFF,
    /** The word after this many minutes in a reflex app; 0 = none. */
    val wordMinutes: Int = 0,
    /** When the evening starts, in minutes from midnight. */
    val eveningMinutes: Int = 22 * 60,
    /** A pause level chosen for one app, replacing [pauseLevel] for it. */
    val pauseLevels: Map<String, Int> = emptyMap(),
    /** Notifications held until the next delivery time. */
    val batching: Boolean = false,
    /** The delivery times, in minutes from midnight. */
    val slots: List<Int> = Batch.parseSlots(Batch.DEFAULT_SLOTS),
    /** Packages whose notifications never wait. */
    val delivered: Set<String> = emptySet()
) {
    /** The level that applies to this app. */
    fun pauseFor(pkg: String): Int = pauseLevels[pkg] ?: pauseLevel
    /** True when some app has a pause, whether for all or for it alone. */
    val anyPause: Boolean get() = pauseLevel != PAUSE_OFF || pauseLevels.values.any { it != PAUSE_OFF }
}

const val PAUSE_OFF = 0
const val PAUSE_LIGHT = 1
const val PAUSE_FIRM = 2
/** Five guided breaths, about a minute, then the question. */
const val PAUSE_BREATH = 3

/** What the person answered to "what for?" when a reflex app was opened: a tapped reason by its [index], or the [text] typed. */
data class Answer(val pkg: String, val t: Long, val index: Int, val text: String?)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val answersSp: SharedPreferences = context.getSharedPreferences("answers", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }
    init { sp.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), ThemeMode.DARK),
        font = enumOr(sp.getString("font", null), FontChoice.SANS),
        textSize = enumOr(sp.getString("text_size", null), TextSize.MEDIUM),
        align = enumOr(sp.getString("align", null), Align.LEFT),
        haptics = sp.getBoolean("haptics", true),
        reflex = sp.getStringSet("reflex", emptySet()) ?: emptySet(),
        tools = sp.getStringSet("tools", emptySet()) ?: emptySet(),
        pauseLevel = sp.getInt("pause_level", PAUSE_OFF),
        wordMinutes = sp.getInt("word_minutes", 0),
        eveningMinutes = sp.getInt("evening_minutes", 22 * 60),
        pauseLevels = parseLevels(sp.getString("pause_levels", null)),
        batching = sp.getBoolean("batching", false),
        slots = Batch.parseSlots(sp.getString("slots", null)),
        delivered = sp.getStringSet("delivered", emptySet()) ?: emptySet()
    )
    private fun parseLevels(s: String?): Map<String, Int> = (s ?: "").split(';').mapNotNull { e ->
        val pkg = e.substringBefore('=', ""); val l = e.substringAfter('=', "").toIntOrNull()
        if (pkg.isEmpty() || l == null) null else pkg to l
    }.toMap()
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    fun setTheme(m: ThemeMode) = sp.edit().putString("theme", m.name).apply()
    fun setFont(f: FontChoice) = sp.edit().putString("font", f.name).apply()
    fun setTextSize(t: TextSize) = sp.edit().putString("text_size", t.name).apply()
    fun setAlign(a: Align) = sp.edit().putString("align", a.name).apply()
    fun setHaptics(v: Boolean) = sp.edit().putBoolean("haptics", v).apply()
    fun setPauseLevel(v: Int) = sp.edit().putInt("pause_level", v).apply()
    fun setWordMinutes(v: Int) = sp.edit().putInt("word_minutes", v).apply()
    fun setEveningMinutes(v: Int) = sp.edit().putInt("evening_minutes", v).apply()
    /** The pause for one app; null = as set for all. */
    fun setPauseFor(pkg: String, level: Int?) {
        val m = HashMap(_settings.value.pauseLevels)
        if (level == null) m.remove(pkg) else m[pkg] = level
        sp.edit().putString("pause_levels", m.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
    fun setBatching(v: Boolean) = sp.edit().putBoolean("batching", v).apply()
    fun setSlots(v: List<Int>) = sp.edit().putString("slots", Batch.formatSlots(v)).apply()
    fun setDelivered(pkg: String, always: Boolean) {
        val d = HashSet(_settings.value.delivered)
        if (always) d += pkg else d -= pkg
        sp.edit().putStringSet("delivered", d).apply()
    }
    /** One tap: the next theme, round and round. */
    fun nextTheme() = setTheme(Palettes.next(_settings.value.theme))

    /** Moves a package to the other class; the person's choice replaces the default. */
    fun setReflex(pkg: String, reflex: Boolean) {
        val r = HashSet(_settings.value.reflex); val t = HashSet(_settings.value.tools)
        if (reflex) { r += pkg; t -= pkg } else { t += pkg; r -= pkg }
        sp.edit().putStringSet("reflex", r).putStringSet("tools", t).apply()
    }

    fun isReflex(pkg: String, category: Int): Boolean = Classify.isReflex(pkg, category, _settings.value.reflex, _settings.value.tools)

    // ---- the answers, kept for nine days ----

    fun recordAnswer(pkg: String, t: Long, index: Int, text: String? = null) {
        val e = answersSp.edit().putString("$t|$pkg", if (text != null) "t:$text" else "i:$index")
        val old = t - 9L * 24 * 3600_000
        answersSp.all.keys.forEach { k -> if ((k.substringBefore('|').toLongOrNull() ?: 0L) < old) e.remove(k) }
        e.apply()
    }

    fun answers(from: Long, to: Long): List<Answer> = answersSp.all.mapNotNull { (k, v) ->
        val t = k.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
        val raw = v as? String ?: return@mapNotNull null
        if (t < from || t >= to) null
        else if (raw.startsWith("t:")) Answer(k.substringAfter('|'), t, -1, raw.substring(2))
        else Answer(k.substringAfter('|'), t, raw.substringAfter(':').toIntOrNull() ?: -1, null)
    }.sortedBy { it.t }
}
