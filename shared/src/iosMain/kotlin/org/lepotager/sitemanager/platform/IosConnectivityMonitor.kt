@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.lepotager.sitemanager.platform

import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

class IosConnectivityMonitor(
    private val onAvailable: () -> Unit,
) {
    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create("org.lepotager.sitemanager.network", null)
    private var wasAvailable = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        nw_path_monitor_set_update_handler(monitor) { path ->
            val available = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            if (available && !wasAvailable) onAvailable()
            wasAvailable = available
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    fun close() {
        if (started) nw_path_monitor_cancel(monitor)
        started = false
    }
}
