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
import java.util.concurrent.atomic.AtomicBoolean
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.VehicleCounter
import org.cyclingcommons.scout.karoo.karoo.streamDataFlow
import org.cyclingcommons.scout.karoo.radar.KarooRadarAdapter
import org.cyclingcommons.scout.karoo.session.ScoutSession

/**
 * Long-lived Karoo link for the extension service: ride state, radar, FIT, and
 * live car tally. Runs while Karoo has bound [ScoutExtension] during a ride —
 * independent of whether [org.cyclingcommons.scout.karoo.tagging.TaggingActivity]
 * is on screen (Garmin data-field parity).
 */
object ScoutExtensionRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var karooSystem: KarooSystemService? = null
    private var radarAdapter: KarooRadarAdapter? = null
    private var rideStateConsumerId: String? = null
    private var speedJob: Job? = null
    private var vehicleJob: Job? = null
    private val started = AtomicBoolean(false)
    private val vehicles = VehicleCounter()

    @Volatile
    var riderKph: Int = 0
        private set

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val system = KarooSystemService(app)
        karooSystem = system
        radarAdapter = KarooRadarAdapter(system)

        system.connect { connected ->
            if (!connected) return@connect
            radarAdapter?.start()
            rideStateConsumerId =
                system.addConsumer { state: RideState ->
                    ScoutSession.onRideState(state)
                    if (state is RideState.Idle) {
                        vehicles.resetRide()
                    }
                }
            startSpeedStream(system)
            startVehicleLoop()
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        speedJob?.cancel()
        speedJob = null
        vehicleJob?.cancel()
        vehicleJob = null
        rideStateConsumerId?.let { karooSystem?.removeConsumer(it) }
        rideStateConsumerId = null
        radarAdapter?.stop()
        radarAdapter = null
        karooSystem?.disconnect()
        karooSystem = null
        riderKph = 0
        vehicles.resetRide()
    }

    fun karooOrNull(): KarooSystemService? = karooSystem

    fun liveCarCount(): Int = vehicles.carCount

    fun liveCarSpeedKph(): Int = vehicles.lastCarSpeedKph

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
                    if (ScoutSession.controller.timer == TimerState.RUNNING) {
                        val radar = ScoutSession.latestRadar()
                        vehicles.onSample(
                            tracking = radar.tracking,
                            occupiedCount = radar.occupiedCount(),
                            nearestClosingKph = radar.nearestClosingKph(),
                            nearestRangeM = radar.nearestRangeM(),
                            riderKph = riderKph,
                        )
                    }
                    delay(1000)
                }
            }
    }
}
