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

import kotlinx.coroutines.flow.mapNotNull

import kotlinx.coroutines.launch

import org.cyclingcommons.scout.karoo.karoo.streamDataFlow

import org.cyclingcommons.scout.karoo.session.ScoutSession

import org.cyclingcommons.scout.karoo.tagging.TaggingActivity



class ScoutExtension : KarooExtension(EXTENSION_ID, EXTENSION_VERSION) {

    override val types by lazy {

        listOf(ScoutStatusDataType(extension))

    }



    override fun startFit(emitter: Emitter<FitEffect>) {

        // onCreate already acquired the runtime. The stream consumer registers
        // itself once Karoo is connected, so no need to wait for the binder here.
        val system = ScoutExtensionRuntime.karooOrNull() ?: return

        val job =

            CoroutineScope(Dispatchers.IO).launch {

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

        ScoutExtensionRuntime.acquire(this)

    }



    override fun onDestroy() {

        ScoutExtensionRuntime.release()

        super.onDestroy()

    }



    companion object {

        const val EXTENSION_ID = "scout"

        const val EXTENSION_VERSION = "1.0.0"

        const val ACTION_OPEN_TAGGING = "open-tagging"

    }

}
