package org.lepotager.sitemanager

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.repository.SiteRepository

enum class AppStage { DISCOVERY, AUTH, TOTP, READY }

data class AppUiState(
    val stage: AppStage = AppStage.DISCOVERY,
    val loading: Boolean = false,
    val manifest: DiscoveryManifest? = null,
    val site: SiteRepository.RestoredSite? = null,
    val challengeId: String? = null,
    val selectedModuleId: String? = null,
    val queuedCount: Int = 0,
    val message: String = "",
    val error: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as SiteManagerApplication).repository
    private val _state = MutableStateFlow(AppUiState(loading = true))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = repository.restoreActive()
            _state.value = if (restored == null) {
                AppUiState(stage = AppStage.DISCOVERY)
            } else {
                AppUiState(
                    stage = AppStage.READY,
                    manifest = restored.manifest,
                    site = restored,
                    queuedCount = repository.queuedCount(),
                )
            }
            if (restored != null) refresh(silent = true)
        }
    }

    fun discover(address: String) = launch {
        val manifest = repository.discover(address)
        _state.value = _state.value.copy(
            stage = AppStage.AUTH,
            manifest = manifest,
            site = null,
            challengeId = null,
            error = "",
        )
    }

    fun login(username: String, password: String) = launch {
        val manifest = requireNotNull(_state.value.manifest)
        val response = repository.startAuth(manifest, username.trim(), password, deviceName())
        when (response.status) {
            "authenticated" -> {
                val token = response.deviceToken ?: error("Jeton absent dans la réponse.")
                finishAuthentication(manifest, token)
            }
            "mfa_required" -> {
                val challenge = response.challengeId ?: error("Challenge TOTP absent.")
                _state.value = _state.value.copy(stage = AppStage.TOTP, challengeId = challenge)
            }
            else -> error(response.message ?: "Connexion refusée.")
        }
    }

    fun verifyTotp(code: String) = launch {
        val manifest = requireNotNull(_state.value.manifest)
        val challenge = requireNotNull(_state.value.challengeId)
        val response = repository.verifyTotp(manifest, challenge, code.filter(Char::isDigit))
        finishAuthentication(manifest, response.deviceToken)
    }

    fun pair(code: String) = launch {
        val manifest = requireNotNull(_state.value.manifest)
        val response = repository.pair(manifest, code, deviceName())
        finishAuthentication(manifest, response.deviceToken)
    }

    /**
     * QR/deep-link v1 : lepotager-manager://pair?site=https%3A%2F%2Fclient.fr&code=12345678
     * Le QR ne contient ni mot de passe ni jeton permanent : seulement un code court à usage unique.
     */
    fun pairFromLink(raw: String) = launch {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme != "lepotager-manager" || uri.host != "pair") {
            error("Ce QR code n'est pas une invitation Le Potager valide.")
        }
        val siteUrl = uri.getQueryParameter("site")?.trim().orEmpty()
        val code = uri.getQueryParameter("code")?.filter(Char::isDigit).orEmpty()
        if (siteUrl.isBlank() || code.length !in 6..12) {
            error("Le QR d'association est incomplet ou expiré.")
        }
        val manifest = repository.discover(siteUrl)
        if ("pairing_code" !in manifest.authMethods) {
            error("Ce site n'autorise pas l'association rapide.")
        }
        val response = repository.pair(manifest, code, deviceName())
        finishAuthentication(manifest, response.deviceToken)
    }

    fun refresh(silent: Boolean = false) {
        val current = _state.value.site ?: return
        launch(showLoading = !silent) {
            val refreshed = repository.refresh(current)
            _state.value = _state.value.copy(
                site = refreshed,
                manifest = refreshed.manifest,
                queuedCount = repository.queuedCount(),
                message = if (silent) _state.value.message else "Synchronisation terminée.",
            )
        }
    }

    fun selectModule(module: ModuleConfig?) {
        _state.value = _state.value.copy(selectedModuleId = module?.id)
    }

    fun submit(moduleId: String, action: String, payload: JsonObject) = launch {
        val current = requireNotNull(_state.value.site)
        when (val result = repository.submitOrQueue(current, moduleId, action, payload)) {
            is SiteRepository.SubmitResult.Sent -> {
                _state.value = _state.value.copy(message = result.response.message ?: statusLabel(result.response.status))
                refresh(silent = true)
            }
            is SiteRepository.SubmitResult.Queued -> {
                _state.value = _state.value.copy(
                    message = "Pas de réseau : la modification explicitement envoyée a été mise en file.",
                    queuedCount = repository.queuedCount(),
                )
            }
        }
    }

    fun disconnect() = launch {
        val id = _state.value.site?.manifest?.siteId ?: _state.value.manifest?.siteId
        if (id != null) repository.disconnect(id)
        _state.value = AppUiState(stage = AppStage.DISCOVERY, message = "Site déconnecté de cet appareil.")
    }

    fun backToDiscovery() {
        _state.value = AppUiState(stage = AppStage.DISCOVERY)
    }

    fun clearNotice() {
        _state.value = _state.value.copy(message = "", error = "")
    }

    private suspend fun finishAuthentication(manifest: DiscoveryManifest, token: String) {
        val restored = repository.activate(manifest, token)
        _state.value = AppUiState(
            stage = AppStage.READY,
            manifest = manifest,
            site = restored,
            queuedCount = repository.queuedCount(),
            message = "${restored.config.site.displayName} est maintenant associé à cet appareil.",
        )
    }

    private fun launch(showLoading: Boolean = true, block: suspend () -> Unit) {
        if (_state.value.loading && showLoading) return
        if (showLoading) _state.value = _state.value.copy(loading = true, error = "")
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Une erreur est survenue.")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Téléphone Android" }

    private fun statusLabel(status: String): String = when (status) {
        "applied" -> "Modification publiée."
        "pending_review" -> "Modification envoyée pour validation."
        else -> "Modification envoyée."
    }
}
