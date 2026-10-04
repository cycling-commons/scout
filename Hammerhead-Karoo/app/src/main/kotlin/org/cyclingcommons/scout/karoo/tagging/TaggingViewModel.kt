package org.cyclingcommons.scout.karoo.tagging



import android.app.Application

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Job

import kotlinx.coroutines.channels.Channel

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import kotlinx.coroutines.withTimeoutOrNull

import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.ScoutUiState

import org.cyclingcommons.scout.domain.TimerState

import org.cyclingcommons.scout.karoo.extension.ScoutExtensionRuntime

import org.cyclingcommons.scout.karoo.session.ScoutSession



data class TaggingUiModel(

    val scout: ScoutUiState = ScoutUiState(),

    val radarSeeking: Boolean = false,

    val riderMessage: String? = null,

)



class TaggingViewModel(app: Application) : AndroidViewModel(app) {

    private val feedback = RideFeedback(app)



    private val _ui = MutableStateFlow(TaggingUiModel())

    val ui: StateFlow<TaggingUiModel> = _ui.asStateFlow()



    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    private var uiVisible = false



    init {

        // Hold the runtime for as long as this screen exists; it also ticks picker
        // deadlines, so a pick still commits after this screen is closed.
        ScoutExtensionRuntime.acquire(app)

        viewModelScope.launch {

            ScoutSession.feedback.collect {

                feedback.confirm(it.undone)

                wake()

            }

        }

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

    }



    fun setUiVisible(visible: Boolean) {

        uiVisible = visible

        if (visible) {

            wake()

        }

    }



    fun onTileTap(index: Int) {

        val now = System.currentTimeMillis()

        val result =

            ScoutSession.withController { c ->

                if (c.timer != TimerState.RUNNING) {

                    null

                } else {

                    c.onTileTap(index, now)

                    Result.success(c.takeFeedback())

                }

            }

        if (result == null) {

            _ui.update { it.copy(riderMessage = "idle") }

            wake()

            return

        }

        result.getOrNull()?.let { feedback.confirm(it.undone) }

        publish(now)

        wake()

    }



    fun onEndOpenSurface() {

        val now = System.currentTimeMillis()

        ScoutSession.withController { c ->

            c.endOpenSurface(now)

            c.takeFeedback()

        }?.let { feedback.confirm(it.undone) }

        publish(now)

        wake()

    }



    fun dismissMessage() {

        _ui.update { it.copy(riderMessage = null) }

    }



    override fun onCleared() {

        feedback.release()

        ScoutExtensionRuntime.release()

        super.onCleared()

    }



    private fun tick(nowMs: Long) {

        // Picker deadlines are advanced by ScoutExtensionRuntime; this loop only redraws.
        publish(nowMs)

    }



    private fun publish(nowMs: Long) {

        val radar = ScoutSession.latestRadar()

        val snapshot = ScoutSession.withController { it.snapshot(nowMs) }

        _ui.update {

            it.copy(

                scout =

                    snapshot.copy(

                        radarLive = radar.tracking,

                        carCount = ScoutExtensionRuntime.liveCarCount(),

                        lastCarSpeedKph = ScoutExtensionRuntime.liveCarSpeedKph(),

                    ),

                radarSeeking = radar.state == RadarLinkState.SCANNING ||
                    radar.state == RadarLinkState.CONNECTING,

            )

        }

    }



    private fun nextTickDelayMs(nowMs: Long): Long? {

        val (needsTick, timer) =

            ScoutSession.withController { it.needsTick(nowMs) to it.timer }

        val animating = uiVisible && needsTick

        if (timer != TimerState.RUNNING) {

            return if (animating) ANIMATION_INTERVAL_MS else null

        }

        if (animating) return ANIMATION_INTERVAL_MS

        return SAMPLE_INTERVAL_MS

    }



    private fun wake() {

        wakeSignal.trySend(Unit)

    }



    private companion object {

        const val SAMPLE_INTERVAL_MS = 1000L

        const val ANIMATION_INTERVAL_MS = 100L

    }

}

