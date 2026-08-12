package org.cyclingcommons.scout.karoo.fit

import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import org.cyclingcommons.scout.domain.RADAR_NA

/**
 * Scout FIT developer fields on Karoo record messages — ids/names match Garmin
 * `fit_contributions.xml` and [DATA-FORMAT.md](../../../../../../../docs/DATA-FORMAT.md).
 */
object ScoutFitFields {
    /** FIT base type uint8 */
    private const val FIT_UINT8 = 2.toShort()

    val poiType =
        DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = FIT_UINT8,
            fieldName = "poi_type",
            units = "",
        )

    val poiDetail =
        DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = FIT_UINT8,
            fieldName = "poi_detail",
            units = "",
        )

    val radarCount =
        DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = FIT_UINT8,
            fieldName = "radar_count",
            units = "cars",
        )

    val radarNear =
        DeveloperField(
            fieldDefinitionNumber = 3,
            fitBaseTypeId = FIT_UINT8,
            fieldName = "radar_near",
            units = "m",
        )

    val radarSpeed =
        DeveloperField(
            fieldDefinitionNumber = 4,
            fitBaseTypeId = FIT_UINT8,
            fieldName = "radar_speed",
            units = "kph",
        )

    fun fieldValues(
        poiType: Int,
        poiDetail: Int,
        radarCount: Int,
        radarNear: Int,
        radarSpeed: Int,
    ): List<FieldValue> =
        listOf(
            FieldValue(this.poiType, poiType.toDouble()),
            FieldValue(this.poiDetail, poiDetail.toDouble()),
            FieldValue(this.radarCount, radarCount.toDouble()),
            FieldValue(this.radarNear, radarNear.toDouble()),
            FieldValue(this.radarSpeed, radarSpeed.toDouble()),
        )

    fun emptySample(): List<FieldValue> =
        fieldValues(
            poiType = 0,
            poiDetail = 0,
            radarCount = RADAR_NA,
            radarNear = RADAR_NA,
            radarSpeed = RADAR_NA,
        )
}
