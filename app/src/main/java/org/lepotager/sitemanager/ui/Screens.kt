package org.lepotager.sitemanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
        AppTitle("Mon Manager Web")
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
        Button(onClick = { vm.discover(address) }, enabled = !state.loading && address.isNotBlank()) {
            Text(if (state.loading) "Connexion…" else "Continuer")
        }
    }
}

@Composable
fun AuthScreen(state: AppUiState, vm: MainViewModel) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    val manifest = state.manifest ?: return
    CenteredCard {
        AppTitle(manifest.displayName)
        Text("Associez cet appareil à votre espace de gestion.")
        if ("password" in manifest.authMethods) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(80) },
                label = { Text("Identifiant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(200) },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.login(username, password) },
                enabled = !state.loading && username.isNotBlank() && password.isNotBlank(),
            ) { Text("Se connecter") }
        }
        if ("pairing_code" in manifest.authMethods) {
            HorizontalDivider()
            Text("Ou utilisez le code d’association affiché dans le back-office.")
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it.filter(Char::isDigit).take(8) },
                label = { Text("Code d’association") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.pair(pairingCode) },
                enabled = !state.loading && pairingCode.length == 8,
            ) { Text("Associer l’appareil") }
        }
        TextButton(onClick = vm::backToDiscovery) { Text("Changer de site") }
    }
}

@Composable
fun TotpScreen(state: AppUiState, vm: MainViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    CenteredCard {
        AppTitle("Double authentification")
        Text("Entrez le code à 6 chiffres de votre application d’authentification.")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text("Code TOTP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.verifyTotp(code) }, enabled = !state.loading && code.length == 6) {
            Text("Valider")
        }
    }
}

@Composable
fun ReadyScreen(state: AppUiState, vm: MainViewModel) {
    val site = state.site ?: return
    val modules = site.config.modules.sortedBy { it.order }
    val selected = site.config.modules.firstOrNull { it.id == state.selectedModuleId }

    if (selected != null) {
        ModuleScreen(selected, site.snapshot.data[selected.id], state, vm)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppTitle(site.config.site.displayName)
            Text("Gestion du site", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (state.queuedCount > 0) {
                Text("${state.queuedCount} modification(s) en attente de réseau.", color = MaterialTheme.colorScheme.tertiary)
            }
        }
        items(modules, key = { it.id }) { module ->
            Card(
                onClick = { vm.selectModule(module) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    module.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refresh() }) { Text("Actualiser") }
                TextButton(onClick = vm::disconnect) { Text("Déconnecter") }
            }
        }
    }
}

@Composable
private fun ModuleScreen(module: ModuleConfig, data: JsonElement?, state: AppUiState, vm: MainViewModel) {
    when (module.kind) {
        "gallery" -> GalleryModuleScreen(module, data, state, vm)
        else -> GenericFormModuleScreen(module, data, state, vm)
    }
}

@Composable
private fun GenericFormModuleScreen(module: ModuleConfig, data: JsonElement?, state: AppUiState, vm: MainViewModel) {
    val objectData = data as? JsonObject ?: JsonObject(emptyMap())
    val values = remember(module.id, objectData) {
        mutableStateMapOf<String, String>().apply {
            module.fields.forEach { field -> put(field.id, objectData[field.id]?.jsonPrimitive?.contentOrNull.orEmpty()) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = { vm.selectModule(null) }) { Text("← Retour") }
            AppTitle(module.title)
            module.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(module.fields, key = { it.id }) { field ->
            GenericField(field, values[field.id].orEmpty()) { values[field.id] = it }
        }
        if (module.writable) {
            item {
                Button(
                    onClick = {
                        val payload = buildJsonObject {
                            module.fields.forEach { field -> put(field.id, JsonPrimitive(values[field.id].orEmpty())) }
                        }
                        vm.submit(module.id, "update", payload)
                    },
                    enabled = !state.loading,
                ) { Text("Enregistrer") }
            }
        }
    }
}

@Composable
private fun GenericField(field: UiField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = value == "true" || value == "1", onCheckedChange = { onChange(it.toString()) })
            Text(field.label)
        }
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(field.choices.firstOrNull { it.value == value }?.label ?: field.label)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.choices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = { onChange(choice.value); expanded = false },
                        )
                    }
                }
            }
        }
        else -> {
            val keyboard = when (field.type) {
                "number" -> KeyboardType.Decimal
                "email" -> KeyboardType.Email
                "url" -> KeyboardType.Uri
                else -> KeyboardType.Text
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next -> onChange(field.maxLength?.let(next::take) ?: next) },
                label = { Text(field.label) },
                supportingText = field.hint?.let { hint -> ({ Text(hint) }) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
                minLines = if (field.type == "textarea") 4 else 1,
            )
        }
    }
}

