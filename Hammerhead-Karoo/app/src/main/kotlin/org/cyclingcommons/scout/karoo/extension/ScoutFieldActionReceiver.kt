package org.cyclingcommons.scout.karoo.extension

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cyclingcommons.scout.domain.UiMode
import org.cyclingcommons.scout.karoo.session.ScoutSession

/**
 * Taps on the ride-page Scout field. The field is RemoteViews drawn by Karoo, so each
 * tile carries a PendingIntent that lands here, in our own process.
 */
class ScoutFieldActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val now = System.currentTimeMillis()
        when (intent.action) {
            ACTION_TAP_TILE -> {
                val index = intent.getIntExtra(EXTRA_TILE_INDEX, -1)
                val mode = UiMode.entries.getOrNull(intent.getIntExtra(EXTRA_MODE, -1))
                if (index >= 0 && mode != null) ScoutSession.tapTile(index, now, mode)
            }
            ACTION_END_SURFACE -> ScoutSession.endOpenSurface(now)
        }
    }

    companion object {
        private const val ACTION_TAP_TILE = "org.cyclingcommons.scout.karoo.FIELD_TAP_TILE"
        private const val ACTION_END_SURFACE = "org.cyclingcommons.scout.karoo.FIELD_END_SURFACE"
        private const val EXTRA_TILE_INDEX = "tile"
        private const val EXTRA_MODE = "mode"

        // Request codes keep one live PendingIntent per tile; extras alone don't.
        private const val END_SURFACE_REQUEST = 100

        fun tileTap(
            context: Context,
            index: Int,
            mode: UiMode,
        ): PendingIntent =
            broadcast(
                context,
                index,
                Intent(context, ScoutFieldActionReceiver::class.java)
                    .setAction(ACTION_TAP_TILE)
                    .putExtra(EXTRA_TILE_INDEX, index)
                    .putExtra(EXTRA_MODE, mode.ordinal),
            )

        fun endSurface(context: Context): PendingIntent =
            broadcast(
                context,
                END_SURFACE_REQUEST,
                Intent(context, ScoutFieldActionReceiver::class.java).setAction(ACTION_END_SURFACE),
            )

        private fun broadcast(
            context: Context,
            requestCode: Int,
            intent: Intent,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
