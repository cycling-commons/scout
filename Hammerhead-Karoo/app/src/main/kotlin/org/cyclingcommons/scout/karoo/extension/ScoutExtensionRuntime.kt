package org.cyclingcommons.scout.karoo.extension

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.VehicleCounter
import org.cyclingcommons.scout.karoo.karoo.streamDataFlow
import org.cyclingcommons.scout.karoo.radar.KarooRadarAdapter
import org.cyclingcommons.scout.karoo.session.ScoutSession

/**
 * Long-lived Karoo link for the extension service: ride state, radar, FIT, picker
 * deadlines and live car tally. Runs while Karoo has bound [ScoutExtension] during a
 * ride — independent of whether [org.cyclingcommons.scout.karoo.tagging.TaggingActivity]
 * is on screen (Garmin data-field parity).
 *
 * Reference counted: the extension service and the tagging screen each [acquire] it,
 * and it only tears down when the last holder calls [release].
 */
object ScoutExtensionRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var holders = 0
    private var karooSystem: KarooSystemService? = null
    @Volatile
    private var radarAdapter: KarooRadarAdapter? = null
    private var rideStateConsumerId: String? = null
    private var speedJob: Job? = null
    private var vehicleJob: Job? = null
    private var tickJob: Job? = null
    private val vehicles = VehicleCounter()

    @Volatile
    var riderKph: Int = 0
        private set

    fun acquire(context: Context) {
        synchronized(lock) {
            holders++
            if (holders == 1) startLocked(context.applicationContext)
        }
    }

    fun release() {
        synchronized(lock) {
            if (holders == 0) return
            holders--
            if (holders == 0) stopLocked()
        }
    }

    fun karooOrNull(): KarooSystemService? = synchronized(lock) { karooSystem }

    fun liveCarCount(): Int = synchronized(vehicles) { vehicles.carCount }

    fun liveCarSpeedKph(): Int = synchronized(vehicles) { vehicles.lastCarSpeedKph }

    private fun startLocked(app: Context) {
        val system = KarooSystemService(app)
        karooSystem = system
        // karoo-ext keeps consumers across reconnects and registers them once the
        // binder is up, so they are added exactly once here — not in the connect
        // callback, which fires again on every reconnect.
        system.connect()
        radarAdapter = KarooRadarAdapter(system).also { it.start() }
        rideStateConsumerId =
            system.addConsumer { state: RideState ->
                ScoutSession.onRideState(state)
                if (state is RideState.Idle) {
                    synchronized(vehicles) { vehicles.resetRide() }
                }
            }
        startSpeedStream(system)
        startVehicleLoop()
        startTickLoop()
    }

    private fun stopLocked() {
        speedJob?.cancel()
        speedJob = null
        vehicleJob?.cancel()
        vehicleJob = null
        tickJob?.cancel()
        tickJob = null
        rideStateConsumerId?.let { karooSystem?.removeConsumer(it) }
        rideStateConsumerId = null
        radarAdapter?.stop()
        radarAdapter = null
        karooSystem?.disconnect()
        karooSystem = null
        riderKph = 0
        synchronized(vehicles) { vehicles.resetRide() }
    }

    private fun startSpeedStream(system: KarooSystemService) {
        speedJob?.cancel()
        speedJob =
            scope.launch {
                system.streamDataFlow(DataType.Type.SPEED).collect { state ->
                    if (state is StreamState.Streaming) {
                        val mps = state.dataPoint.singleValue ?: 0.0
                        riderKph = (mps * 3.6).toInt().coerceAtLeast(0)
                    }
                }
            }
    }

    private fun startVehicleLoop() {
        vehicleJob?.cancel()
        vehicleJob =
            scope.launch {
                while (isActive) {
                    if (ScoutSession.timer == TimerState.RUNNING) {
                        val radar = ScoutSession.latestRadar()
                        // Karoo radar has no closing speed; use the range-derived
                        // estimate so passes can be credited (display only).
                        val closingKph = radarAdapter?.estimatedClosingKph ?: -1
                        synchronized(vehicles) {
                            vehicles.onSample(
                                tracking = radar.tracking,
                                occupiedCount = radar.occupiedCount(),
                                nearestClosingKph = closingKph,
                                nearestRangeM = radar.nearestRangeM(),
                                riderKph = riderKph,
                            )
                        }
                    }
                    delay(1000)
                }
            }
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob =
            scope.launch {
                while (isActive) {
                    ScoutSession.tick(System.currentTimeMillis())
                    delay(TICK_INTERVAL_MS)
                }
            }
    }

    private const val TICK_INTERVAL_MS = 200L
}
