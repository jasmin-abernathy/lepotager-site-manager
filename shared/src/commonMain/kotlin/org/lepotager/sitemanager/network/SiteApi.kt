package org.lepotager.sitemanager.network

import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.AuthStartResponse
import org.lepotager.sitemanager.model.AuthTokenResponse
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SnapshotResponse

/**
 * Contrat réseau commun. Chaque plateforme garde son transport, mais expose
 * exactement les mêmes opérations au coeur partagé.
 */
interface SiteApi {
    suspend fun discover(address: String): DiscoveryManifest

    suspend fun startAuth(
        manifest: DiscoveryManifest,
        username: String,
        password: String,
        deviceName: String,
    ): AuthStartResponse

    suspend fun verifyTotp(
        manifest: DiscoveryManifest,
        challengeId: String,
        code: String,
    ): AuthTokenResponse

    suspend fun pair(
        manifest: DiscoveryManifest,
        code: String,
        deviceName: String,
    ): AuthTokenResponse

    suspend fun fetchConfig(manifest: DiscoveryManifest, token: String): SiteConfig

    suspend fun fetchSnapshot(manifest: DiscoveryManifest, token: String): SnapshotResponse

    suspend fun submitChange(
        manifest: DiscoveryManifest,
        token: String,
        change: ChangeRequest,
    ): ChangeResponse

    suspend fun uploadMedia(
        manifest: DiscoveryManifest,
        token: String,
        moduleId: String,
        itemId: String,
        clientRequestId: String,
        metadata: JsonObject,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ChangeResponse

    fun canonicalOrigin(address: String): String
}
