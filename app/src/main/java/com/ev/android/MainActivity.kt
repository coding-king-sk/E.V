package com.ev.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.ev.android.feature.bubble.Bubble
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

        // Back dabane pe app band nahi hoti, bas peeche chali jati hai.
        //
        // E.V assistant hai, ek baar ka kaam nahi. Poori app band kar dene se
        // hands-free listener aur chalu kaam sab mar jate the aur agli baar
        // sab dobara shuru hota tha. Ab wahi hota hai jo home dabane pe hota
        // hai \u2014 app zinda, bas peeche.
        //
        // Compose ke andar khule panel apna back khud lete hain (BackHandler),
        // isliye ye callback tabhi chalta hai jab wahan koi panel khula na ho.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )

        // Floating bubble se aaye ho to seedha kaam pe lag jao \u2014 mic khol do
        // ya jo command bheji hai wo chala do.
        val listen = intent?.getBooleanExtra(Bubble.EXTRA_LISTEN, false) ?: false
        val command = intent?.getStringExtra(Bubble.EXTRA_COMMAND)

        setContent {
            EVTheme {
                LauncherScreen(autoListen = listen, autoCommand = command)
            }
        }
    }

    /**
     * App pehle se khuli ho aur bubble se dobara aaye.
     *
     * SINGLE_TOP ki wajah se `onCreate` dobara nahi chalta, isliye naya intent
     * yahan aata hai. Sabse saaf tareeka hai screen ko dobara banana \u2014 tabhi
     * naya extra padha jayega.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val hasWork = intent.getBooleanExtra(Bubble.EXTRA_LISTEN, false) ||
            intent.getStringExtra(Bubble.EXTRA_COMMAND) != null
        if (hasWork) recreate()
    }
}
