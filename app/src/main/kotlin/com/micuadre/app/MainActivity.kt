package com.micuadre.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.domain.model.ThemeSchedule
import com.micuadre.app.presentation.LaunchRouting
import com.micuadre.app.presentation.MainViewModel
import com.micuadre.app.presentation.screens.HomeScreen
import com.micuadre.app.presentation.screens.PlatformsScreen
import com.micuadre.app.presentation.screens.SettingsScreen
import com.micuadre.app.presentation.theme.ThemeViewModel
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.ui.theme.colorSchemeFor
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId

private enum class AppScreen { Home, Settings, Platforms }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process restore) must not replay the launch intent.
        if (savedInstanceState == null) handleLaunchIntent(intent)
        setContent {
            val themeState by themeViewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            // Until the stored mode is read there is nothing to draw: the window background
            // already follows the system theme, so this is not a visible flash.
            themeState?.let { current ->
                val darkTheme = ThemeSchedule.isDarkAt(
                    current.mode, systemDark, current.nowMs, ZoneId.systemDefault()
                )
                ApplyWindowTheme(darkTheme)
                MiCuadreTheme(darkTheme = darkTheme) {
                    // Three fixed screens in a chain: one saved value is enough, no NavHost needed.
                    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
                    when (screen) {
                        AppScreen.Home ->
                            HomeScreen(onOpenSettings = { screen = AppScreen.Settings }, viewModel = viewModel)
                        AppScreen.Settings -> SettingsScreen(
                            onBack = { screen = AppScreen.Home },
                            onOpenPlatforms = { screen = AppScreen.Platforms }
                        )
                        AppScreen.Platforms -> PlatformsScreen(onBack = { screen = AppScreen.Settings })
                    }
                }
            }
        }
    }

    /** Keeps system bar icons and the window background in sync with a forced dark/light mode. */
    @Composable
    private fun ApplyWindowTheme(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM) { darkTheme }
            )
            window.setBackgroundDrawable(
                ColorDrawable(colorSchemeFor(darkTheme).background.toArgb())
            )
            onDispose { }
        }
    }

    // singleTop: a notification tap while the app is open arrives here instead of onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val request = LaunchRouting.requestFor(intent?.action) ?: return
        // Consume the action so the same intent is never handled twice.
        intent?.action = null
        viewModel.onLaunchRequest(request)
    }

    private companion object {
        // Same scrims enableEdgeToEdge() uses by default for the 3-button navigation bar.
        val NAV_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val NAV_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
