package org.cyclingcommons.scout.karoo.extension



import android.content.Intent

import io.hammerhead.karooext.extension.KarooExtension

import io.hammerhead.karooext.internal.Emitter

import io.hammerhead.karooext.models.DataType

import io.hammerhead.karooext.models.FitEffect

import io.hammerhead.karooext.models.RideState

import io.hammerhead.karooext.models.StreamState

import io.hammerhead.karooext.models.WriteToRecordMesg

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.mapNotNull

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import org.cyclingcommons.scout.karoo.karoo.streamDataFlow

import org.cyclingcommons.scout.karoo.session.ScoutSession

import org.cyclingcommons.scout.karoo.tagging.TaggingActivity



class ScoutExtension : KarooExtension(EXTENSION_ID, EXTENSION_VERSION) {

    override val types by lazy {

        listOf(ScoutStatusDataType(extension))

    }



    override fun startFit(emitter: Emitter<FitEffect>) {

        ScoutExtensionRuntime.start(this)

        val job =

            CoroutineScope(Dispatchers.IO).launch {

                val system = ScoutExtensionRuntime.karooOrNull() ?: return@launch

                while (isActive && !system.connected) {

                    delay(50)

                }

                system

                    .streamDataFlow(DataType.Type.ELAPSED_TIME)

                    .mapNotNull { (it as? StreamState.Streaming)?.dataPoint }

                    .collect {

                        when (ScoutSession.rideState) {

                            is RideState.Recording -> {

                                emitter.onNext(WriteToRecordMesg(ScoutSession.buildFitFieldValues()))

                            }

                            else -> Unit

                        }

                    }

            }

        emitter.setCancellable { job.cancel() }

    }



    override fun onBonusAction(actionId: String) {

        if (actionId != ACTION_OPEN_TAGGING) return

        val intent = Intent(this, TaggingActivity::class.java)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)

    }



    override fun onCreate() {

        super.onCreate()

        ScoutExtensionRuntime.start(this)

    }



    override fun onDestroy() {

        ScoutExtensionRuntime.stop()

        super.onDestroy()

    }



    companion object {

        const val EXTENSION_ID = "scout"

        const val EXTENSION_VERSION = "1.0.0"

        const val ACTION_OPEN_TAGGING = "open-tagging"

    }

}

