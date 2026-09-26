package org.lepotager.sitemanager

import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ModuleConfig

/** Actions de présentation consommables par une UI Android ou iOS. */
interface ManagerUiActions {
    fun discover(address: String)
    fun login(username: String, password: String)
    fun verifyTotp(code: String)
    fun pair(code: String)
    fun pairFromLink(raw: String)
    fun refresh(silent: Boolean = false)
    fun selectModule(module: ModuleConfig?)
    fun submit(
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean = true,
    )
    /** Référence opaque fournie par le sélecteur de média de la plateforme. */
    fun uploadMedia(
        moduleId: String,
        itemId: String,
        platformRef: String,
        metadata: JsonObject,
    )
    fun disconnect()
    fun backToDiscovery()
    fun clearNotice()
}
