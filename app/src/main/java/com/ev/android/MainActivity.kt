package com.ev.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ev.android.feature.launcher.LauncherScreen
import com.ev.android.ui.theme.EVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EVTheme {
                LauncherScreen()
            }
        }
    }
}
