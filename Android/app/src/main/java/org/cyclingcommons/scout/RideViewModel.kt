package org.cyclingcommons.scout

import android.app.Application
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.cyclingcommons.scout.a11y.isTalkBackActive
import org.cyclingcommons.scout.domain.ScoutController
import org.cyclingcommons.scout.domain.ScoutUiState
import org.cyclingcommons.scout.domain.Timings
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.VehicleCounter
import org.cyclingcommons.scout.recording.RideFile
import org.cyclingcommons.scout.recording.RideFiles
import org.cyclingcommons.scout.recording.RideFitSession
import org.cyclingcommons.scout.recording.RideForegroundService
import org.cyclingcommons.scout.recording.RideRecoveryStore
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.sensors.LocationSampler
import org.cyclingcommons.scout.sensors.radar.RadarCoordinator
import org.cyclingcommons.scout.sensors.radar.RadarDeviceRow
import org.cyclingcommons.scout.sensors.radar.RadarStatus
import org.cyclingcommons.scout.sensors.radar.RadarTransport
import org.cyclingcommons.scout.ui.theme.ThemeMode
import java.io.File
import kotlin.math.roundToInt

enum class Screen {
    INTRO,
    RECOVERY,
    RIDE,
    SETTINGS,
    HELP,
    PAIR_RADAR,
}

data class RecoveryPrompt(
    val elapsedLabel: String,
    val sampleCount: Long,
    val fitFileName: String,
)

data class RideUiModel(
    val screen: Screen = Screen.RIDE,
    val scout: ScoutUiState = ScoutUiState(),
    val elapsedSec: Long = 0L,
    val sampleCount: Long = 0L,
    val pendingTags: Int = 0,
    val hasLocationPermission: Boolean = false,
    /** Formatted last fix, or null while GPS has nothing yet. */
    val fixLabel: String? = null,
    val lastFitPath: String? = null,
    val radar: RadarStatus = RadarStatus(),
    val imperial: Boolean = false,
    val keepScreenOn: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val rides: List<RideFile> = emptyList(),
    /** Transient rider hint (e.g. tapped a tile before recording). */
    val userMessage: String? = null,
    val recovery: RecoveryPrompt? = null,
)

/**
 * Ride façade for the UI (TECHNICAL §4). Holds no radio logic of its own: the tagging
 * rules live in [ScoutController], the radar link in [RadarCoordinator], the file in
 * [RideFitSession].
 *
 * The tick loop is demand-driven — it samples at ~1 Hz while RUNNING, drops to 250 ms
 * only while a picker or lit tile is on screen, and suspends outright when the app is
 * idle so a backgrounded Scout costs nothing (SPEC §12.1).
 */
