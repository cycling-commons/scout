package org.cyclingcommons.scout.karoo.session

import io.hammerhead.karooext.models.RideState
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.RadarObservation
import org.cyclingcommons.scout.domain.ScoutController
import org.cyclingcommons.scout.domain.TagFeedback
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.karoo.fit.ScoutFitFields
import io.hammerhead.karooext.models.FieldValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide bridge between [org.cyclingcommons.scout.karoo.extension.ScoutExtension],
 * tagging UI, and shared domain logic.
 *
 * [ScoutController] is not thread-safe, and on Karoo it is driven from the main
 * thread (taps), the FIT writer and the ride-page field (IO). Every access goes
 * through [withController] so tags are never lost or double-written.
 */
object ScoutSession {
    private val controller = ScoutController()
    private val controllerLock = Any()

    private val radarRef = AtomicReference(RadarObservation())

    private val _feedback =
        MutableSharedFlow<TagFeedback>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Confirm/undo feedback for tags committed off the UI thread (picker timeouts). */
    val feedback: SharedFlow<TagFeedback> = _feedback.asSharedFlow()

    @Volatile
    var rideState: RideState = RideState.Idle
        private set

    fun <T> withController(block: (ScoutController) -> T): T =
        synchronized(controllerLock) { block(controller) }

    val timer: TimerState
        get() = withController { it.timer }

    fun onRideState(state: RideState) {
        rideState = state
        withController { c ->
            when (state) {
                is RideState.Recording ->
                    when (c.timer) {
                        TimerState.PAUSED -> c.resume()
                        TimerState.IDLE -> c.start()
                        TimerState.RUNNING -> Unit
                    }
                is RideState.Paused -> c.pause()
                is RideState.Idle -> c.stop()
            }
        }
    }

    /**
     * Advance picker / hold-to-confirm deadlines. Runs for the whole ride from
     * [org.cyclingcommons.scout.karoo.extension.ScoutExtensionRuntime], so a pick
     * commits on time even after the Tag ride screen is closed.
     */
    fun tick(nowMs: Long) {
        val committed =
            withController { c ->
                if (c.onTick(nowMs)) c.takeFeedback() else null
            }
        committed?.let { _feedback.tryEmit(it) }
    }

    fun updateRadar(observation: RadarObservation) {
        radarRef.set(observation)
    }

    fun latestRadar(): RadarObservation = radarRef.get()

    /** One Scout channel row for the current tick while Karoo is recording. */
    fun buildFitFieldValues(): List<FieldValue> {
        val tag = withController { it.drainTag() }
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
