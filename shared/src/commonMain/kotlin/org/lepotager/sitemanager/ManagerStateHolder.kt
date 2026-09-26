package org.lepotager.sitemanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ChangeResponse
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

data class PairingInvitation(
    val siteUrl: String,
    val code: String,
)

fun interface DeviceNameProvider {
    fun deviceName(): String
}

fun interface PairingLinkParser {
    fun parse(raw: String): PairingInvitation
}

class ManagerStateHolder(
    private val repository: SiteRepository,
    private val deviceNameProvider: DeviceNameProvider,
    private val pairingLinkParser: PairingLinkParser,
) {
    private val _state = MutableStateFlow(AppUiState(loading = true))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    suspend fun initialize() {
        try {
            val restored = repository.restoreActive()
            _state.value = if (restored == null) {
                AppUiState(stage = AppStage.DISCOVERY, loading = false)
            } else {
                AppUiState(
                    stage = AppStage.READY,
                    loading = false,
                    manifest = restored.manifest,
                    site = restored,
                    queuedCount = repository.queuedCount(),
                )
            }
            if (restored != null) refreshInternal(silent = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                loading = false,
                error = e.message ?: "Une erreur est survenue.",
            )
        }
    }

    suspend fun discover(address: String) = execute {
        val manifest = repository.discover(address)
        _state.value = _state.value.copy(
            stage = AppStage.AUTH,
            manifest = manifest,
            site = null,
            challengeId = null,
            error = "",
        )
    }

    suspend fun login(username: String, password: String) = execute {
        val manifest = requireNotNull(_state.value.manifest)
        val response = repository.startAuth(
            manifest,
            username.trim(),
            password,
            deviceNameProvider.deviceName(),
        )
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

    suspend fun verifyTotp(code: String) = execute {
        val manifest = requireNotNull(_state.value.manifest)
        val challenge = requireNotNull(_state.value.challengeId)
        val response = repository.verifyTotp(manifest, challenge, code.filter(Char::isDigit))
        finishAuthentication(manifest, response.deviceToken)
    }

    suspend fun pair(code: String) = execute {
        val manifest = requireNotNull(_state.value.manifest)
        val response = repository.pair(manifest, code, deviceNameProvider.deviceName())
        finishAuthentication(manifest, response.deviceToken)
    }

    suspend fun pairFromLink(raw: String) = execute {
        val invitation = pairingLinkParser.parse(raw)
        if (invitation.siteUrl.isBlank() || invitation.code.length !in 6..12 ||
            invitation.code.any { !it.isDigit() }
        ) {
            error("Le QR d’association est incomplet ou expiré.")
        }
        val manifest = repository.discover(invitation.siteUrl)
        if ("pairing_code" !in manifest.authMethods) {
            error("Ce site n’autorise pas l’association rapide.")
        }
        val response = repository.pair(
            manifest,
            invitation.code,
            deviceNameProvider.deviceName(),
        )
        finishAuthentication(manifest, response.deviceToken)
    }

    suspend fun refresh(silent: Boolean = false) = execute(showLoading = !silent) {
        refreshInternal(silent)
    }

    suspend fun resumePendingChanges() = execute(showLoading = false) {
        repository.flushQueue()
        _state.value = _state.value.copy(queuedCount = repository.queuedCount())
    }

    fun selectModule(module: ModuleConfig?) {
        _state.value = _state.value.copy(selectedModuleId = module?.id)
    }

    suspend fun submit(
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean = true,
    ) = execute {
        val current = requireNotNull(_state.value.site)
        when (val result = repository.submitOrQueue(current, moduleId, action, payload, allowOffline)) {
            is SiteRepository.SubmitResult.Sent -> {
                _state.value = _state.value.copy(
                    message = result.response.message ?: statusLabel(result.response.status),
                )
                refreshInternal(silent = true)
            }
            is SiteRepository.SubmitResult.Queued -> {
                _state.value = _state.value.copy(
                    message = "Pas de réseau : la modification explicitement envoyée a été mise en file.",
                    queuedCount = repository.queuedCount(),
                )
            }
        }
    }

    suspend fun runPlatformMutation(
        mutation: suspend (SiteRepository.RestoredSite) -> ChangeResponse,
    ) = execute {
        val current = requireNotNull(_state.value.site)
        val response = mutation(current)
        _state.value = _state.value.copy(
            message = response.message ?: statusLabel(response.status),
        )
        refreshInternal(silent = true)
    }

    suspend fun disconnect() = execute {
        val id = _state.value.site?.manifest?.siteId ?: _state.value.manifest?.siteId
        if (id != null) repository.disconnect(id)
        _state.value = AppUiState(
            stage = AppStage.DISCOVERY,
            loading = true,
            message = "Site déconnecté de cet appareil.",
        )
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
            loading = true,
            manifest = manifest,
            site = restored,
            queuedCount = repository.queuedCount(),
            message = "${restored.config.site.displayName} est maintenant associé à cet appareil.",
        )
    }

    private suspend fun refreshInternal(silent: Boolean) {
        val current = _state.value.site ?: return
        val refreshed = repository.refresh(current)
        _state.value = _state.value.copy(
            site = refreshed,
            manifest = refreshed.manifest,
            queuedCount = repository.queuedCount(),
            message = if (silent) _state.value.message else "Synchronisation terminée.",
        )
    }

    private suspend fun execute(
        showLoading: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (_state.value.loading && showLoading) return
        if (showLoading) _state.value = _state.value.copy(loading = true, error = "")
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Une erreur est survenue.")
        } finally {
            if (showLoading) _state.value = _state.value.copy(loading = false)
        }
    }

    private fun statusLabel(status: String): String = when (status) {
        "applied" -> "Modification publiée."
        "pending_review" -> "Modification envoyée pour validation."
        else -> "Modification envoyée."
    }
}
