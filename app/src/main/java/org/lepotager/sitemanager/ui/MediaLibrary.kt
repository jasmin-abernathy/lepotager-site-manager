package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.lepotager.sitemanager.AppUiState
import org.lepotager.sitemanager.MainViewModel
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.model.UiField

/**
 * Bibliothèque média générique pilotée par le serveur. L'application ne connaît pas
 * BMH : elle attend seulement des items avec id, parent_id, thumb/path et les champs
 * annoncés dans ModuleConfig.fields.
 */
@Composable
internal fun MediaLibraryRoot(state: AppUiState, module: ModuleConfig, vm: MainViewModel) {
    val site = state.site ?: return
    val objectData = site.snapshot.data[module.id] as? JsonObject
    val mediaItems = (objectData?.get("items") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.selectModule(null) }, enabled = !state.loading) { Text("← Retour") }
            Column(Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                module.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = { vm.refresh() }, enabled = !state.loading) { Text("Actualiser") }
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${mediaItems.size} photo${if (mediaItems.size > 1) "s" else ""} synchronisée${if (mediaItems.size > 1) "s" else ""}. Les rotations modifient une copie gérée par le site ; les fichiers historiques restent intacts.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mediaItems.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Aucune photo remontée par le site.", Modifier.padding(16.dp))
                    }
                }
            }
            items(mediaItems, key = { mediaText(it["id"]).ifBlank { it.hashCode().toString() } }) { item ->
                MediaLibraryCard(module, item, state, vm)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MediaLibraryCard(
    module: ModuleConfig,
    item: JsonObject,
    state: AppUiState,
    vm: MainViewModel,
) {
    val itemId = mediaText(item["id"])
    val parentId = mediaText(item["parent_id"])
    val title = mediaText(item["title"]).ifBlank { "Photo" }
    val subtitle = mediaText(item["subtitle"])
    val thumb = mediaText(item["thumb"]).ifBlank { mediaText(item["path"]) }
    val values = remember(itemId, thumb, state.site?.snapshot?.revision) { mutableStateMapOf<String, String>() }
    var confirmDelete by rememberSaveable(itemId) { mutableStateOf(false) }

    LaunchedEffect(itemId, thumb, state.site?.snapshot?.revision) {
        module.fields.forEach { field ->
            values[field.id] = item[field.id]?.let(::mediaText) ?: mediaDefault(field)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (thumb.startsWith("https://")) {
                AsyncImage(
                    model = thumb,
                    contentDescription = values["alt"].orEmpty().ifBlank { title },
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (module.writable) {
                module.fields.forEach { field ->
                    MediaField(field, values[field.id].orEmpty()) { values[field.id] = it }
                }

                Button(
                    onClick = {
                        vm.submit(
                            moduleId = module.id,
                            action = "update_item",
                            payload = buildJsonObject {
                                put("item_id", itemId)
                                put("parent_id", parentId)
                                module.fields.forEach { field -> put(field.id, mediaFieldValue(field, values[field.id].orEmpty())) }
                            },
                            allowOffline = false,
                        )
                    },
                    enabled = !state.loading && itemId.isNotBlank() && parentId.isNotBlank() && module.fields.all { mediaValid(it, values[it.id].orEmpty()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enregistrer la photo") }

                if (mediaOption(module, "allow_rotate")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                vm.submit(
                                    module.id,
                                    "rotate_left",
                                    buildJsonObject { put("item_id", itemId); put("parent_id", parentId) },
                                    allowOffline = false,
                                )
                            },
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f),
                        ) { Text("↶ Gauche") }
                        OutlinedButton(
                            onClick = {
                                vm.submit(
                                    module.id,
                                    "rotate_right",
                                    buildJsonObject { put("item_id", itemId); put("parent_id", parentId) },
                                    allowOffline = false,
                                )
                            },
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f),
                        ) { Text("↷ Droite") }
                    }
                }

                if (mediaOption(module, "allow_delete")) {
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }, enabled = !state.loading) {
                            Text("Retirer cette photo")
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Retirer cette photo de la réalisation ?",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            vm.submit(
                                                module.id,
                                                "delete_item",
                                                buildJsonObject { put("item_id", itemId); put("parent_id", parentId) },
                                                allowOffline = false,
                                            )
                                            confirmDelete = false
                                        },
                                        enabled = !state.loading,
                                    ) { Text("Confirmer") }
                                    TextButton(onClick = { confirmDelete = false }, enabled = !state.loading) { Text("Annuler") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaField(field: UiField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        "single_choice" -> {
            var open by remember { mutableStateOf(false) }
            val selected = field.choices.firstOrNull { it.value == value }?.label ?: field.label
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected, modifier = Modifier.weight(1f))
                    Text("⌄")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    field.choices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = { onChange(choice.value); open = false },
                        )
                    }
                }
            }
        }
        else -> {
            val keyboard = when (field.type) {
                "number" -> KeyboardType.Number
                "email" -> KeyboardType.Email
                else -> KeyboardType.Text
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next -> onChange(field.maxLength?.let { next.take(it) } ?: next) },
                label = { Text(field.label) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                singleLine = true,
                isError = !mediaValid(field, value),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun mediaText(element: JsonElement?): String = when (element) {
    is JsonPrimitive -> element.contentOrNull ?: element.toString()
    null -> ""
    else -> element.toString()
}

private fun mediaDefault(field: UiField): String = when (field.type) {
    "single_choice" -> field.choices.firstOrNull()?.value.orEmpty()
    "boolean" -> "false"
    else -> ""
}

private fun mediaFieldValue(field: UiField, value: String): JsonPrimitive = when (field.type) {
    "boolean" -> JsonPrimitive(value.toBooleanStrictOrNull() ?: false)
    "number" -> value.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
    else -> JsonPrimitive(value)
}

private fun mediaValid(field: UiField, value: String): Boolean {
    if (field.required && value.isBlank()) return false
    if (field.maxLength != null && value.length > field.maxLength) return false
    if (field.type == "number" && value.isNotBlank()) {
        val number = value.toDoubleOrNull() ?: return false
        if (field.min != null && number < field.min) return false
        if (field.max != null && number > field.max) return false
    }
    if (field.type == "single_choice" && field.choices.none { it.value == value }) return false
    return true
}

private fun mediaOption(module: ModuleConfig, key: String): Boolean =
    module.options[key]?.jsonPrimitive?.booleanOrNull ?: false
