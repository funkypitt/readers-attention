package com.freedomfighter.readersattention.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freedomfighter.readersattention.AndroidRes
import com.freedomfighter.readersattention.App
import com.freedomfighter.readersattention.R
import com.freedomfighter.readersattention.Words
import com.freedomfighter.readersattention.data.AppDay
import com.freedomfighter.readersattention.data.Day
import com.freedomfighter.readersattention.data.Ev
import com.freedomfighter.readersattention.data.FontChoice
import com.freedomfighter.readersattention.data.Batch
import com.freedomfighter.readersattention.data.PAUSE_BREATH
import com.freedomfighter.readersattention.data.PAUSE_FIRM
import com.freedomfighter.readersattention.data.PAUSE_LIGHT
import com.freedomfighter.readersattention.data.PAUSE_OFF
import com.freedomfighter.readersattention.data.Palettes
import com.freedomfighter.readersattention.service.BatchService
import com.freedomfighter.readersattention.data.Sessions
import com.freedomfighter.readersattention.data.TextSize
import com.freedomfighter.readersattention.data.Usage
import com.freedomfighter.readersattention.service.WatchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed class Screen {
    data object Home : Screen()
    data object Week : Screen()
    data class AppDay(val pkg: String) : Screen()
    data object Settings : Screen()
    data object Slots : Screen()
    data object Delivered : Screen()
}

/** Intent extra: open on the settings (the quick-settings tile, when notification access is missing). */
const val OPEN_SETTINGS = "open_settings"

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
}

/** The days, today first; null until read, empty when usage access is missing. */
@Composable
fun rememberDays(app: App): List<Day>? {
    val context = LocalContext.current
    val version by app.version.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    var raw by remember { mutableStateOf<Pair<List<Ev>, List<Pair<Long, Long>>>?>(null) }
    LaunchedEffect(version) {
        raw = if (Usage.allowed(context)) withContext(Dispatchers.IO) { App.events(context, App.DAYS) } else emptyList<Ev>() to emptyList()
    }
    return remember(raw, settings.reflex, settings.tools, settings.eveningMinutes) {
        raw?.let { App.days(context, it.first, it.second, settings) }
    }
}

@Composable
fun rememberRes() = LocalContext.current.let { c -> remember(c) { AndroidRes(c) } }

fun openUsageAccess(context: android.content.Context) =
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun openAccessibility(context: android.content.Context) =
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun openNotificationAccess(context: android.content.Context) =
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** The name of a pause level. */
@Composable
fun pauseName(level: Int): String = stringResource(when (level) {
    PAUSE_LIGHT -> R.string.pause_light; PAUSE_FIRM -> R.string.pause_firm; PAUSE_BREATH -> R.string.pause_breath; else -> R.string.off
})

fun nextPause(level: Int): Int = when (level) { PAUSE_OFF -> PAUSE_LIGHT; PAUSE_LIGHT -> PAUSE_FIRM; PAUSE_FIRM -> PAUSE_BREATH; else -> PAUSE_OFF }

