package com.micuadre.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.micuadre.app.presentation.screens.HomeScreen
import com.micuadre.app.ui.theme.MiCuadreTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MiCuadreTheme {
                HomeScreen()
            }
        }
    }
}
