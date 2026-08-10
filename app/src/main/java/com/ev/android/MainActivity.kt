package com.ev.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ev.android.feature.launcher.LauncherScreen
import com.ev.android.feature.onboarding.Onboarding
import com.ev.android.feature.onboarding.OnboardingScreen
import com.ev.android.ui.theme.EVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val needsSetup = !Onboarding.isDone(this)

        setContent {
            EVTheme {
                var showOnboarding by remember { mutableStateOf(needsSetup) }

                if (showOnboarding) {
                    OnboardingScreen(onFinished = { showOnboarding = false })
                } else {
                    LauncherScreen()
                }
            }
        }
    }
}
