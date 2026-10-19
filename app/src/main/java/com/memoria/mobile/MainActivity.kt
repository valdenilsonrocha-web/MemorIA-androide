package com.memoria.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import com.memoria.mobile.ui.common.MemoriaToastHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.ApiResult
import androidx.lifecycle.lifecycleScope
import com.memoria.mobile.reminders.DeviceReporter
import com.memoria.mobile.reminders.MemoriaNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.memoria.mobile.ui.auth.AppLoadingScreen
import com.memoria.mobile.ui.common.repository
import com.memoria.mobile.ui.nav.MemoriaNav
import com.memoria.mobile.ui.theme.MemoriaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MemoriaNotifications.ensureChannels(this)
        setContent {
            // Collected here rather than inside a screen so changing the palette in
            // Settings repaints the whole app immediately, bottom bar included.
            val preset by (application as MemoriaApp).graph.repository.themePresetFlow
                .collectAsStateWithLifecycle(initialValue = null)
            // The web has no dark mode — the same light page whatever the phone's
            // system setting — so the clone stays light too.
            MemoriaTheme(darkTheme = false, themePreset = preset) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        AppEntry()
                        // `#toastContainer`: above every page, the header included.
                        MemoriaToastHost(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Android's settings is when a permission most often
        // changed, so the admin panel learns it on the spot.
        val app = application as MemoriaApp
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if (!app.graph.repository.isLoggedIn()) app.graph.repository.bootstrap()
                DeviceReporter.report(app)
            }
        }
    }
}

@Composable
private fun AppEntry() {
    val context = LocalContext.current
    val repo = context.repository()
    var ready by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // A corrupt preferences file must not strand the app on this spinner:
        // the login screen can now repair the server address by itself.
        runCatching { repo.bootstrap() }
        loggedIn = repo.isLoggedIn()
        // Seamless auto-login for elderly users: if token expired or missing, but
        // credentials are saved in Keystore, log in automatically in background.
        if (!loggedIn && repo.credentials.rememberEnabled() && repo.credentials.hasSaved()) {
            runCatching {
                val autoResult = repo.loginWithSavedCredentials()
                if (autoResult is ApiResult.Ok) {
                    loggedIn = true
                }
            }
        }
        ready = true
    }

    if (!ready) {
        // The web's `.app-loading-screen`: brand gradient + floating logo.
        AppLoadingScreen()
    } else {
        MemoriaNav(startLoggedIn = loggedIn)
    }
}
