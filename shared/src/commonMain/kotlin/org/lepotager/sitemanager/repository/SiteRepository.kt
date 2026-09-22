package org.lepotager.sitemanager.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.AuthStartResponse
import org.lepotager.sitemanager.model.AuthTokenResponse
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SnapshotResponse
import org.lepotager.sitemanager.network.SiteApi
import org.lepotager.sitemanager.network.SiteJson
import org.lepotager.sitemanager.network.SiteProtocolValidator

class SiteRepository(
    private val api: SiteApi,
    private val sites: SiteCache,
    private val queue: PendingChangeStore,
    private val preferences: ActiveSiteStore,
    private val tokens: TokenStore,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val queueScheduler: QueueScheduler,
    private val networkFailures: NetworkFailureClassifier,
) {
    data class RestoredSite(
        val manifest: DiscoveryManifest,
        val config: SiteConfig,
        val snapshot: SnapshotResponse,
    )

    sealed interface SubmitResult {
        data class Sent(val response: ChangeResponse) : SubmitResult
        data class Queued(val clientRequestId: String) : SubmitResult
    }

    suspend fun discover(address: String): DiscoveryManifest = api.discover(address)

    suspend fun startAuth(
        manifest: DiscoveryManifest,
        username: String,
        password: String,
        deviceName: String,
    ): AuthStartResponse = api.startAuth(manifest, username, password, deviceName)

    suspend fun verifyTotp(
        manifest: DiscoveryManifest,
        challengeId: String,
        code: String,
    ): AuthTokenResponse = api.verifyTotp(manifest, challengeId, code)

    suspend fun pair(
        manifest: DiscoveryManifest,
        code: String,
        deviceName: String,
    ): AuthTokenResponse = api.pair(manifest, code, deviceName)

    suspend fun activate(manifest: DiscoveryManifest, token: String): RestoredSite {
        tokens.save(manifest.siteId, token)
        val config = api.fetchConfig(manifest, token)
        SiteProtocolValidator.validateConfig(config)
        val snapshot = api.fetchSnapshot(manifest, token)
        saveCache(manifest, config, snapshot)
        preferences.setActiveSite(manifest.siteId)
        return RestoredSite(manifest, config, snapshot)
    }

    suspend fun restoreActive(): RestoredSite? {
        val id = preferences.activeSiteId() ?: return null
        val entity = sites.get(id) ?: return null
        return runCatching {
            RestoredSite(
                manifest = SiteJson.codec.decodeFromString(entity.manifestJson),
                config = SiteJson.codec.decodeFromString(entity.configJson),
                snapshot = SiteJson.codec.decodeFromString(entity.snapshotJson),
            )
        }.getOrNull()
    }

    suspend fun refresh(site: RestoredSite): RestoredSite {
        val token = tokens.load(site.manifest.siteId) ?: throw SiteSessionException("Session de l’appareil absente.")
        val config = api.fetchConfig(site.manifest, token)
        val snapshot = api.fetchSnapshot(site.manifest, token)
        saveCache(site.manifest, config, snapshot)
        return RestoredSite(site.manifest, config, snapshot)
    }

    suspend fun submitOrQueue(
        site: RestoredSite,
        moduleId: String,
        action: String,
        payload: JsonObject,
        allowOffline: Boolean = true,
    ): SubmitResult {
        val token = tokens.load(site.manifest.siteId) ?: throw SiteSessionException("Session de l’appareil absente.")
        val clientRequestId = ids.newId()
        val change = ChangeRequest(moduleId, action, clientRequestId, payload)
        return try {
            SubmitResult.Sent(api.submitChange(site.manifest, token, change))
        } catch (e: Exception) {
            if (!networkFailures.isNetworkFailure(e) || !site.config.policy.allowOfflineQueue || !allowOffline) throw e
            queue.upsert(
                PendingChangeRecord(
                    clientRequestId = clientRequestId,
                    siteId = site.manifest.siteId,
                    moduleId = moduleId,
                    action = action,
                    payloadJson = SiteJson.codec.encodeToString(payload),
                    createdAt = time.nowMillis(),
                    lastError = e.message.orEmpty(),
                ),
            )
            queueScheduler.schedule()
            SubmitResult.Queued(clientRequestId)
        }
    }

    suspend fun flushQueue(): Boolean {
        var allOk = true
        queue.all().forEach { queued ->
            val siteEntity = sites.get(queued.siteId)
            val token = tokens.load(queued.siteId)
            if (siteEntity == null || token == null) {
                queue.fail(queued.clientRequestId, "Site ou session introuvable")
                allOk = false
                return@forEach
            }
            val manifest = runCatching {
                SiteJson.codec.decodeFromString<DiscoveryManifest>(siteEntity.manifestJson)
            }.getOrNull()
            val payload = runCatching {
                SiteJson.codec.decodeFromString<JsonObject>(queued.payloadJson)
            }.getOrNull()
            if (manifest == null || payload == null) {
                queue.fail(queued.clientRequestId, "Données locales invalides")
                allOk = false
                return@forEach
            }
            try {
                api.submitChange(
                    manifest,
                    token,
                    ChangeRequest(queued.moduleId, queued.action, queued.clientRequestId, payload),
                )
                queue.delete(queued.clientRequestId)
            } catch (e: Exception) {
                queue.fail(queued.clientRequestId, e.message.orEmpty().take(300))
                allOk = false
            }
        }
        return allOk
    }

    suspend fun queuedCount(): Int = queue.count()

    suspend fun disconnect(siteId: String) {
        tokens.remove(siteId)
        sites.delete(siteId)
        if (preferences.activeSiteId() == siteId) preferences.setActiveSite(null)
    }

    private suspend fun saveCache(
        manifest: DiscoveryManifest,
        config: SiteConfig,
        snapshot: SnapshotResponse,
    ) {
        sites.upsert(
            CachedSiteRecord(
                siteId = manifest.siteId,
                origin = api.canonicalOrigin(manifest.apiBaseUrl),
                displayName = manifest.displayName,
                apiBaseUrl = manifest.apiBaseUrl,
                manifestJson = SiteJson.codec.encodeToString(manifest),
                configJson = SiteJson.codec.encodeToString(config),
                configVersion = config.configVersion,
                snapshotJson = SiteJson.codec.encodeToString(snapshot),
                snapshotRevision = snapshot.revision,
                updatedAt = time.nowMillis(),
            ),
        )
    }
}
