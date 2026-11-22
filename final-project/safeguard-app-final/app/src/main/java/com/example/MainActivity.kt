package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.PrefsManager
import com.example.manager.VestTelemetryManager
import com.example.model.WorkerProfile
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScaffold
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.SafeGuardTheme

enum class AppNavState {
    SPLASH,
    LOGIN,
    MAIN
}

class MainActivity : ComponentActivity() {

    private var activeActionMode: ActionMode? = null

    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)
        activeActionMode = mode
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        super.onActionModeFinished(mode)
        if (activeActionMode == mode) {
            activeActionMode = null
        }
    }

    fun dismissActiveActionMode() {
        try {
            activeActionMode?.finish()
        } catch (_: Throwable) {}
        activeActionMode = null
    }

    override fun onResume() {
        super.onResume()
        // some phones drop back to 60 Hz after the app was in the background - ask again
        requestHighRefreshRate()
    }

    /**
     * Ask the system for the display's highest refresh rate (120 Hz on realme GT Neo 2, Honor 200,
     * Redmi Note 15 ...). Picks the fastest display mode that keeps the current resolution, and also
     * sets preferredRefreshRate for devices that only look at that field.
     */
    private fun requestHighRefreshRate() {
        try {
            val attrs = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
                val cur = disp?.mode
                val best = disp?.supportedModes
                    ?.filter { cur == null || (it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight) }
                    ?.maxByOrNull { it.refreshRate }
                if (best != null) {
                    attrs.preferredDisplayModeId = best.modeId
                    attrs.preferredRefreshRate = best.refreshRate
                }
            }
            window.attributes = attrs
        } catch (_: Throwable) {
        }
    }

    override fun onPause() {
        super.onPause()
        dismissActiveActionMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()

        // Initialize telemetry manager and Firebase watchdog
        VestTelemetryManager.init(applicationContext)
        com.example.manager.HelmetTelemetryManager.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val prefs = remember { PrefsManager.getInstance(context) }
            var isDarkTheme by remember { mutableStateOf(prefs.isDarkMode) }
            var appNavState by remember { mutableStateOf(AppNavState.SPLASH) }

            // Profile identity (Static metadata as requested)
            val workerProfile = remember {
                WorkerProfile(
                    workerId = "WKR-01",
                    workerName = "Tariqul Islam",
                    batchId = "BATCH-LORA-92",
                    department = "Hazardous Drilling & Refinery",
                    loraNode = "worker1",
                    loraFrequency = "915.0 MHz"
                )
            }

            // Runtime Permissions Request (Notifications on Android 13+, Fine/Coarse Location for Map Tracking)
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Permissions acknowledged */ }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(perms.toTypedArray())
            }

            SafeGuardTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AnimatedContent(
                        targetState = appNavState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "AppNavigation"
                    ) { targetState ->
                        when (targetState) {
                            AppNavState.SPLASH -> {
                                SplashScreen(
                                    onNavigate = { loggedIn ->
                                        dismissActiveActionMode()
                                        appNavState = if (loggedIn) AppNavState.MAIN else AppNavState.LOGIN
                                    }
                                )
                            }
                            AppNavState.LOGIN -> {
                                LoginScreen(
                                    onLoginSuccess = {
                                        dismissActiveActionMode()
                                        appNavState = AppNavState.MAIN
                                    }
                                )
                            }
                            AppNavState.MAIN -> {
                                MainScaffold(
                                    profile = workerProfile,
                                    onThemeToggle = { dark ->
                                        isDarkTheme = dark
                                    },
                                    onLogout = {
                                        dismissActiveActionMode()
                                        appNavState = AppNavState.LOGIN
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
