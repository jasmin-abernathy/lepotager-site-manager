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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.lepotager.sitemanager.AppUiState
import org.lepotager.sitemanager.MainViewModel
import org.lepotager.sitemanager.model.ModuleActionConfig
import org.lepotager.sitemanager.model.ModuleConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun BusinessModuleRoot(state: AppUiState, module: ModuleConfig, vm: MainViewModel) {
    val site = state.site ?: return
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
        }
        BusinessNotice(state)
        HorizontalDivider()
        when (module.kind) {
            "records" -> RecordsModuleScreen(module, site.snapshot.data[module.id], state, vm)
            "calendar" -> CalendarModuleScreen(module, site.snapshot.data[module.id], state, vm)
        }
    }
}

@Composable
private fun BusinessNotice(state: AppUiState) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (state.message.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text(state.message, Modifier.fillMaxWidth().padding(11.dp))
            }
        }
        if (state.error.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                Text(
                    state.error,
                    Modifier.fillMaxWidth().padding(11.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
internal fun RecordsModuleScreen(
    module: ModuleConfig,
    data: JsonElement?,
    state: AppUiState,
    vm: MainViewModel,
) {
    val records = businessItems(data)
    var confirming by rememberSaveable(module.id) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        if (records.isEmpty()) {
            item {
                Text(
                    "Aucun élément synchronisé pour ce module.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(records, key = { recordId(it).ifBlank { it.hashCode().toString() } }) { item ->
            BusinessRecordCard(
                module = module,
                item = item,
                state = state,
                vm = vm,
                confirming = confirming,
                onConfirmingChange = { confirming = it },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun CalendarModuleScreen(
    module: ModuleConfig,
    data: JsonElement?,
    state: AppUiState,
    vm: MainViewModel,
) {
    val records = businessItems(data).sortedBy { it["start"]?.let(::businessText).orEmpty() }
    var confirming by rememberSaveable(module.id) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        if (records.isEmpty()) {
            item {
                Text(
                    "Aucun rendez-vous ou événement synchronisé.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(records, key = { recordId(it).ifBlank { it.hashCode().toString() } }) { item ->
            val start = item["start"]?.let(::businessText).orEmpty()
            val end = item["end"]?.let(::businessText).orEmpty()
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (start.isNotBlank()) {
                        Text(
                            formatBusinessDateTime(start),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(recordTitle(item), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    recordSubtitle(item).takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (end.isNotBlank()) {
                        Text("Fin : ${formatBusinessDateTime(end)}", style = MaterialTheme.typography.bodySmall)
                    }
                    RecordStatus(item)
                    RecordFields(module, item)
                    RecordActions(module, item, state, vm, confirming) { confirming = it }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BusinessRecordCard(
    module: ModuleConfig,
    item: JsonObject,
    state: AppUiState,
    vm: MainViewModel,
    confirming: String?,
    onConfirmingChange: (String?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recordTitle(item), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            recordSubtitle(item).takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RecordStatus(item)
            RecordFields(module, item)
            RecordActions(module, item, state, vm, confirming, onConfirmingChange)
        }
    }
}

@Composable
private fun RecordStatus(item: JsonObject) {
    val status = item["status"]?.let(::businessText).orEmpty()
    if (status.isNotBlank()) {
        Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordFields(module: ModuleConfig, item: JsonObject) {
    val reserved = setOf("id", "title", "subtitle", "status", "start", "end")
    module.fields.filterNot { it.id in reserved }.forEach { field ->
        val value = item[field.id]?.let(::businessText).orEmpty()
        if (value.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    field.label,
                    modifier = Modifier.weight(0.42f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, modifier = Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RecordActions(
    module: ModuleConfig,
    item: JsonObject,
    state: AppUiState,
    vm: MainViewModel,
    confirming: String?,
    onConfirmingChange: (String?) -> Unit,
) {
    val itemId = recordId(item)
    if (itemId.isBlank() || module.actions.isEmpty()) return

    module.actions.forEach { action ->
        val key = "$itemId:${action.id}"
        if (confirming == key) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        action.confirmationText ?: "Confirmer l'action « ${action.label} » ?",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                submitItemAction(module, itemId, action, vm)
                                onConfirmingChange(null)
                            },
                            enabled = !state.loading,
                        ) { Text("Confirmer") }
                        TextButton(onClick = { onConfirmingChange(null) }, enabled = !state.loading) {
                            Text("Annuler")
                        }
                    }
                }
            }
        } else {
            when (action.tone) {
                "primary" -> Button(
                    onClick = {
                        if (action.requiresConfirmation) onConfirmingChange(key)
                        else submitItemAction(module, itemId, action, vm)
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(action.label) }
                else -> OutlinedButton(
                    onClick = {
                        if (action.requiresConfirmation) onConfirmingChange(key)
                        else submitItemAction(module, itemId, action, vm)
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(action.label) }
            }
        }
    }
}

private fun submitItemAction(
    module: ModuleConfig,
    itemId: String,
    action: ModuleActionConfig,
    vm: MainViewModel,
) {
    vm.submit(
        moduleId = module.id,
        action = "item_action",
        payload = buildJsonObject {
            put("item_id", itemId)
            put("action_id", action.id)
        },
        allowOffline = action.allowOffline,
    )
}

private fun businessItems(data: JsonElement?): List<JsonObject> = when (data) {
    is JsonArray -> data.mapNotNull { it as? JsonObject }
    is JsonObject -> (data["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    else -> emptyList()
}

private fun recordId(item: JsonObject): String = item["id"]?.let(::businessText).orEmpty()

private fun recordTitle(item: JsonObject): String = item["title"]?.let(::businessText).orEmpty()
    .ifBlank { recordId(item).takeIf { it.isNotBlank() }?.let { "Élément $it" } ?: "Élément" }

private fun recordSubtitle(item: JsonObject): String = item["subtitle"]?.let(::businessText).orEmpty()

private fun businessText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> element.contentOrNull ?: element.toString()
    else -> element.toString()
}

private fun formatBusinessDateTime(raw: String): String {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())
    runCatching { return OffsetDateTime.parse(raw).format(formatter) }
    runCatching { return LocalDateTime.parse(raw).format(formatter) }
    runCatching {
        return LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    return raw.replace('T', ' ').substringBeforeLast(':').ifBlank { raw }
}
