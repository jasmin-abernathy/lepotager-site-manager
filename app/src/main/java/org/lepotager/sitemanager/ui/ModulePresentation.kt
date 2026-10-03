package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.lepotager.sitemanager.model.ModuleConfig

internal data class FieldSectionSpec(
    val id: String,
    val title: String,
    val description: String?,
    val fieldIds: List<String>,
)

internal data class ModuleGroupSpec(
    val id: String,
    val title: String,
    val description: String?,
    val order: Int,
)

internal fun moduleFieldSections(module: ModuleConfig): List<FieldSectionSpec> {
    val raw = module.options["field_sections"] as? JsonArray ?: return emptyList()
    if (raw.isEmpty() || raw.size > 20) return emptyList()

    val known = module.fields.map { it.id }.toSet()
    if (known.isEmpty()) return emptyList()
    val seenSections = mutableSetOf<String>()
    val seenFields = mutableSetOf<String>()
    val result = mutableListOf<FieldSectionSpec>()

    for (element in raw) {
        val section = element as? JsonObject ?: return emptyList()
        val id = (section["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val title = (section["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val description = (section["description"] as? JsonPrimitive)
            ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val fieldArray = section["field_ids"] as? JsonArray ?: return emptyList()
        if (id.isEmpty() || title.isEmpty() || !seenSections.add(id) || fieldArray.isEmpty()) return emptyList()

        val fieldIds = mutableListOf<String>()
        for (fieldElement in fieldArray) {
            val fieldId = (fieldElement as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (fieldId !in known || !seenFields.add(fieldId)) return emptyList()
            fieldIds += fieldId
        }
        result += FieldSectionSpec(id, title, description, fieldIds)
    }

    return result.takeIf { seenFields == known } ?: emptyList()
}

internal fun moduleGroupSpec(module: ModuleConfig): ModuleGroupSpec? {
    val id = (module.options["group_id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val title = (module.options["group_title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (id.isEmpty() || title.isEmpty()) return null
    val description = (module.options["group_description"] as? JsonPrimitive)
        ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val order = (module.options["group_order"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    return ModuleGroupSpec(id, title, description, order)
}

internal fun sortModulesForHome(modules: List<ModuleConfig>): List<ModuleConfig> =
    modules.sortedWith(
        compareBy<ModuleConfig>(
            { moduleGroupSpec(it)?.order ?: 0 },
            { moduleGroupSpec(it)?.id.orEmpty() },
            { it.order },
            { it.title },
        ),
    )
