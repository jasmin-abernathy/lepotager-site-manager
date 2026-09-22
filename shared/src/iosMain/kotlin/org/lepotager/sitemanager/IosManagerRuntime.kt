package org.lepotager.sitemanager

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
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

private object IosPairingLinkInbox {
    val pending = MutableStateFlow<String?>(null)

    fun offer(raw: String) {
        pending.value = raw.trim().take(2048).takeIf { it.isNotBlank() }
    }

    fun consume(raw: String) {
        if (pending.value == raw) pending.value = null
    }
}

/** Called by the SwiftUI host when iOS delivers a custom pairing URL. */
fun handleIncomingPairingLink(raw: String) {
    IosPairingLinkInbox.offer(raw)
}
class IosManagerController(
    private val scope: CoroutineScope = MainScope(),
) : ManagerUiActions {
    private val api = IosSiteApiClient()
    private val connectivity = IosConnectivityMonitor()
    private val tokens = IosTokenStore()
    private val ids = IosIdGenerator
    private val mediaUploader = org.lepotager.sitemanager.media.IosMediaUploader(api, tokens, ids)
    private val repository: SiteRepository
    private val holder: ManagerStateHolder

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
            runCatching { repository.flushQueue() }
            holder.initialize()
            IosPairingLinkInbox.pending.filterNotNull().collect { raw ->
                IosPairingLinkInbox.consume(raw)
                holder.pairFromLink(raw)
            }
        }
        scope.launch {
            connectivity.online.filter { it }.collect {
                if (repository.queuedCount() > 0) {
                    val flushed = runCatching { repository.flushQueue() }.getOrDefault(false)
                    if (flushed) runCatching { holder.refresh(silent = true) }
                }
            }
        }
        connectivity.start()
    }

    val state: StateFlow<AppUiState> get() = holder.state

    override fun discover(address: String) = launch { holder.discover(address) }
    override fun login(username: String, password: String) = launch { holder.login(username, password) }
    override fun verifyTotp(code: String) = launch { holder.verifyTotp(code) }
    override fun pair(code: String) = launch { holder.pair(code) }
    override fun pairFromLink(raw: String) = launch { holder.pairFromLink(raw) }
    override fun refresh(silent: Boolean) = launch {
        runCatching { repository.flushQueue() }
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
        connectivity.stop()
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
