package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.lepotager.sitemanager.AppUiState
import org.lepotager.sitemanager.MainViewModel
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.model.UiField

@Composable
fun DiscoveryScreen(state: AppUiState, vm: MainViewModel) {
    var address by rememberSaveable { mutableStateOf("") }
    CenteredCard {
        AppTitle("Le Potager — Gestion")
        Text("Connectez directement votre site. L'application récupérera ensuite son identité, ses couleurs et les fonctions autorisées.")
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.take(240) },
            label = { Text("Adresse du site") },
            placeholder = { Text("https://monsite.fr") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Notice(state)
        Button(
            onClick = { vm.discover(address) },
            enabled = address.isNotBlank() && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Détecter mon site") }
        Text(
            "La configuration est lue sur le domaine que vous indiquez ; aucun annuaire central n'est nécessaire.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AuthScreen(state: AppUiState, vm: MainViewModel) {
    val manifest = state.manifest ?: return
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var pairCode by rememberSaveable { mutableStateOf("") }
    val passwordAuth = "password_totp" in manifest.authMethods
    val pairAuth = "pairing_code" in manifest.authMethods

    CenteredCard {
        BrandPreview(manifest.displayName, manifest.brandingPreview?.logoUrl)
        Text("Site détecté", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (passwordAuth && !pairing) {
            OutlinedTextField(username, { username = it.take(120) }, label = { Text("Identifiant du site") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Le mot de passe sert uniquement à cette connexion et n'est jamais enregistré dans l'application.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { vm.login(username, password) },
                enabled = username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Se connecter") }
        }
        if (pairAuth && pairing) {
            OutlinedTextField(
                pairCode,
                { pairCode = it.filter(Char::isDigit).take(12) },
                label = { Text("Code d'association") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.pair(pairCode) }, enabled = pairCode.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("Associer cet appareil") }
        }
        if (passwordAuth && pairAuth) {
            TextButton(onClick = { pairing = !pairing }) {
                Text(if (pairing) "Utiliser mon identifiant et mon mot de passe" else "J'ai un code d'association")
            }
        }
        Notice(state)
        TextButton(onClick = vm::backToDiscovery) { Text("Changer de site") }
    }
}

@Composable
fun TotpScreen(state: AppUiState, vm: MainViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    CenteredCard {
        AppTitle("Double authentification")
        Text("Entrez le code à 6 chiffres de votre application TOTP.")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text("Code TOTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.verifyTotp(code) }, enabled = code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Valider") }
        Notice(state)
    }
}

@Composable
fun ReadyScreen(state: AppUiState, vm: MainViewModel) {
    val site = state.site ?: return
    val selected = site.config.modules.firstOrNull { it.id == state.selectedModuleId }
    if (selected != null) {
        ModuleScreen(state, selected, vm)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SiteHeader(state, vm)
        Notice(state, Modifier.padding(horizontal = 16.dp))
        if (state.queuedCount > 0) {
            InfoCard("${state.queuedCount} modification${if (state.queuedCount > 1) "s" else ""} envoyée${if (state.queuedCount > 1) "s" else ""} hors ligne attendent le réseau.")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            items(site.config.modules.sortedBy { it.order }, key = { it.id }) { module ->
                ModuleCard(module = module, onClick = { vm.selectModule(module) })
            }
        }
    }
}

@Composable
private fun ModuleScreen(state: AppUiState, module: ModuleConfig, vm: MainViewModel) {
    val site = state.site ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.selectModule(null) }) { Text("← Retour") }
            Column(Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                module.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        HorizontalDivider()
        when (module.kind) {
            "form" -> GenericFormScreen(module, site.snapshot.data[module.id], state, vm)
            "gallery" -> GalleryModuleScreen(module, site.snapshot.data[module.id], state, vm)
            "requests" -> RequestsModuleScreen(site.snapshot.data[module.id])
            "dashboard" -> DashboardModuleScreen(module, site.snapshot.data[module.id])
            else -> UnsupportedModuleScreen(module)
        }
    }
}

@Composable
private fun GenericFormScreen(module: ModuleConfig, data: JsonElement?, state: AppUiState, vm: MainViewModel) {
    val objectData = data as? JsonObject ?: JsonObject(emptyMap())
    val values = remember(module.id, state.site?.snapshot?.revision) { mutableStateMapOf<String, String>() }

    LaunchedEffect(module.id, state.site?.snapshot?.revision) {
        module.fields.forEach { field ->
            val value = objectData[field.id]
            values[field.id] = value?.let { primitiveText(it) }.orEmpty()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(module.fields, key = { it.id }) { field ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                GenericField(field, values[field.id].orEmpty()) { values[field.id] = it }
            }
        }
        if (module.writable) {
            item {
                Button(
                    onClick = {
                        val payload = buildJsonObject {
                            module.fields.forEach { field -> put(field.id, fieldValue(field, values[field.id].orEmpty())) }
                        }
                        vm.submit(module.id, "update_fields", payload)
                    },
                    enabled = module.fields.all { validField(it, values[it.id].orEmpty()) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(if (state.site?.config?.policy?.reviewBeforePublish == true) "Envoyer pour validation" else "Enregistrer")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GalleryModuleScreen(module: ModuleConfig, data: JsonElement?, state: AppUiState, vm: MainViewModel) {
    val objectData = data as? JsonObject
    val itemsArray = objectData?.get("items") as? JsonArray ?: JsonArray(emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Les cartes et champs disponibles sont définis par votre site. Une modification reste soumise aux permissions du serveur.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(itemsArray, key = { element -> (element as? JsonObject)?.get("id")?.toString() ?: element.hashCode() }) { element ->
            val item = element as? JsonObject ?: return@items
            val id = item["id"]?.let(::primitiveText).orEmpty()
            val title = item["title"]?.let(::primitiveText).orEmpty().ifBlank { "Réalisation" }
            val category = item["category"]?.let(::primitiveText).orEmpty()
            val thumb = item["thumb"]?.let(::primitiveText).orEmpty()
            GalleryCard(title, category, thumb) {
                if (module.writable && id.isNotBlank()) {
                    vm.submit(module.id, "select_item", buildJsonObject { put("item_id", id) })
                }
            }
        }
        if (itemsArray.isEmpty()) item { InfoCard("Aucune réalisation synchronisée pour ce module.") }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RequestsModuleScreen(data: JsonElement?) {
    val array = when (data) {
        is JsonArray -> data
        is JsonObject -> data["items"] as? JsonArray ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        items(array) { element ->
            val item = element as? JsonObject ?: return@items
            val status = item["status"]?.let(::primitiveText).orEmpty()
            val title = item["title"]?.let(::primitiveText).orEmpty().ifBlank { item["kind"]?.let(::primitiveText).orEmpty() }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title.ifBlank { "Demande" }, fontWeight = FontWeight.Bold)
                        item["created_at"]?.let { Text(primitiveText(it), style = MaterialTheme.typography.bodySmall) }
                    }
                    Text(status.ifBlank { "—" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardModuleScreen(module: ModuleConfig, data: JsonElement?) {
    val objectData = data as? JsonObject ?: JsonObject(emptyMap())
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(module.subtitle.orEmpty(), Modifier.padding(16.dp)) }
        items(objectData.entries.toList(), key = { it.key }) { (key, value) ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(key, style = MaterialTheme.typography.labelMedium)
                    Text(primitiveText(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UnsupportedModuleScreen(module: ModuleConfig) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Le module « ${module.kind} » n'est pas encore pris en charge par cette version de l'application. Il a été ignoré sans exécuter de code du serveur.")
    }
}

@Composable
private fun GenericField(field: UiField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Switch(checked = value.toBooleanStrictOrNull() ?: false, onCheckedChange = { onChange(it.toString()) })
            Spacer(Modifier.width(10.dp))
            Column {
                Text(field.label, fontWeight = FontWeight.Medium)
                field.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        "single_choice" -> ChoiceField(field, value, onChange)
        else -> {
            val keyboard = when (field.type) {
                "number" -> KeyboardType.Decimal
                "email" -> KeyboardType.Email
                "url" -> KeyboardType.Uri
                else -> KeyboardType.Text
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next -> onChange(field.maxLength?.let { next.take(it) } ?: next) },
                label = { Text(field.label) },
                supportingText = field.hint?.let { hint -> { Text(hint) } },
                minLines = if (field.type == "multiline") 4 else 1,
                maxLines = if (field.type == "multiline") 12 else 1,
                singleLine = field.type != "multiline",
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                isError = !validField(field, value),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceField(field: UiField, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = field.choices.firstOrNull { it.value == value }?.label ?: field.label
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            field.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label) }, onClick = { onChange(choice.value); open = false })
            }
        }
    }
}

@Composable
private fun SiteHeader(state: AppUiState, vm: MainViewModel) {
    val site = state.site ?: return
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            site.config.branding.logoUrl?.takeIf { it.startsWith("https://") }?.let {
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(site.config.site.displayName, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Gestion du site", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { vm.refresh() }) { Text("Actualiser") }
            TextButton(onClick = { vm.disconnect() }) { Text("Déconnecter") }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleConfig, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(6.dp).height(154.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            module.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.weight(1f))
            Text(if (module.writable) "Modifiable" else "Lecture", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GalleryCard(title: String, category: String, thumb: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thumb.startsWith("https://")) {
                AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.size(96.dp))
            } else {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Text("Photo") }
            }
            Column(Modifier.padding(14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                if (category.isNotBlank()) Text(category, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CenteredCard(content: @Composable Column.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
        }
    }
}

@Composable
private fun AppTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
}

@Composable
private fun BrandPreview(name: String, logo: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (logo?.startsWith("https://") == true) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.size(54.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Notice(state: AppUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (state.message.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text(state.message, Modifier.fillMaxWidth().padding(11.dp))
            }
        }
        if (state.error.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                Text(state.error, Modifier.fillMaxWidth().padding(11.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private fun primitiveText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> element.contentOrNull.orEmpty()
    else -> element.toString()
}

private fun fieldValue(field: UiField, value: String): JsonPrimitive = when (field.type) {
    "boolean" -> JsonPrimitive(value.toBooleanStrictOrNull() ?: false)
    "number" -> value.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
    else -> JsonPrimitive(value)
}

private fun validField(field: UiField, value: String): Boolean {
    if (field.required && value.isBlank()) return false
    if (field.maxLength != null && value.length > field.maxLength) return false
    if (field.type == "number" && value.isNotBlank()) {
        val number = value.toDoubleOrNull() ?: return false
        if (field.min != null && number < field.min) return false
        if (field.max != null && number > field.max) return false
    }
    if (field.type == "single_choice" && value.isNotBlank() && field.choices.none { it.value == value }) return false
    return true
}
