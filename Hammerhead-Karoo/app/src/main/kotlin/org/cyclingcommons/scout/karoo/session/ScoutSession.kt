package org.cyclingcommons.scout.karoo.session

import io.hammerhead.karooext.models.RideState
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.RadarObservation
import org.cyclingcommons.scout.domain.ScoutController
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.karoo.fit.ScoutFitFields
import io.hammerhead.karooext.models.FieldValue
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide bridge between [org.cyclingcommons.scout.karoo.extension.ScoutExtension],
 * tagging UI, and shared domain logic.
 */
object ScoutSession {
    val controller = ScoutController()

    private val radarRef = AtomicReference(RadarObservation())

    @Volatile
    var rideState: RideState = RideState.Idle
        private set

    fun onRideState(state: RideState) {
        rideState = state
        when (state) {
            is RideState.Recording ->
                when (controller.timer) {
                    TimerState.PAUSED -> controller.resume()
                    TimerState.IDLE -> controller.start()
                    TimerState.RUNNING -> Unit
                }
            is RideState.Paused -> controller.pause()
            is RideState.Idle -> controller.stop()
        }
    }

    fun updateRadar(observation: RadarObservation) {
        radarRef.set(observation)
    }

    fun latestRadar(): RadarObservation = radarRef.get()

    /** One Scout channel row for the current tick while Karoo is recording. */
    fun buildFitFieldValues(): List<FieldValue> {
        val tag = controller.drainTag()
        val radar = latestRadar().fitChannels()
        return ScoutFitFields.fieldValues(
            poiType = tag?.type ?: PoiType.NONE,
            poiDetail = tag?.detail ?: 0,
            radarCount = radar[0],
            radarNear = radar[1],
            radarSpeed = radar[2],
        )
    }

    fun rideStateLabel(): String =
        when (rideState) {
            is RideState.Recording -> "recording"
            is RideState.Paused -> "paused"
            is RideState.Idle -> "idle"
        }
}
