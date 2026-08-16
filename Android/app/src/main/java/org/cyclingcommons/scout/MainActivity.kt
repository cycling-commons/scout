package org.cyclingcommons.scout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.ui.HelpScreen
import org.cyclingcommons.scout.ui.IntroScreen
import org.cyclingcommons.scout.ui.PairRadarScreen
import org.cyclingcommons.scout.ui.RecoveryScreen
import org.cyclingcommons.scout.ui.ScoutRideScreen
import org.cyclingcommons.scout.ui.SettingsScreen
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutTheme
import org.cyclingcommons.scout.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    private val rideVm: RideViewModel by viewModels()

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            rideVm.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        paintColdStart()
        setContent {
            val model by rideVm.ui.collectAsStateWithLifecycle()
            val dark = model.themeMode.isDark()
            ScoutTheme(dark = dark) {
                SystemBarIcons(dark = dark)
                KeepScreenOn(
                    hold = model.keepScreenOn && model.scout.timer == TimerState.RUNNING,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScoutColors.Screen)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    Crossfade(targetState = model.screen, label = "screen") { screen ->
                        when (screen) {
                            Screen.INTRO -> IntroScreen(
                                onContinue = {
                                    rideVm.dismissIntro()
                                    requestNeededPermissions()
                                },
                                onOpenLink = ::openLink,
                            )
                            Screen.RECOVERY -> {
                                val prompt = model.recovery
                                if (prompt != null) {
                                    RecoveryScreen(
                                        prompt = prompt,
                                        onResume = {
                                            requestNeededPermissions()
                                            rideVm.resumeRecoveredRide()
                                        },
                                        onDiscard = rideVm::discardRecovery,
                                    )
                                }
                            }
                            Screen.PAIR_RADAR -> PairRadarScreen(
                                status = model.radar,
                                onTransport = rideVm::setTransport,
                                onStartBleScan = {
                                    requestNeededPermissions()
                                    rideVm.startRadarScan()
                                },
                                onStopBleScan = rideVm::stopRadarScan,
                                onStartAntSearch = rideVm::startAntSearch,
                                onSelect = rideVm::selectRadar,
                                onForget = rideVm::forgetRadar,
                                onBack = rideVm::closePairRadar,
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                imperial = model.imperial,
                                keepScreenOn = model.keepScreenOn,
                                themeMode = model.themeMode,
                                radarLabel = getString(
                                    R.string.settings_radar_preferred,
                                    model.radar.savedName
                                        ?: model.radar.savedAddress
                                        ?: getString(R.string.radar_none),
                                    model.radar.transport.name.lowercase(),
                                ),
                                rides = model.rides,
                                onImperial = rideVm::setImperial,
                                onKeepScreenOn = rideVm::setKeepScreenOn,
                                onThemeMode = rideVm::setThemeMode,
                                onPairRadar = {
                                    requestNeededPermissions()
                                    rideVm.openPairRadar()
                                },
                                onHelp = { rideVm.openHelp() },
                                onReplayIntro = rideVm::replayIntro,
                                onShareRide = { ride -> share(rideVm.shareRide(ride)) },
                                onDeleteRide = rideVm::deleteRide,
                                onBack = rideVm::closeSettings,
                            )
                            Screen.HELP -> HelpScreen(
                                onBack = rideVm::closeHelp,
                                onOpenLink = ::openLink,
                            )
                            Screen.RIDE -> ScoutRideScreen(
                                model = model,
                                onStart = {
                                    requestNeededPermissions()
                                    rideVm.startRide()
                                },
                                onPause = rideVm::pauseRide,
                                onResume = {
                                    requestNeededPermissions()
                                    rideVm.resumeRide()
                                },
                                onStop = rideVm::stopRide,
                                onTileTap = rideVm::onTileTap,
                                onEndOpenSurface = rideVm::endOpenSurface,
                                onRetryRadar = rideVm::retryRadar,
                                onShareFit = { share(rideVm.shareLastFit()) },
                                onSettings = rideVm::openSettings,
                                onHelp = { rideVm.openHelp(Screen.RIDE) },
                                onDismissMessage = rideVm::clearUserMessage,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        rideVm.setUiVisible(true)
    }

    override fun onResume() {
        super.onResume()
        rideVm.refreshPermissions()
    }

    override fun onStop() {
        rideVm.setUiVisible(false)
        super.onStop()
    }

    /**
     * The window is drawn before the first composition, and its theme background can
     * only follow the *system* night mode. Repaint it here so a rider who overrode the
     * appearance does not get a flash of the other one on every cold start.
     */
    private fun paintColdStart() {
        val dark = when (AppPrefs(this).themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> return
        }
        val background = if (dark) {
            R.color.screen_background_dark
        } else {
            R.color.screen_background_light
        }
        window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, background)))
    }

    /** Status and navigation glyphs have to invert with the page under them. */
    @Composable
    private fun SystemBarIcons(dark: Boolean) {
        val view = LocalView.current
        LaunchedEffect(dark) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    @Composable
    private fun KeepScreenOn(hold: Boolean) {
        LaunchedEffect(hold) {
            if (hold) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun share(intent: Intent?) {
        intent?.let { startActivity(Intent.createChooser(it, getString(R.string.settings_share))) }
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }
        if (needed.isEmpty()) {
            rideVm.refreshPermissions()
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
