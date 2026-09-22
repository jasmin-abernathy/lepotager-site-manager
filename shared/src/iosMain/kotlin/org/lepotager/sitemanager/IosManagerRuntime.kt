package org.lepotager.sitemanager

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.network.IosSiteApiClient
import org.lepotager.sitemanager.platform.IosDeviceNameProvider
import org.lepotager.sitemanager.platform.IosIdGenerator
import org.lepotager.sitemanager.platform.IosNetworkFailureClassifier
import org.lepotager.sitemanager.platform.IosPairingLinkParser
import org.lepotager.sitemanager.platform.IosQueueScheduler
import org.lepotager.sitemanager.platform.IosTimeProvider
import org.lepotager.sitemanager.repository.IosActiveSiteStore
import org.lepotager.sitemanager.repository.IosPendingChangeStore
import org.lepotager.sitemanager.repository.IosSiteCache
import org.lepotager.sitemanager.repository.IosTokenStore
import org.lepotager.sitemanager.repository.SiteRepository
import org.lepotager.sitemanager.ui.ManagerApp
import platform.UIKit.UIViewController

class IosManagerController(
    private val scope: CoroutineScope = MainScope(),
) : ManagerUiActions {
    private val api = IosSiteApiClient()
    private val repository: SiteRepository
    private val holder: ManagerStateHolder

    init {
        repository = SiteRepository(
            api = api,
            sites = IosSiteCache(),
            queue = IosPendingChangeStore(),
            preferences = IosActiveSiteStore(),
            tokens = IosTokenStore(),
            ids = IosIdGenerator,
            time = IosTimeProvider,
            queueScheduler = IosQueueScheduler,
            networkFailures = IosNetworkFailureClassifier,
        )
        holder = ManagerStateHolder(
            repository = repository,
            deviceNameProvider = IosDeviceNameProvider,
            pairingLinkParser = IosPairingLinkParser,
        )
        scope.launch {
            runCatching { repository.flushQueue() }
            holder.initialize()
        }
    }

    val state: StateFlow<AppUiState> get() = holder.state

    override fun discover(address: String) = launch { holder.discover(address) }
    override fun login(username: String, password: String) = launch { holder.login(username, password) }
    override fun verifyTotp(code: String) = launch { holder.verifyTotp(code) }
    override fun pair(code: String) = launch { holder.pair(code) }
    override fun pairFromLink(raw: String) = launch { holder.pairFromLink(raw) }
    override fun refresh(silent: Boolean) = launch { holder.refresh(silent) }
    override fun selectModule(module: ModuleConfig?) = holder.selectModule(module)

    override fun submit(
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean,
    ) = launch { holder.submit(moduleId, action, payload, allowOffline) }

    override fun uploadMedia(
        moduleId: String,
        itemId: String,
        platformRef: String,
        metadata: JsonObject,
    ) {
        // Intentionally unreachable until the native Photos picker is connected.
    }

    override fun disconnect() = launch { holder.disconnect() }
    override fun backToDiscovery() = holder.backToDiscovery()
    override fun clearNotice() = holder.clearNotice()

    fun close() {
        scope.cancel()
        api.close()
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    val controller = remember { IosManagerController() }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    ManagerApp(state = state, actions = controller)
}
