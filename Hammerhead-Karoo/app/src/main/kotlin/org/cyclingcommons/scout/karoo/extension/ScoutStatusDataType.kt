package org.cyclingcommons.scout.karoo.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.ScoutUiState
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.UiMode
import org.cyclingcommons.scout.karoo.R
import org.cyclingcommons.scout.karoo.session.ScoutSession
import org.cyclingcommons.scout.karoo.tagging.TaggingActivity

/**
 * Ride-page Scout field (Garmin data-field analogue). Given half a page or more it is
 * the tag grid itself, tapped in place; in a smaller slot it shows status and a tap
 * opens [TaggingActivity] (same screen as the Tag ride BonusAction).
 */
class ScoutStatusDataType(
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {
    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        val showGrid = config.gridSize.second >= GRID_MIN_ROWS
        emitter.onNext(UpdateGraphicConfig(showHeader = !showGrid))
        val job =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val seen = ScoutSession.redraw.value
                    val nowMs = System.currentTimeMillis()
                    val views =
                        if (showGrid) {
                            buildTagGrid(context, nowMs, interactive = !config.preview)
                        } else {
                            buildStatus(context, interactive = !config.preview)
                        }
                    emitter.updateView(views)
                    // Taps and picker commits bump redraw; countdowns need a quicker beat.
                    val animating = showGrid && ScoutSession.withController { it.needsTick(nowMs) }
                    withTimeoutOrNull(if (animating) ANIMATION_INTERVAL_MS else IDLE_INTERVAL_MS) {
                        ScoutSession.redraw.first { it != seen }
                    }
                }
            }
        emitter.setCancellable { job.cancel() }
    }

    private fun buildTagGrid(
        context: Context,
        nowMs: Long,
        interactive: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.scout_tag_field)
        val scout = ScoutSession.withController { it.snapshot(nowMs) }

        views.setTextViewText(R.id.field_status, gridStatusText(context, scout))
        if (scout.title != null) {
            views.setViewVisibility(R.id.field_title, View.VISIBLE)
            views.setTextViewText(R.id.field_title, scout.title)
        } else {
            views.setViewVisibility(R.id.field_title, View.GONE)
        }

        val tiles = scout.tiles
        val rows = (tiles.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        TILE_ROW_IDS.forEachIndexed { row, id ->
            views.setViewVisibility(id, if (row < rows) View.VISIBLE else View.GONE)
        }
        TILE_IDS.forEachIndexed { i, id ->
            if (i >= tiles.size) {
                views.setViewVisibility(id, View.INVISIBLE)
                return@forEachIndexed
            }
            views.setViewVisibility(id, View.VISIBLE)
            val tile = tiles[i]
            val lit = i == scout.flashIdx || (scout.mode != UiMode.GRID && i == scout.pendingIdx)
            val openStretch =
                scout.openSurfaceLabel?.takeIf {
                    scout.mode == UiMode.GRID && tile.code == PoiType.SURFACE
                }
            val untilMs =
                when {
                    i == scout.flashIdx && scout.flashUntilMs > nowMs -> scout.flashUntilMs
                    scout.mode != UiMode.GRID && i == scout.pendingIdx && scout.pendingUntilMs > nowMs ->
                        scout.pendingUntilMs
                    else -> 0L
                }
            val count = scout.tileCounts.getOrElse(i) { 0 }
            val extras =
                listOfNotNull(
                    count.takeIf { it > 0 }?.toString(),
                    untilMs.takeIf { it > 0L }?.let { "${((it - nowMs + 999L) / 1000L).coerceAtLeast(1)}s" },
                )
            val text =
                listOfNotNull(
                    openStretch?.let { tile.label },
                    openStretch ?: tile.label,
                    extras.takeIf { it.isNotEmpty() }?.joinToString("  "),
                ).joinToString("\n")
            views.setTextViewText(id, text)

            val color = OPAQUE or (tile.rgb and 0xFFFFFF)
            val filled = lit || openStretch != null
            views.setInt(id, "setBackgroundColor", if (filled) color else idleTint(color))
            views.setTextColor(
                id,
                when {
                    !filled -> TEXT_PRIMARY
                    Color.luminance(color) > PALE_TILE_LUMINANCE -> TEXT_PRIMARY
                    else -> TEXT_ON_BRAND
                },
            )
            if (interactive) {
                views.setOnClickPendingIntent(id, ScoutFieldActionReceiver.tileTap(context, i, scout.mode))
            }
        }

        val openSurfaceLabel = scout.openSurfaceLabel
        if (openSurfaceLabel != null) {
            views.setTextViewText(
                R.id.field_footer,
                context.getString(R.string.surface_open_banner, openSurfaceLabel) + "  " +
                    context.getString(R.string.surface_open_action).uppercase(),
            )
            views.setInt(R.id.field_footer, "setBackgroundColor", BRAND)
            views.setTextColor(R.id.field_footer, TEXT_ON_BRAND)
            if (interactive) {
                views.setOnClickPendingIntent(R.id.field_footer, ScoutFieldActionReceiver.endSurface(context))
            }
        } else {
            views.setTextViewText(R.id.field_footer, radarText(context))
        }
        return views
    }

    private fun gridStatusText(
        context: Context,
        scout: ScoutUiState,
    ): String {
        val state =
            when (scout.timer) {
                TimerState.RUNNING -> R.string.ride_state_recording
                TimerState.PAUSED -> R.string.ride_state_paused
                TimerState.IDLE -> return context.getString(R.string.tagging_idle_prompt)
            }
        return context.getString(R.string.tagging_tag_count, scout.tagTotal) + "  " +
            context.getString(state).uppercase()
    }

    private fun radarText(context: Context): String {
        val radar = ScoutSession.latestRadar()
        return when {
            radar.tracking -> {
                val cars = context.getString(R.string.radar_cars, ScoutExtensionRuntime.liveCarCount())
                val speed = ScoutExtensionRuntime.liveCarSpeedKph()
                if (speed >= 0) "$cars  " + context.getString(R.string.radar_speed_kph, speed) else cars
            }
            radar.state == RadarLinkState.SCANNING || radar.state == RadarLinkState.CONNECTING ->
                context.getString(R.string.radar_label).uppercase() + "  " +
                    context.getString(R.string.radar_connecting)
            else ->
                context.getString(R.string.radar_label).uppercase() + "  " +
                    context.getString(R.string.radar_none)
        }
    }

    private fun buildStatus(
        context: Context,
        interactive: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.scout_status_field)
        val nowMs = System.currentTimeMillis()
        val scout = ScoutSession.withController { it.snapshot(nowMs) }
        val radar = ScoutSession.latestRadar()

        val stateText =
            when (ScoutSession.rideState) {
                is RideState.Recording -> context.getString(R.string.ride_state_recording)
                is RideState.Paused -> context.getString(R.string.ride_state_paused)
                is RideState.Idle -> context.getString(R.string.ride_state_idle)
            }
        views.setTextViewText(R.id.scout_state, stateText)

        val tagsText =
            if (scout.timer == TimerState.RUNNING) {
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
                    radar.tracking && ScoutExtensionRuntime.liveCarCount() > 0 ->
                        context.getString(
                            R.string.radar_cars,
                            ScoutExtensionRuntime.liveCarCount(),
                        )
                    radar.tracking -> context.getString(R.string.radar_label)
                    radar.state == RadarLinkState.SCANNING ||
                        radar.state == RadarLinkState.CONNECTING ->
                        context.getString(R.string.radar_connecting)
                    else -> context.getString(R.string.radar_none)
                }
            views.setTextViewText(R.id.scout_radar, radarText)
        }

        views.setTextViewText(R.id.scout_hint, context.getString(R.string.field_open_tagging_hint))
        if (interactive) {
            views.setOnClickPendingIntent(R.id.scout_root, openTaggingIntent(context))
        }
        return views
    }

    private fun openTaggingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, TaggingActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Opaque equivalent of the tag screen's 22% tile wash over its #F0F0F0 page. */
    private fun idleTint(color: Int): Int {
        fun mix(channel: (Int) -> Int) =
            (channel(color) * IDLE_ALPHA + channel(SCREEN_BG) * (1 - IDLE_ALPHA)).toInt()
        return Color.rgb(mix(Color::red), mix(Color::green), mix(Color::blue))
    }

    companion object {
        const val TYPE_ID = "scout-status"

        /** Karoo pages are 60 grid rows tall; from half a page up the tiles are big enough to hit. */
        private const val GRID_MIN_ROWS = 30
        private const val GRID_COLUMNS = 2
        private const val IDLE_INTERVAL_MS = 1000L
        private const val ANIMATION_INTERVAL_MS = 500L

        private val TILE_IDS =
            intArrayOf(
                R.id.tile_0, R.id.tile_1, R.id.tile_2, R.id.tile_3, R.id.tile_4,
                R.id.tile_5, R.id.tile_6, R.id.tile_7, R.id.tile_8, R.id.tile_9,
            )
        private val TILE_ROW_IDS =
            intArrayOf(R.id.tile_row_0, R.id.tile_row_1, R.id.tile_row_2, R.id.tile_row_3, R.id.tile_row_4)

        // Mirrors ScoutKarooColors / PALE_TILE_LUMINANCE (Compose types aren't usable in RemoteViews).
        private const val OPAQUE = 0xFF000000.toInt()
        private const val SCREEN_BG = 0xFFF0F0F0.toInt()
        private const val BRAND = 0xFFF0311B.toInt()
        private const val TEXT_PRIMARY = 0xFF1A1A1A.toInt()
        private const val TEXT_ON_BRAND = 0xFFFFFFFF.toInt()
        private const val IDLE_ALPHA = 0.22f
        private const val PALE_TILE_LUMINANCE = 0.3f

        private const val DEFAULT_BG = 0xFF101418.toInt()
        private const val OPEN_SURFACE_BG = 0xFF2A1F14.toInt()
    }
}