// ---------------------------------------------------------------------------------------------
// Home: today, in words.
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val res = rememberRes()
    val settings by app.prefs.settings.collectAsState()
    val version by app.version.collectAsState()
    val allowed = remember(version) { Usage.allowed(context) }
    val days = rememberDays(app)
    val today = days?.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf<AppDay?>(null) }
    var toolsOpen by remember { mutableStateOf(false) }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.app_title), onBack = null, trailing = "⋯", onTrailing = { menu = true })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (!allowed) Setup()
                else if (today != null) {
                    // ---- the reflex line, the screen under it ----
                    Column(Modifier.fillMaxWidth().padding(horizontal = rowPadH).padding(top = 22.dp, bottom = 12.dp)) {
                        Small(stringResource(R.string.reflex), maxLines = 1)
                        T(Words.reflexLine(today, res), size = typo.tile * 1.3f, maxLines = 2)
                        Small(Words.screenLine(today, res), Modifier.padding(top = 4.dp), maxLines = 2)
                        // the notifications held for the next delivery time, while that is on
                        remember(version, settings.batching, settings.slots) { BatchService.nextLine(context, settings) }?.let { Small(it, Modifier.padding(top = 4.dp), maxLines = 1) }
                    }
                    // ---- the habit, named ----
                    val sentences = Words.sentences(today, res, { Usage.name(context, it) }, settings.eveningMinutes)
                    sentences.forEach { T(it, Modifier.padding(horizontal = rowPadH, vertical = 8.dp), size = typo.title, maxLines = 4) }
                    if (sentences.isNotEmpty()) VSpace(8.dp)
                    Rule()
                    // ---- the reflex apps ----
                    if (today.reflex.isEmpty()) Small(stringResource(R.string.no_reflex), Modifier.padding(horizontal = rowPadH, vertical = 14.dp), maxLines = 3)
                    today.reflex.forEach { a -> TextRow(Usage.name(context, a.pkg), secondary = Words.appLine(a, res)) { tick(); appMenu = a } }
                    Rule()
                    // ---- the tools, folded ----
                    TextRow(stringResource(R.string.tools), secondary = Words.toolsLine(today, res)) { tick(); toolsOpen = !toolsOpen }
                    if (toolsOpen) {
                        today.tools.forEach { a -> TextRow(Usage.name(context, a.pkg), secondary = Words.appLine(a, res), size = typo.title) { tick(); appMenu = a } }
                        if (today.tools.isEmpty()) Small(stringResource(R.string.no_tools), Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 3)
                    }
                    // the numbers alone change little: one row towards the pause, until a pause is on
                    if (!settings.anyPause) {
                        Rule()
                        T(stringResource(R.string.nudge), Modifier.fillMaxWidth().noRippleClickable { nav.push(Screen.Settings) }.padding(horizontal = rowPadH, vertical = 14.dp), size = typo.title, maxLines = 4)
                    }
                    VSpace(12.dp)
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (menu) TextMenu(null, listOf(
            MenuItem(stringResource(R.string.week)) { nav.push(Screen.Week) }
        ), onDismiss = { menu = false }, footer = listOf(
            MenuItem(Words.themeName(Palettes.next(settings.theme), res)) { app.prefs.nextTheme() },
            MenuItem(stringResource(R.string.settings)) { nav.push(Screen.Settings) }
        ))
        appMenu?.let { a ->
            val reflex = today?.reflex?.any { it.pkg == a.pkg } == true
            val own = settings.pauseLevels[a.pkg]
            TextMenu(Usage.name(context, a.pkg), listOfNotNull(
                MenuItem(stringResource(if (reflex) R.string.count_tool else R.string.count_reflex)) { app.prefs.setReflex(a.pkg, !reflex) },
                MenuItem(stringResource(R.string.see_day)) { nav.push(Screen.AppDay(a.pkg)) },
                // the pause for this app alone: as set for all, then each level, then back
                if (reflex) MenuItem(if (own == null) stringResource(R.string.pause_as_all) else pauseName(own), secondary = stringResource(R.string.pause_this_app)) {
                    app.prefs.setPauseFor(a.pkg, when (own) { null -> PAUSE_OFF; PAUSE_OFF -> PAUSE_LIGHT; PAUSE_LIGHT -> PAUSE_FIRM; PAUSE_FIRM -> PAUSE_BREATH; else -> null })
                } else null
            ), onDismiss = { appMenu = null })
        }
    }
}

/** Shown in place of the day until usage access is given. */
@Composable
fun Setup() {
    val context = LocalContext.current
    val typo = LocalTypo.current
    Column {
        T(stringResource(R.string.setup_title), Modifier.padding(horizontal = rowPadH).padding(top = 22.dp, bottom = 8.dp), size = typo.tile * 1.3f, align = TextAlign.Start)
        Small(stringResource(R.string.setup_text), Modifier.padding(horizontal = rowPadH).padding(bottom = 16.dp), maxLines = 14, align = TextAlign.Start)
        Rule()
        TextRow(stringResource(R.string.allow_usage), secondary = stringResource(R.string.allow_usage_hint), size = typo.title) { openUsageAccess(context) }
        Rule()
    }
}

// ---------------------------------------------------------------------------------------------
// Week: one row per day, the average of the previous days under the title.
// ---------------------------------------------------------------------------------------------

