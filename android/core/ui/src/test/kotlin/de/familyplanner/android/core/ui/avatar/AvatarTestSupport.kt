package de.familyplanner.android.core.ui.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Shared avatar contract (`api/avatar/avataaars.json`), read straight from the repository for JVM tests. */
internal fun loadDefinition(): JsonObject {
    val file =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "api/avatar/avataaars.json") }
            .first { it.exists() }
    return Json.parseToJsonElement(file.readText()).jsonObject
}
