package org.lepotager.sitemanager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.platform.AndroidDeviceNameProvider
import org.lepotager.sitemanager.platform.AndroidPairingLinkParser

class MainViewModel(application: Application) : AndroidViewModel(application), ManagerUiActions {
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

    override fun discover(address: String) = launch { holder.discover(address) }

    override fun login(username: String, password: String) = launch { holder.login(username, password) }

    override fun verifyTotp(code: String) = launch { holder.verifyTotp(code) }

    override fun pair(code: String) = launch { holder.pair(code) }

    override fun pairFromLink(raw: String) = launch { holder.pairFromLink(raw) }

    override fun refresh(silent: Boolean) = launch { holder.refresh(silent) }

    override fun selectModule(module: ModuleConfig?) {
        holder.selectModule(module)
    }

    override fun submit(
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean,
    ) = launch {
        holder.submit(moduleId, action, payload, allowOffline)
    }

    override fun uploadMedia(
        moduleId: String,
        itemId: String,
        platformRef: String,
        metadata: JsonObject,
    ) = launch {
        holder.runPlatformMutation { site ->
            mediaUploader.uploadMedia(
                site = site,
                moduleId = moduleId,
                itemId = itemId,
                uri = Uri.parse(platformRef),
                metadata = metadata,
            )
        }
    }

    override fun disconnect() = launch { holder.disconnect() }

    override fun backToDiscovery() = holder.backToDiscovery()

    override fun clearNotice() = holder.clearNotice()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
