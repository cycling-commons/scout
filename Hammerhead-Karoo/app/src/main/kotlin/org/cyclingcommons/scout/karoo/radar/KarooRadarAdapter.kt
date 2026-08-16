package org.cyclingcommons.scout.karoo.radar

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.RadarObservation
import org.cyclingcommons.scout.domain.RadarTarget
import org.cyclingcommons.scout.karoo.karoo.addStreamConsumer
import org.cyclingcommons.scout.karoo.session.ScoutSession
import java.util.concurrent.atomic.AtomicReference

/**
 * Normalizes Karoo's native ANT+ radar stream into Scout [RadarObservation].
 *
 * Karoo exposes per-target range fields only — no closing speed (TECHNICAL §8).
 */
class KarooRadarAdapter(
    private val karooSystem: KarooSystemService,
) {
    private val consumerId = AtomicReference<String?>(null)

    private val targetRangeFields =
        listOf(
            DataType.Field.RADAR_TARGET_1_RANGE,
            DataType.Field.RADAR_TARGET_2_RANGE,
            DataType.Field.RADAR_TARGET_3_RANGE,
            DataType.Field.RADAR_TARGET_4_RANGE,
            DataType.Field.RADAR_TARGET_5_RANGE,
            DataType.Field.RADAR_TARGET_6_RANGE,
            DataType.Field.RADAR_TARGET_7_RANGE,
            DataType.Field.RADAR_TARGET_8_RANGE,
        )

    fun start() {
        if (consumerId.get() != null) return
        consumerId.set(
            karooSystem.addStreamConsumer(DataType.Type.RADAR) { state ->
                handleStreamState(state)
            },
        )
    }

    fun stop() {
        consumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
        ScoutSession.updateRadar(RadarObservation())
    }

    private fun handleStreamState(state: StreamState) {
        when (state) {
            is StreamState.Streaming -> ScoutSession.updateRadar(mapValues(state.dataPoint.values))
            is StreamState.Searching ->
                ScoutSession.updateRadar(RadarObservation(state = RadarLinkState.SCANNING))
            is StreamState.NotAvailable,
            is StreamState.Idle,
            -> ScoutSession.updateRadar(RadarObservation(state = RadarLinkState.DISCONNECTED))
        }
    }

    private fun mapValues(values: Map<String, Double>): RadarObservation {
        val radarError = values[DataType.Field.RADAR_ERROR]
        if (radarError != null && radarError > 0) {
            return RadarObservation(state = RadarLinkState.DISCONNECTED)
        }

        val ranges =
            targetRangeFields.mapNotNull { field ->
                values[field]?.toInt()?.takeIf { it > 0 }
            }

        if (ranges.isEmpty() && values[DataType.Field.RADAR_THREAT_LEVEL]?.toInt() == 0) {
            return RadarObservation(
                state = RadarLinkState.TRACKING,
                targets = emptyList(),
            )
        }

        val targets =
            ranges.map { rangeM ->
                RadarTarget(
                    occupied = true,
                    rangeM = rangeM,
                    closingSpeedMps = null,
                )
            }

        return RadarObservation(
            state = RadarLinkState.TRACKING,
            targets = targets,
        )
    }
}
