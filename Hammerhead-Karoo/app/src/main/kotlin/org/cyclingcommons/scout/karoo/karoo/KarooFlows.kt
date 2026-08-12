package org.cyclingcommons.scout.karoo.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** From [karoo-ext sample Extensions.kt](https://github.com/hammerheadnav/karoo-ext/blob/master/app/src/main/kotlin/io/hammerhead/sampleext/extension/Extensions.kt). */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> =
    callbackFlow {
        val listenerId =
            addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                trySendBlocking(event.state)
            }
        awaitClose {
            removeConsumer(listenerId)
        }
    }

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> =
    callbackFlow {
        val listenerId = addConsumer<T> { trySend(it) }
        awaitClose {
            removeConsumer(listenerId)
        }
    }

fun KarooSystemService.addStreamConsumer(
    dataTypeId: String,
    onState: (StreamState) -> Unit,
): String =
    addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        onState(event.state)
    }
