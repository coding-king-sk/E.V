package com.ev.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.ev.android.feature.launcher.LauncherScreen
import com.ev.android.feature.permissions.AppPermissions
import com.ev.android.ui.theme.EVTheme

class MainActivity : ComponentActivity() {

    /**
     * Result se hume kuch karna nahi hai \u2014 har screen apna status khud padhti
     * hai. Bas dialog dikhana maqsad hai.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pehle chaar step wala onboarding tha; ab app seedha khulti hai aur jo
        // permissions baaki hain wo ek saath maang leti hai. Jo pehle se mil
        // chuki hain unka dialog nahi aata.
        //
        // Note: agar user pehle "Don't ask again" daba chuka hai to Android
        // dialog dikhata hi nahi. Isliye Settings me poori list hai, jahan se
        // app settings khol ke permission di ja sakti hai.
        val missing = AppPermissions.missing(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            EVTheme {
                LauncherScreen()
            }
        }
    }
}