@Composable
private fun GalleryModuleScreen(module: ModuleConfig, data: JsonElement?, state: AppUiState, vm: MainViewModel) {
    val objectData = data as? JsonObject
    val itemsData = objectData?.get("items") as? JsonArray ?: JsonArray(emptyList())
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (editing != null || creating) {
        GalleryEditor(module, editing, state, vm) { editing = null; creating = false }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = { vm.selectModule(null) }) { Text("← Retour") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppTitle(module.title)
            if (module.writable) Button(onClick = { creating = true }) { Text("Ajouter") }
        }
        module.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(itemsData, key = { it.jsonPrimitive.contentOrNull ?: it.hashCode() }) { element ->
                val item = element as? JsonObject ?: return@items
                val title = item["title"]?.jsonPrimitive?.contentOrNull ?: "Réalisation"
                val thumb = item["thumbnail_url"]?.jsonPrimitive?.contentOrNull
                Card(onClick = { editing = item }) {
                    Column {
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = title,
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                            )
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryEditor(module: ModuleConfig, item: JsonObject?, state: AppUiState, vm: MainViewModel, onClose: () -> Unit) {
    val isNew = item == null
    val values = remember(item) {
        mutableStateMapOf<String, String>().apply {
            module.fields.forEach { field -> put(field.id, item?.get(field.id)?.jsonPrimitive?.contentOrNull.orEmpty()) }
        }
    }
    val itemId = item?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
    var selectedMediaUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedMediaUri = uri }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onClose) { Text("← Retour") }; AppTitle(if (isNew) "Nouvelle réalisation" else "Modifier la réalisation") }
        items(module.fields, key = { it.id }) { field -> GenericField(field, values[field.id].orEmpty()) { values[field.id] = it } }
        item {
            Button(
                onClick = {
                    val payload = buildJsonObject {
                        module.fields.forEach { field -> put(field.id, JsonPrimitive(values[field.id].orEmpty())) }
                        if (!isNew) put("id", JsonPrimitive(itemId))
                    }
                    vm.submit(module.id, if (isNew) "create_item" else "update_item", payload)
                    onClose()
                },
                enabled = !state.loading,
            ) { Text(if (isNew) "Créer" else "Enregistrer") }
        }
        if (!isNew && module.media?.uploadEnabled == true) {
            item {
                HorizontalDivider()
                Text("Ajouter une photo", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { launcher.launch("image/*") }) { Text("Choisir une image") }
                selectedMediaUri?.let { uri ->
                    Button(
                        onClick = { vm.uploadMedia(module.id, itemId, uri, JsonObject(emptyMap())); selectedMediaUri = null },
                        enabled = !state.loading,
                    ) { Text("Envoyer cette photo") }
                }
            }
        }
        if (!isNew) {
            item {
                TextButton(
                    onClick = {
                        vm.submit(module.id, "delete_item", buildJsonObject { put("id", JsonPrimitive(itemId)) }, allowOffline = false)
                        onClose()
                    },
                ) { Text("Supprimer la réalisation", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CenteredCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AppTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}
