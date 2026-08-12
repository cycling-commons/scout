package org.cyclingcommons.scout.karoo.extension

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.karoo.R
import org.cyclingcommons.scout.karoo.session.ScoutSession

/**
 * Compact ride-page field (Garmin data-field analogue): status, tag tally, live car count.
 * Tile tagging uses [org.cyclingcommons.scout.karoo.tagging.TaggingActivity] via BonusAction.
 */
class ScoutStatusDataType(
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {
    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        emitter.onNext(UpdateGraphicConfig(showHeader = true))
        val job =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    emitter.updateView(buildRemoteViews(context))
                    delay(1000)
                }
            }
        emitter.setCancellable { job.cancel() }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.scout_status_field)
        val nowMs = System.currentTimeMillis()
        val scout = ScoutSession.controller.snapshot(nowMs)
        val radar = ScoutSession.latestRadar()

        val stateText =
            when (ScoutSession.rideState) {
                is RideState.Recording -> context.getString(R.string.ride_state_recording)
                is RideState.Paused -> context.getString(R.string.ride_state_paused)
                is RideState.Idle -> context.getString(R.string.ride_state_idle)
            }
        views.setTextViewText(R.id.scout_state, stateText)

        val tagsText =
            if (ScoutSession.controller.timer == TimerState.RUNNING) {
                context.getString(R.string.tagging_tag_count, scout.tagTotal)
            } else {
                context.getString(R.string.tagging_idle_prompt)
            }
        views.setTextViewText(R.id.scout_tags, tagsText)

        val openSurfaceLabel = scout.openSurfaceLabel
        if (openSurfaceLabel != null) {
            views.setViewVisibility(R.id.scout_open_surface, View.VISIBLE)
            views.setTextViewText(
                R.id.scout_open_surface,
                context.getString(R.string.field_surface_strip),
            )
            views.setViewVisibility(R.id.scout_radar, View.GONE)
            views.setInt(R.id.scout_root, "setBackgroundColor", OPEN_SURFACE_BG)
        } else {
            views.setViewVisibility(R.id.scout_open_surface, View.GONE)
            views.setViewVisibility(R.id.scout_radar, View.VISIBLE)
            views.setInt(R.id.scout_root, "setBackgroundColor", DEFAULT_BG)
        }

        if (openSurfaceLabel == null) {
            val radarText =
                when {
                    !radar.tracking -> context.getString(R.string.radar_none)
                    ScoutExtensionRuntime.liveCarCount() > 0 ->
                        context.getString(
                            R.string.radar_cars,
                            ScoutExtensionRuntime.liveCarCount(),
                        )
                    else -> context.getString(R.string.radar_label)
                }
            views.setTextViewText(R.id.scout_radar, radarText)
        }

        views.setTextViewText(R.id.scout_hint, context.getString(R.string.field_open_tagging_hint))
        return views
    }

    companion object {
        const val TYPE_ID = "scout-status"
        private const val DEFAULT_BG = 0xFF101418.toInt()
        private const val OPEN_SURFACE_BG = 0xFF2A1F14.toInt()
    }
}
