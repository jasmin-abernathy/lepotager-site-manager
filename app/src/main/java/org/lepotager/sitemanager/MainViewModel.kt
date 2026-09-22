package org.lepotager.sitemanager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.platform.AndroidDeviceNameProvider
import org.lepotager.sitemanager.platform.AndroidPairingLinkParser

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SiteManagerApplication
    private val mediaUploader = app.mediaUploader
    private val holder = ManagerStateHolder(
        repository = app.repository,
        deviceNameProvider = AndroidDeviceNameProvider,
        pairingLinkParser = AndroidPairingLinkParser,
    )

    val state: StateFlow<AppUiState> = holder.state

    init {
        viewModelScope.launch { holder.initialize() }
    }

    fun discover(address: String) = launch { holder.discover(address) }

    fun login(username: String, password: String) = launch { holder.login(username, password) }

    fun verifyTotp(code: String) = launch { holder.verifyTotp(code) }

    fun pair(code: String) = launch { holder.pair(code) }

    fun pairFromLink(raw: String) = launch { holder.pairFromLink(raw) }

    fun refresh(silent: Boolean = false) = launch { holder.refresh(silent) }

    fun selectModule(module: org.lepotager.sitemanager.model.ModuleConfig?) {
        holder.selectModule(module)
    }

    fun submit(
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean = true,
    ) = launch {
        holder.submit(moduleId, action, payload, allowOffline)
    }

    fun uploadMedia(moduleId: String, itemId: String, uri: Uri, metadata: JsonObject) = launch {
        holder.runPlatformMutation { site ->
            mediaUploader.uploadMedia(
                site = site,
                moduleId = moduleId,
                itemId = itemId,
                uri = uri,
                metadata = metadata,
            )
        }
    }

    fun disconnect() = launch { holder.disconnect() }

    fun backToDiscovery() = holder.backToDiscovery()

    fun clearNotice() = holder.clearNotice()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
