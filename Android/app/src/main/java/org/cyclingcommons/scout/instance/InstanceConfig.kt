package org.cyclingcommons.scout.instance

import android.content.Context
import org.json.JSONObject

/** Which Atlas instance this build is wired to. Bundled from Android/.env.* at compile time. */
data class InstanceConfig(
    val instanceUrl: String,
    val instanceName: String,
) {
    /** Base URL with no trailing slash — safe to append `/.well-known/scout-upload.json`. */
    val baseUrl: String get() = instanceUrl.trimEnd('/')

    /** Browser review page: choose or drop a FIT / Scout zip. */
    val reviewUrl: String get() = "$baseUrl/scout/review"
}

/** Loads `assets/instance.json` (see Android/.env.example). */
object InstanceConfigLoader {
    private const val ASSET = "instance.json"

    fun load(context: Context): InstanceConfig? = runCatching {
        context.assets.open(ASSET).use { stream ->
            parse(JSONObject(stream.readBytes().decodeToString()))
        }
    }.getOrNull()

    internal fun parse(root: JSONObject): InstanceConfig = InstanceConfig(
        instanceUrl = root.getString("instance_url"),
        instanceName = root.getString("instance_name"),
    )
}