class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = ScoutController()
    private val vehicles = VehicleCounter()
    private val location = LocationSampler(app)
    private val radar = RadarCoordinator(app)
    private val appPrefs = AppPrefs(app)
    private val feedback = RideFeedback(app)
    private val recoveryStore = RideRecoveryStore(app)

    private var fitSession: RideFitSession? = null
    private var pendingRecovery: RideRecoveryStore.PendingRecovery? = null

    private val _ui = MutableStateFlow(buildInitialUi(app))
    val ui: StateFlow<RideUiModel> = _ui.asStateFlow()

    /** Signals the tick loop to run again; conflated because one wake is enough. */
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    private var uiVisible = false
    private var lastSampleAt = 0L
    private var sampleCount = 0L
    private var rideStartedAtMs = 0L
    private var elapsedBeforePauseMs = 0L

    private val accessibilityManager =
        app.getSystemService(AccessibilityManager::class.java)
    private val accessibilityListener =
        AccessibilityManager.AccessibilityStateChangeListener { syncTalkBackTimeouts() }
    private val touchExplorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener { syncTalkBackTimeouts() }

    init {
        radar.onStatusChanged = ::wake
        accessibilityManager?.addAccessibilityStateChangeListener(accessibilityListener)
        accessibilityManager?.addTouchExplorationStateChangeListener(touchExplorationListener)
        syncTalkBackTimeouts()
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                tick(now)
                val delayMs = nextTickDelayMs(now)
                if (delayMs == null) {
                    wakeSignal.receive()
                } else {
                    withTimeoutOrNull(delayMs) { wakeSignal.receive() }
                }
            }
        }
        refreshPermissions()
    }

    private fun tick(nowMs: Long) {
        if (controller.onTick(nowMs)) {
            // Picker commit / timeout — same confirm/undo feedback as a tap.
            controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        }
        if (controller.timer == TimerState.RUNNING && nowMs - lastSampleAt >= SAMPLE_INTERVAL_MS) {
            lastSampleAt = nowMs
            writeSample(nowMs)
            radar.onTick(nowMs)
        }
        publish(nowMs)
    }

    /** Null = nothing pending; park the loop until something wakes it. */
    private fun nextTickDelayMs(nowMs: Long): Long? {
        val animating = uiVisible && controller.needsTick(nowMs)
        if (controller.timer != TimerState.RUNNING) {
            return if (animating) ANIMATION_INTERVAL_MS else null
        }
        if (animating) return ANIMATION_INTERVAL_MS
        return (SAMPLE_INTERVAL_MS - (nowMs - lastSampleAt)).coerceIn(1L, SAMPLE_INTERVAL_MS)
    }

    private fun wake() {
        wakeSignal.trySend(Unit)
    }

    private fun syncTalkBackTimeouts(nowMs: Long = System.currentTimeMillis()) {
        val scale = if (getApplication<Application>().isTalkBackActive()) {
            Timings.TALKBACK_TIMEOUT_SCALE
        } else {
            1
        }
        controller.setTimeoutScale(scale, nowMs)
    }

    /** Driven by the activity lifecycle: while hidden, only recording keeps ticking. */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) wake()
    }

    fun refreshPermissions() {
        syncTalkBackTimeouts()
        radar.refreshCapabilities()
        _ui.update {
            it.copy(
                hasLocationPermission = location.hasPermission(),
                radar = radar.status.value,
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
                themeMode = appPrefs.themeMode,
            )
        }
    }

    fun dismissIntro() {
        appPrefs.introSeen = true
        show(Screen.RIDE)
    }

    fun replayIntro() {
        appPrefs.introSeen = false
        show(Screen.INTRO)
    }

    fun openSettings() {
        show(Screen.SETTINGS)
        refreshPermissions()
        loadRides()
    }

    fun closeSettings() = show(Screen.RIDE)

    fun openHelp(returnTo: Screen = Screen.SETTINGS) {
        helpReturnScreen = returnTo
        show(Screen.HELP)
    }

    fun closeHelp() = show(helpReturnScreen)

    private var helpReturnScreen = Screen.SETTINGS

    fun openPairRadar() {
        radar.openPairing()
        show(Screen.PAIR_RADAR)
    }

    fun closePairRadar() {
        radar.closePairing(rideIdle = controller.timer == TimerState.IDLE)
        show(Screen.SETTINGS)
        loadRides()
    }

    fun setImperial(value: Boolean) {
        appPrefs.imperial = value
        _ui.update { it.copy(imperial = value, scout = it.scout.copy(imperial = value)) }
    }

    fun setKeepScreenOn(value: Boolean) {
        appPrefs.keepScreenOn = value
        _ui.update { it.copy(keepScreenOn = value) }
    }

    fun setThemeMode(value: ThemeMode) {
        appPrefs.themeMode = value
        _ui.update { it.copy(themeMode = value) }
    }

    fun setTransport(transport: RadarTransport) = radar.setTransport(transport)

    fun startRadarScan() = radar.startScan()

    fun stopRadarScan() = radar.stopScan()

    fun startAntSearch() = radar.startAntSearch()

    fun selectRadar(row: RadarDeviceRow) = radar.select(row)

    fun forgetRadar() = radar.forget()

    /** Tap on the dead radar strip — same as Garmin `openRadar()`. */
    fun retryRadar() {
        if (controller.timer != TimerState.RUNNING) return
        radar.searchFromTap()
        feedback.confirm(undone = false)
        publish()
        wake()
    }

    fun startRide() {
        controller.start()
        vehicles.resetRide()
        lastSampleAt = 0L
        sampleCount = 0L
        rideStartedAtMs = System.currentTimeMillis()
        elapsedBeforePauseMs = 0L
        fitSession = RideFitSession(getApplication(), viewModelScope)
        if (location.hasPermission()) location.start()
        radar.onRideStart()
        RideForegroundService.sync(getApplication(), TimerState.RUNNING)
        persistRideState()
        publish()
        wake()
    }

    fun pauseRide() {
        controller.pause()
        elapsedBeforePauseMs += System.currentTimeMillis() - rideStartedAtMs
        location.stop()
        radar.onRideStop()
        fitSession?.flush()
        RideForegroundService.sync(getApplication(), TimerState.PAUSED)
        persistRideState()
        publish()
    }

    fun resumeRide() {
        controller.resume()
        rideStartedAtMs = System.currentTimeMillis()
        if (location.hasPermission()) location.start()
        radar.onRideStart()
        RideForegroundService.sync(getApplication(), TimerState.RUNNING)
        persistRideState()
        publish()
        wake()
    }

    fun stopRide() {
        controller.stop()
        vehicles.resetRide()
        location.stop()
        radar.onRideStop()
        elapsedBeforePauseMs = 0L
        sampleCount = 0L
        recoveryStore.clear()
        fitSession?.finish { saved ->
            _ui.update { it.copy(lastFitPath = saved?.absolutePath) }
            loadRides()
        }
        fitSession = null
        RideForegroundService.sync(getApplication(), TimerState.IDLE)
        _ui.update { it.copy(fixLabel = null, recovery = null) }
        publish()
    }

    fun resumeRecoveredRide() {
        val pending = pendingRecovery ?: return
        val session = pending.session
        pendingRecovery = null
        controller.restoreSession(session)
        vehicles.restore(session.carCount, session.lastCarSpeedKph)
        sampleCount = session.sampleCount
        lastSampleAt = 0L
        elapsedBeforePauseMs = session.elapsedMs
        rideStartedAtMs = System.currentTimeMillis()
        fitSession = RideFitSession(
            getApplication(),
            viewModelScope,
            existingFile = pending.file,
            existingRecordCount = session.sampleCount.toInt(),
        )
        when (session.timer) {
            TimerState.RUNNING -> {
                if (location.hasPermission()) location.start()
                radar.onRideStart()
                RideForegroundService.sync(getApplication(), TimerState.RUNNING)
            }
            TimerState.PAUSED -> {
                RideForegroundService.sync(getApplication(), TimerState.PAUSED)
            }
            TimerState.IDLE -> Unit
        }
        persistRideState()
        _ui.update { it.copy(screen = Screen.RIDE, recovery = null) }
        publish()
        wake()
    }

    fun discardRecovery() {
        val pending = pendingRecovery
        pendingRecovery = null
        recoveryStore.clear()
        viewModelScope.launch {
            pending?.file?.let { file ->
                withContext(Dispatchers.IO) { file.delete() }
            }
            _ui.update { it.copy(screen = initialScreenAfterRecovery(), recovery = null) }
        }
    }

    fun onTileTap(index: Int) {
        if (controller.timer != TimerState.RUNNING) {
            val res = when (controller.timer) {
                TimerState.PAUSED -> R.string.ride_resume_to_tag
                else -> R.string.ride_start_to_tag
            }
            showUserMessage(getApplication<Application>().getString(res))
            return
        }
        val now = System.currentTimeMillis()
        controller.onTileTap(index, now)
        controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        persistRideState()
        publish(now)
        wake()
    }

    fun clearUserMessage() {
        _ui.update { it.copy(userMessage = null) }
    }

    private fun showUserMessage(message: String) {
        _ui.update { it.copy(userMessage = message) }
    }

    fun endOpenSurface() {
        if (controller.timer != TimerState.RUNNING) {
            val res = when (controller.timer) {
                TimerState.PAUSED -> R.string.ride_resume_to_tag
                else -> R.string.ride_start_to_tag
            }
            showUserMessage(getApplication<Application>().getString(res))
            return
        }
        val now = System.currentTimeMillis()
        controller.endOpenSurface(now)
        controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        persistRideState()
        publish(now)
        wake()
    }

    fun shareLastFit(): Intent? = _ui.value.lastFitPath?.let { shareFitPath(it) }

    fun shareRide(ride: RideFile): Intent? = shareFitPath(ride.file.absolutePath)

    fun deleteRide(ride: RideFile) {
        val path = ride.file.absolutePath
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RideFiles.delete(ride) }
            _ui.update {
                it.copy(lastFitPath = it.lastFitPath?.takeIf { p -> p != path })
            }
            loadRides()
        }
    }

    private fun show(screen: Screen) {
        _ui.update { it.copy(screen = screen) }
    }

    private fun loadRides() {
        viewModelScope.launch {
            val rides = withContext(Dispatchers.IO) { RideFiles.list(getApplication()) }
            _ui.update { it.copy(rides = rides) }
        }
    }

    private fun writeSample(nowMs: Long) {
        val tag = controller.drainTag()
        val fix = location.latest
        val observation = radar.observation()
        val channels = observation.fitChannels()
        val riderKph = fix?.speedMps?.let { (it * KPH_PER_MPS).toInt() } ?: 0
        vehicles.onSample(
            tracking = observation.tracking,
            occupiedCount = observation.occupiedCount(),
            nearestClosingKph = observation.nearestClosingKph(),
            nearestRangeM = observation.nearestRangeM(),
            riderKph = riderKph,
        )
        fitSession?.appendSample(
            nowMs = nowMs,
            fix = fix,
            poiType = tag?.type ?: 0,
            poiDetail = tag?.detail ?: 0,
            radarCount = channels[0],
            radarNear = channels[1],
            radarSpeed = channels[2],
        )
        sampleCount++
        persistRideState(nowMs)
    }

    private fun persistRideState(nowMs: Long = System.currentTimeMillis()) {
        val timer = controller.timer
        if (timer == TimerState.IDLE) return
        val session = fitSession ?: return
        recoveryStore.save(
            fitPath = session.outFile.absolutePath,
            session = controller.sessionSnapshot(
                elapsedMs = currentElapsedMs(nowMs),
                sampleCount = sampleCount,
                carCount = vehicles.carCount,
                lastCarSpeedKph = vehicles.lastCarSpeedKph,
            ),
        )
    }

    private fun currentElapsedMs(nowMs: Long): Long =
        when (controller.timer) {
            TimerState.IDLE -> 0L
            TimerState.PAUSED -> elapsedBeforePauseMs
            TimerState.RUNNING -> elapsedBeforePauseMs + (nowMs - rideStartedAtMs)
        }

    private fun buildInitialUi(app: Application): RideUiModel {
        pendingRecovery = recoveryStore.load()
        val recoveryPrompt = pendingRecovery?.let { pending ->
            RecoveryPrompt(
                elapsedLabel = formatElapsed(pending.session.elapsedMs),
                sampleCount = pending.session.sampleCount,
                fitFileName = pending.file.name,
            )
        }
        val screen = when {
            recoveryPrompt != null -> Screen.RECOVERY
            !appPrefs.introSeen -> Screen.INTRO
            else -> Screen.RIDE
        }
        return RideUiModel(screen = screen, recovery = recoveryPrompt)
    }

    private fun initialScreenAfterRecovery(): Screen =
        if (appPrefs.introSeen) Screen.RIDE else Screen.INTRO

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    private fun publish(nowMs: Long = System.currentTimeMillis()) {
        val timer = controller.timer
        val elapsedMs = when (timer) {
            TimerState.IDLE -> 0L
            TimerState.PAUSED -> elapsedBeforePauseMs
            TimerState.RUNNING -> elapsedBeforePauseMs + (nowMs - rideStartedAtMs)
        }
        _ui.update {
            it.copy(
                scout = withRadar(controller.snapshot(nowMs)),
                elapsedSec = elapsedMs / 1000L,
                sampleCount = sampleCount,
                pendingTags = controller.queueSize(),
                fixLabel = if (timer == TimerState.IDLE) it.fixLabel else fixLabel(),
                radar = radar.status.value,
            )
        }
    }

    private fun withRadar(base: ScoutUiState): ScoutUiState = base.copy(
        radarLive = radar.status.value.tracking,
        carCount = vehicles.carCount,
        lastCarSpeedKph = vehicles.lastCarSpeedKph,
        imperial = appPrefs.imperial,
    )

    /**
     * A rider cannot do anything with raw coordinates at 30 km/h; what they need to know
     * is whether the tag they just dropped will land in the right place.
     */
    private fun fixLabel(): String? {
        val fix = location.latest ?: return null
        val app = getApplication<Application>()
        val accuracy = fix.accuracyM ?: return app.getString(R.string.ride_gps_ready)
        return if (appPrefs.imperial) {
            app.getString(R.string.ride_gps_accuracy_ft, (accuracy * FEET_PER_METRE).roundToInt())
        } else {
            app.getString(R.string.ride_gps_accuracy_m, accuracy.roundToInt())
        }
    }

    private fun shareFitPath(path: String): Intent? {
        val file = File(path)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
    }

    override fun onCleared() {
        accessibilityManager?.removeAccessibilityStateChangeListener(accessibilityListener)
        accessibilityManager?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        location.stop()
        radar.onRideStop()
        feedback.release()
        super.onCleared()
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val ANIMATION_INTERVAL_MS = 250L
        const val KPH_PER_MPS = 3.6f
        const val FEET_PER_METRE = 3.28084f
    }
}
