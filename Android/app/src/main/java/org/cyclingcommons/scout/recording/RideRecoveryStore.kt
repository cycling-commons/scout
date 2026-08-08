package org.cyclingcommons.scout.recording

import android.content.Context
import androidx.core.content.edit
import org.cyclingcommons.scout.domain.QueuedTag
import org.cyclingcommons.scout.domain.RideSessionSnapshot
import org.cyclingcommons.scout.domain.TagTalliesSnapshot
import org.cyclingcommons.scout.domain.TimerState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists an in-progress ride so a process kill can offer resume on cold start.
 * Cleared when the rider stops normally or discards the partial file.
 */
class RideRecoveryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        fitPath: String,
        session: RideSessionSnapshot,
    ) {
        prefs.edit {
            putString(KEY_FIT_PATH, fitPath)
            putString(KEY_SESSION, encode(session))
        }
    }

    fun load(): PendingRecovery? {
        val fitPath = prefs.getString(KEY_FIT_PATH, null) ?: return null
        val sessionJson = prefs.getString(KEY_SESSION, null) ?: return null
        val file = File(fitPath)
        if (!file.isFile) {
            clear()
            return null
        }
        val session = decode(sessionJson) ?: run {
            clear()
            return null
        }
        if (session.timer == TimerState.IDLE) {
            clear()
            return null
        }
        return PendingRecovery(file = file, session = session)
    }

    fun clear() {
        prefs.edit {
            remove(KEY_FIT_PATH)
            remove(KEY_SESSION)
        }
    }

    data class PendingRecovery(
        val file: File,
        val session: RideSessionSnapshot,
    )

    internal companion object {
        const val PREFS = "scout_ride_recovery"
        const val KEY_FIT_PATH = "fit_path"
        const val KEY_SESSION = "session_json"

        fun encode(session: RideSessionSnapshot): String {
            val root = JSONObject()
            root.put("timer", session.timer.name)
            root.put("elapsedMs", session.elapsedMs)
            root.put("sampleCount", session.sampleCount)
            root.put("carCount", session.carCount)
            root.put("lastCarSpeedKph", session.lastCarSpeedKph)
            root.put("queue", JSONArray().apply {
                session.queuedTags.forEach { tag ->
                    put(JSONArray().put(tag.type).put(tag.detail))
                }
            })
            val tallies = session.tallies
            root.put("counts", JSONArray(tallies.counts.toList()))
            root.put("dangerDetails", JSONArray(tallies.dangerDetails.toList()))
            root.put("closureDetails", JSONArray(tallies.closureDetails.toList()))
            root.put("surfaceDetails", JSONArray(tallies.surfaceDetails.toList()))
            root.put("sceneryDetails", JSONArray(tallies.sceneryDetails.toList()))
            root.put("resupplyDetails", JSONArray(tallies.resupplyDetails.toList()))
            root.put("lastTapType", tallies.lastTapType)
            root.put("lastTapDetail", tallies.lastTapDetail)
            root.put("lastTapAtMs", tallies.lastTapAtMs)
            root.put("openSurfaceDetail", tallies.openSurfaceDetail)
            return root.toString()
        }

        fun decode(json: String): RideSessionSnapshot? =
            try {
                val root = JSONObject(json)
                val timer = TimerState.valueOf(root.getString("timer"))
                if (timer == TimerState.IDLE) return null
                val queue = buildList {
                    val arr = root.getJSONArray("queue")
                    for (i in 0 until arr.length()) {
                        val pair = arr.getJSONArray(i)
                        add(QueuedTag(pair.getInt(0), pair.getInt(1)))
                    }
                }
                val tallies = TagTalliesSnapshot(
                    counts = root.getJSONArray("counts").toIntArray(),
                    dangerDetails =
                        if (root.has("dangerDetails")) {
                            root.getJSONArray("dangerDetails").toIntArray()
                        } else {
                            IntArray(6)
                        },
                    closureDetails = root.getJSONArray("closureDetails").toIntArray(),
                    surfaceDetails = root.getJSONArray("surfaceDetails").toIntArray(),
                    sceneryDetails =
                        if (root.has("sceneryDetails")) {
                            root.getJSONArray("sceneryDetails").toIntArray()
                        } else {
                            IntArray(7)
                        },
                    resupplyDetails =
                        if (root.has("resupplyDetails")) {
                            root.getJSONArray("resupplyDetails").toIntArray()
                        } else {
                            IntArray(4)
                        },
                    lastTapType = root.getInt("lastTapType"),
                    lastTapDetail = root.getInt("lastTapDetail"),
                    lastTapAtMs = root.getLong("lastTapAtMs"),
                    openSurfaceDetail = root.getInt("openSurfaceDetail"),
                )
                RideSessionSnapshot(
                    timer = timer,
                    queuedTags = queue,
                    tallies = tallies,
                    elapsedMs = root.getLong("elapsedMs"),
                    sampleCount = root.getLong("sampleCount"),
                    carCount = root.getInt("carCount"),
                    lastCarSpeedKph = root.getInt("lastCarSpeedKph"),
                )
            } catch (_: Exception) {
                null
            }

        private fun JSONArray.toIntArray(): IntArray =
            IntArray(length()) { i -> getInt(i) }

        /** Test-only accessors for JSON round-trip without Android context. */
        internal fun encodeForTest(session: RideSessionSnapshot): String = encode(session)

        internal fun decodeForTest(json: String): RideSessionSnapshot? = decode(json)
    }
}
