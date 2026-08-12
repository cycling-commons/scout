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

import org.cyclingcommons.scout.domain.ScoutUiState

import org.cyclingcommons.scout.domain.TimerState

import org.cyclingcommons.scout.karoo.extension.ScoutExtensionRuntime

import org.cyclingcommons.scout.karoo.session.ScoutSession



data class TaggingUiModel(

    val scout: ScoutUiState = ScoutUiState(),

    val riderMessage: String? = null,

)



class TaggingViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = ScoutSession.controller

    private val feedback = RideFeedback(app)



    private val _ui = MutableStateFlow(TaggingUiModel())

    val ui: StateFlow<TaggingUiModel> = _ui.asStateFlow()



    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    private var uiVisible = false



    init {

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

        ScoutExtensionRuntime.start(getApplication())

        if (visible) {

            wake()

        }

    }



    fun onTileTap(index: Int) {

        val now = System.currentTimeMillis()

        if (controller.timer != TimerState.RUNNING) {

            _ui.update { it.copy(riderMessage = "idle") }

            wake()

            return

        }

        controller.onTileTap(index, now)

        controller.takeFeedback()?.let { feedback.confirm(it.undone) }

        publish(now)

        wake()

    }



    fun onEndOpenSurface() {

        val now = System.currentTimeMillis()

        controller.endOpenSurface(now)

        controller.takeFeedback()?.let { feedback.confirm(it.undone) }

        publish(now)

        wake()

    }



    fun dismissMessage() {

        _ui.update { it.copy(riderMessage = null) }

    }



    override fun onCleared() {

        feedback.release()

        super.onCleared()

    }



    private fun tick(nowMs: Long) {

        if (controller.onTick(nowMs)) {

            controller.takeFeedback()?.let { feedback.confirm(it.undone) }

        }

        publish(nowMs)

    }



    private fun publish(nowMs: Long) {

        val radar = ScoutSession.latestRadar()

        _ui.update {

            it.copy(

                scout =

                    controller.snapshot(nowMs).copy(

                        radarLive = radar.tracking,

                        carCount = ScoutExtensionRuntime.liveCarCount(),

                        lastCarSpeedKph = ScoutExtensionRuntime.liveCarSpeedKph(),

                    ),

            )

        }

    }



    private fun nextTickDelayMs(nowMs: Long): Long? {

        val animating = uiVisible && controller.needsTick(nowMs)

        if (controller.timer != TimerState.RUNNING) {

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

