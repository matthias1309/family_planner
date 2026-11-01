package de.familyplanner.android.core.ui.avatar

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicReference

private const val DEFINITION_ASSET = "avataaars.json"

/** Loads the shared style definition from the app assets once; parsing the 125 KB file is too slow for composition. */
object AvatarDefinition {
    private val cached = AtomicReference<JsonObject?>(null)

    /** The definition if it is already loaded (no I/O), so composables can render at once on later calls. */
    fun peek(): JsonObject? = cached.get()

    fun load(context: Context): JsonObject = cached.get() ?: parse(context).also { cached.compareAndSet(null, it) }

    private fun parse(context: Context): JsonObject =
        context.applicationContext.assets.open(DEFINITION_ASSET).bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
}
