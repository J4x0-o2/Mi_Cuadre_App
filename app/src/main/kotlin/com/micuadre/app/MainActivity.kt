package com.micuadre.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.micuadre.app.presentation.LaunchRouting
import com.micuadre.app.presentation.MainViewModel
import com.micuadre.app.presentation.screens.HomeScreen
import com.micuadre.app.ui.theme.MiCuadreTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process restore) must not replay the launch intent.
        if (savedInstanceState == null) handleLaunchIntent(intent)
        setContent {
            MiCuadreTheme {
                HomeScreen(viewModel = viewModel)
            }
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
}
