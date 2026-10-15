package com.freedomfighter.readersattention

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Intent
import com.freedomfighter.readersattention.ui.AppDayScreen
import com.freedomfighter.readersattention.ui.DeliveredScreen
import com.freedomfighter.readersattention.ui.OPEN_SETTINGS
import com.freedomfighter.readersattention.ui.SlotsScreen
import com.freedomfighter.readersattention.ui.HomeScreen
import com.freedomfighter.readersattention.ui.LocalColors
import com.freedomfighter.readersattention.ui.Nav
import com.freedomfighter.readersattention.ui.ReaderTheme
import com.freedomfighter.readersattention.ui.Screen
import com.freedomfighter.readersattention.ui.SettingsScreen
import com.freedomfighter.readersattention.ui.WeekScreen
import com.freedomfighter.readersattention.widget.AttentionWidget

class MainActivity : ComponentActivity() {
    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as App
        if (intent?.getBooleanExtra(OPEN_SETTINGS, false) == true) nav.push(Screen.Settings)
        setContent {
            val settings by app.prefs.settings.collectAsState()
            ReaderTheme(settings) {
                Bars()
                when (val s = nav.current) {
                    Screen.Home -> HomeScreen(nav, app)
                    Screen.Week -> WeekScreen(nav, app)
                    is Screen.AppDay -> AppDayScreen(nav, app, s.pkg)
                    Screen.Settings -> SettingsScreen(nav, app)
                    Screen.Slots -> SlotsScreen(nav, app)
                    Screen.Delivered -> DeliveredScreen(nav, app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OPEN_SETTINGS, false) && nav.current != Screen.Settings) nav.push(Screen.Settings)
    }

    override fun onResume() {
        super.onResume()
        // the permission or the day may have changed meanwhile
        (application as App).version.value++
    }

    override fun onPause() {
        super.onPause()
        // the widget shows what the app has just shown
        AttentionWidget.refresh(this)
    }

    @Composable
    private fun Bars() {
        val view = LocalView.current
        val colors = LocalColors.current
        LaunchedEffect(colors.isDark) {
            val c = WindowInsetsControllerCompat(window, view)
            c.isAppearanceLightStatusBars = !colors.isDark
            c.isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}
