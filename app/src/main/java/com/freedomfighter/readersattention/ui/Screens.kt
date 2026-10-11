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
import com.freedomfighter.readersattention.data.PAUSE_FIRM
import com.freedomfighter.readersattention.data.PAUSE_LIGHT
import com.freedomfighter.readersattention.data.PAUSE_OFF
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
}

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

// ---------------------------------------------------------------------------------------------
// Home: today, in words.
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val colors = LocalColors.current
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
                    if (settings.pauseLevel == PAUSE_OFF) {
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
            MenuItem(if (colors.isDark) stringResource(R.string.theme_light) else stringResource(R.string.theme_dark)) { app.prefs.toggleTheme(colors.isDark) },
            MenuItem(stringResource(R.string.settings)) { nav.push(Screen.Settings) }
        ))
        appMenu?.let { a ->
            val reflex = today?.reflex?.any { it.pkg == a.pkg } == true
            TextMenu(Usage.name(context, a.pkg), listOf(
                MenuItem(stringResource(if (reflex) R.string.count_tool else R.string.count_reflex)) { app.prefs.setReflex(a.pkg, !reflex) },
                MenuItem(stringResource(R.string.see_day)) { nav.push(Screen.AppDay(a.pkg)) }
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
    val s by app.prefs.settings.collectAsState()
    val version by app.version.collectAsState()
    val colors = LocalColors.current
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
                TextRow(stringResource(when (s.pauseLevel) { PAUSE_LIGHT -> R.string.pause_light; PAUSE_FIRM -> R.string.pause_firm; else -> R.string.off }), secondary = stringResource(R.string.pause_title)) {
                    app.prefs.setPauseLevel(when (s.pauseLevel) { PAUSE_OFF -> PAUSE_LIGHT; PAUSE_LIGHT -> PAUSE_FIRM; else -> PAUSE_OFF })
                }
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
                // ---- the two permissions, each row opening its system page ----
                TextRow(stringResource(if (usage) R.string.granted else R.string.not_granted), secondary = stringResource(R.string.usage_access)) { openUsageAccess(context) }
                TextRow(stringResource(if (access) R.string.on else R.string.off), secondary = stringResource(R.string.accessibility)) { openAccessibility(context) }
                Small(stringResource(R.string.accessibility_hint), Modifier.padding(horizontal = rowPadH).padding(bottom = 10.dp), maxLines = 6)
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(if (colors.isDark) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light), secondary = stringResource(R.string.colours)) { app.prefs.toggleTheme(colors.isDark) }
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
