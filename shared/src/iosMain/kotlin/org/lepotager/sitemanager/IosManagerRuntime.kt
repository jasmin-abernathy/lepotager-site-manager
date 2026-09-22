package org.lepotager.sitemanager

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ModuleConfig
import org.lepotager.sitemanager.network.IosSiteApiClient
import org.lepotager.sitemanager.platform.IosConnectivityMonitor
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

private sealed interface IosRuntimeEvent {
    data class PairingUrl(val raw: String) : IosRuntimeEvent
    data class SceneActive(val active: Boolean) : IosRuntimeEvent
    data object NetworkAvailable : IosRuntimeEvent
}

private val iosRuntimeEvents = Channel<IosRuntimeEvent>(capacity = Channel.BUFFERED)

fun handleIncomingPairingUrl(raw: String) {
    raw.trim().takeIf { it.isNotBlank() }?.let {
        iosRuntimeEvents.trySend(IosRuntimeEvent.PairingUrl(it))
    }
}

fun notifyIosSceneActive(active: Boolean) {
    iosRuntimeEvents.trySend(IosRuntimeEvent.SceneActive(active))
}

class IosManagerController(
    private val scope: CoroutineScope = MainScope(),
) : ManagerUiActions {
    private val api = IosSiteApiClient()
    private val tokens = IosTokenStore()
    private val ids = IosIdGenerator
    private val mediaUploader = org.lepotager.sitemanager.media.IosMediaUploader(api, tokens, ids)
    private val repository: SiteRepository
    private val holder: ManagerStateHolder
    private val connectivityMonitor = IosConnectivityMonitor {
        iosRuntimeEvents.trySend(IosRuntimeEvent.NetworkAvailable)
    }

    init {
        repository = SiteRepository(
            api = api,
            sites = IosSiteCache(),
            queue = IosPendingChangeStore(),
            preferences = IosActiveSiteStore(),
            tokens = tokens,
            ids = ids,
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
            holder.initialize()
            connectivityMonitor.start()
            var sceneActive = false
            for (event in iosRuntimeEvents) {
                when (event) {
                    is IosRuntimeEvent.PairingUrl -> holder.pairFromLink(event.raw)
                    is IosRuntimeEvent.SceneActive -> {
                        sceneActive = event.active
                        if (sceneActive) holder.resumePendingChanges()
                    }
                    IosRuntimeEvent.NetworkAvailable -> {
                        if (sceneActive) holder.resumePendingChanges()
                    }
                }
            }
        }
    }

    val state: StateFlow<AppUiState> get() = holder.state

    override fun discover(address: String) = launch { holder.discover(address) }
    override fun login(username: String, password: String) = launch { holder.login(username, password) }
    override fun verifyTotp(code: String) = launch { holder.verifyTotp(code) }
    override fun pair(code: String) = launch { holder.pair(code) }
    override fun pairFromLink(raw: String) = launch { holder.pairFromLink(raw) }
    override fun refresh(silent: Boolean) = launch {
        holder.resumePendingChanges()
        holder.refresh(silent)
    }
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
    ) = launch {
        holder.runPlatformMutation { site ->
            mediaUploader.uploadMedia(
                site = site,
                moduleId = moduleId,
                itemId = itemId,
                platformRef = platformRef,
                metadata = metadata,
            )
        }
    }

    override fun disconnect() = launch { holder.disconnect() }
    override fun backToDiscovery() = holder.backToDiscovery()
    override fun clearNotice() = holder.clearNotice()

    fun close() {
        connectivityMonitor.close()
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
