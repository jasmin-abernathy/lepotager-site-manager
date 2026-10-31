package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.lepotager.sitemanager.model.ModuleConfig

/**
 * Generic MMW v1 optimistic concurrency for modules that opt into it.
 * No site or client name is hardcoded in the Android app.
 */
internal fun hasRecordVersion(module: ModuleConfig, item: JsonObject): Boolean =
    !optionBoolean(module, "require_version") ||
        ((item["version"] as? JsonPrimitive)?.intOrNull?.let { it > 0 } == true)

internal fun recordIdentityPayload(module: ModuleConfig, item: JsonObject): JsonObject {
    val id=(item["id"] as? JsonPrimitive)?.content.orEmpty()
    require(id.isNotBlank()) { "Identifiant de contenu manquant." }
    return buildJsonObject {
        put("item_id", id)
        if (optionBoolean(module, "require_version")) {
            val version=(item["version"] as? JsonPrimitive)?.intOrNull
                ?.takeIf { it > 0 } ?: throw IllegalArgumentException("Version du contenu manquante : actualisez la page.")
            put("expected_version", version)
        }
    }
}