@Composable
fun WeekScreen(nav: Nav, app: App) {
    val res = rememberRes()
    val typo = LocalTypo.current
    val days = rememberDays(app)
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.week), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (days != null) {
                    val avg = Words.weekAverage(Sessions.average(days.drop(1)), res) ?: stringResource(R.string.week_none)
                    Small(avg, Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 6.dp), maxLines = 2)
                    val today = LocalDate.now()
                    days.forEach { d -> TextRow(Words.weekRow(d, today, res), size = typo.title) }
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One app's day: its openings, with the answer given at the pause when there was one.
// ---------------------------------------------------------------------------------------------

@Composable
fun AppDayScreen(nav: Nav, app: App, pkg: String) {
    val context = LocalContext.current
    val res = rememberRes()
    val typo = LocalTypo.current
    val days = rememberDays(app)
    val today = days?.firstOrNull()
    val appDay = today?.let { d -> (d.reflex + d.tools).firstOrNull { it.pkg == pkg } }
    val answers = remember(today) { today?.let { app.prefs.answers(it.start, it.end).filter { a -> a.pkg == pkg } } ?: emptyList() }
    val answerWords = Words.answerIds.map { stringResource(it) }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(Usage.name(context, pkg), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (today != null) {
                    Small(if (appDay == null) stringResource(R.string.no_openings) else stringResource(R.string.pair, stringResource(R.string.today), Words.appLine(appDay, res)),
                        Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 6.dp), maxLines = 2)
                    appDay?.sessions?.forEach { s ->
                        // the answer given at the start of this opening, if the pause was on
                        val a = answers.firstOrNull { it.t >= s.start - 5_000 && it.t <= minOf(s.end, s.start + 120_000) }
                        TextRow(Words.sessionLine(s, a?.let { it.text ?: answerWords.getOrNull(it.index) }, res), size = typo.title)
                    }
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: the permissions, the interventions, the look
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val res = rememberRes()
    val s by app.prefs.settings.collectAsState()
    val version by app.version.collectAsState()
    val typo = LocalTypo.current
    val usage = remember(version) { Usage.allowed(context) }
    val access = remember(version) { WatchService.enabled(context) }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.settings), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VSpace(10.dp)
                // ---- what the app may do, all off by default ----
                TextRow(pauseName(s.pauseLevel), secondary = stringResource(R.string.pause_title)) { app.prefs.setPauseLevel(nextPause(s.pauseLevel)) }
                Small(stringResource(R.string.pause_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 10)
                TextRow(if (s.wordMinutes == 0) stringResource(R.string.off) else stringResource(R.string.word_after, s.wordMinutes), secondary = stringResource(R.string.word_title)) {
                    app.prefs.setWordMinutes(when (s.wordMinutes) { 0 -> 10; 10 -> 15; 15 -> 20; 20 -> 30; else -> 0 })
                }
                Small(stringResource(R.string.word_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 6)
                TextRow(Words.minutesWord(s.eveningMinutes), secondary = stringResource(R.string.evening_title)) {
                    app.prefs.setEveningMinutes(when (s.eveningMinutes) { 20 * 60 -> 21 * 60; 21 * 60 -> 22 * 60; 22 * 60 -> 23 * 60; 23 * 60 -> 0; else -> 20 * 60 })
                }
                Small(stringResource(R.string.evening_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 6)
                Rule(Modifier.padding(vertical = 8.dp))
                // ---- notifications delivered together ----
                val listener = remember(version) { BatchService.enabled(context) }
                TextRow(stringResource(if (s.batching) R.string.on else R.string.off), secondary = stringResource(R.string.batch_title)) {
                    if (!s.batching && !listener) openNotificationAccess(context)
                    BatchService.set(context, !s.batching)
                }
                Small(stringResource(R.string.batch_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 12)
                TextRow(s.slots.joinToString(" · ") { Words.minutesWord(it) }, secondary = stringResource(R.string.slots_title)) { nav.push(Screen.Slots) }
                TextRow(if (s.delivered.isEmpty()) stringResource(R.string.none) else s.delivered.map { Usage.name(context, it) }.sorted().joinToString(" · "), secondary = stringResource(R.string.delivered_title)) { nav.push(Screen.Delivered) }
                TextRow(stringResource(if (listener) R.string.granted else R.string.not_granted), secondary = stringResource(R.string.notif_access)) { openNotificationAccess(context) }
                Small(stringResource(R.string.notif_access_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 8)
                Rule(Modifier.padding(vertical = 8.dp))
                // ---- the two permissions, each row opening its system page ----
                TextRow(stringResource(if (usage) R.string.granted else R.string.not_granted), secondary = stringResource(R.string.usage_access)) { openUsageAccess(context) }
                TextRow(stringResource(if (access) R.string.on else R.string.off), secondary = stringResource(R.string.accessibility)) { openAccessibility(context) }
                Small(stringResource(R.string.accessibility_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 6)
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(Words.themeName(s.theme, res), secondary = stringResource(R.string.colours)) { app.prefs.nextTheme() }
                TextRow(when (s.textSize) { TextSize.SMALL -> stringResource(R.string.size_small); TextSize.MEDIUM -> stringResource(R.string.size_medium); TextSize.LARGE -> stringResource(R.string.size_large) }, secondary = stringResource(R.string.text_size)) {
                    app.prefs.setTextSize(when (s.textSize) { TextSize.SMALL -> TextSize.MEDIUM; TextSize.MEDIUM -> TextSize.LARGE; TextSize.LARGE -> TextSize.SMALL })
                }
                TextRow(when (s.font) { FontChoice.SANS -> stringResource(R.string.font_sans); FontChoice.SERIF -> stringResource(R.string.font_serif); FontChoice.MONO -> stringResource(R.string.font_mono) }, secondary = stringResource(R.string.font)) {
                    app.prefs.setFont(when (s.font) { FontChoice.SANS -> FontChoice.SERIF; FontChoice.SERIF -> FontChoice.MONO; FontChoice.MONO -> FontChoice.SANS })
                }
                TextRow(if (s.haptics) stringResource(R.string.on) else stringResource(R.string.off), secondary = stringResource(R.string.haptics)) { app.prefs.setHaptics(!s.haptics) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.app_name), secondary = stringResource(R.string.about)) { }
                TextRow(stringResource(R.string.credits)) { }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The delivery times: one row each, a tap to change or remove, a row to add one.
// ---------------------------------------------------------------------------------------------

@Composable
fun SlotsScreen(nav: Nav, app: App) {
    val s by app.prefs.settings.collectAsState()
    val typo = LocalTypo.current
    var editing by remember { mutableStateOf<Int?>(null) } // the slot under the menu
    var prompt by remember { mutableStateOf<Int?>(null) } // -1 = a new one, else the slot replaced
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.slots_title), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.slots_hint), Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 6.dp), maxLines = 6)
                s.slots.forEach { m -> TextRow(Words.minutesWord(m)) { editing = m } }
                Rule()
                TextRow(stringResource(R.string.add_time), size = typo.title) { prompt = -1 }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        editing?.let { m ->
            TextMenu(Words.minutesWord(m), listOfNotNull(
                MenuItem(stringResource(R.string.change_time)) { prompt = m },
                if (s.slots.size > 1) MenuItem(stringResource(R.string.remove_time)) { app.prefs.setSlots(s.slots - m) } else null
            ), onDismiss = { editing = null })
        }
        prompt?.let { old ->
            TextPrompt(stringResource(R.string.time_prompt), initial = if (old >= 0) Words.minutesWord(old) else "", selectAll = old >= 0,
                keyboard = androidx.compose.ui.text.input.KeyboardType.Number,
                onDone = { text ->
                    Batch.parseTime(text)?.let { m -> app.prefs.setSlots((if (old >= 0) s.slots - old else s.slots) + m) }
                    prompt = null
                }, onCancel = { prompt = null })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The apps whose notifications never wait: the marked ones first, then all the others.
// ---------------------------------------------------------------------------------------------

@Composable
fun DeliveredScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val s by app.prefs.settings.collectAsState()
    val typo = LocalTypo.current
    val all = remember { Usage.launchable(context).sortedBy { Usage.name(context, it) } }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.delivered_title), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.delivered_hint), Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 6.dp), maxLines = 6)
                val marked = all.filter { it in s.delivered }
                marked.forEach { p -> TextRow(Usage.name(context, p), inverted = true, size = typo.title) { app.prefs.setDelivered(p, false) } }
                if (marked.isNotEmpty()) Rule()
                all.filter { it !in s.delivered }.forEach { p -> TextRow(Usage.name(context, p), size = typo.title) { app.prefs.setDelivered(p, true) } }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
