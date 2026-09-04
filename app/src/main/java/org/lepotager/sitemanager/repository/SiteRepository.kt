package org.lepotager.sitemanager.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.data.LocalDatabase
import org.lepotager.sitemanager.data.PreferencesStore
import org.lepotager.sitemanager.data.QueuedChangeEntity
import org.lepotager.sitemanager.data.SiteEntity
import org.lepotager.sitemanager.model.AuthStartResponse
import org.lepotager.sitemanager.model.AuthTokenResponse
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SnapshotResponse
import org.lepotager.sitemanager.network.SiteApiClient
import org.lepotager.sitemanager.security.TokenVault
import org.lepotager.sitemanager.worker.PendingChangesWorker
import java.io.IOException
import java.util.UUID

class SiteRepository(
    private val context: Context,
    private val api: SiteApiClient,
    private val db: LocalDatabase,
    private val preferences: PreferencesStore,
    private val tokens: TokenVault,
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

    suspend fun startAuth(manifest: DiscoveryManifest, username: String, password: String, deviceName: String): AuthStartResponse =
        api.startAuth(manifest, username, password, deviceName)

    suspend fun verifyTotp(manifest: DiscoveryManifest, challengeId: String, code: String): AuthTokenResponse =
        api.verifyTotp(manifest, challengeId, code)

    suspend fun pair(manifest: DiscoveryManifest, code: String, deviceName: String): AuthTokenResponse =
        api.pair(manifest, code, deviceName)

    suspend fun activate(manifest: DiscoveryManifest, token: String): RestoredSite {
        tokens.save(manifest.siteId, token)
        val config = api.fetchConfig(manifest, token)
        require(config.schemaVersion == 1) { "Configuration du site non prise en charge." }
        val snapshot = api.fetchSnapshot(manifest, token)
        saveCache(manifest, config, snapshot)
        preferences.setActiveSite(manifest.siteId)
        return RestoredSite(manifest, config, snapshot)
    }

    suspend fun restoreActive(): RestoredSite? {
        val id = preferences.activeSiteId() ?: return null
        val entity = db.sites().get(id) ?: return null
        return runCatching {
            RestoredSite(
                manifest = api.json.decodeFromString(entity.manifestJson),
                config = api.json.decodeFromString(entity.configJson),
                snapshot = api.json.decodeFromString(entity.snapshotJson),
            )
        }.getOrNull()
    }

    suspend fun refresh(site: RestoredSite): RestoredSite {
        val token = tokens.load(site.manifest.siteId) ?: throw SecurityException("Session de l'appareil absente.")
        val config = api.fetchConfig(site.manifest, token)
        val snapshot = api.fetchSnapshot(site.manifest, token)
        saveCache(site.manifest, config, snapshot)
        return RestoredSite(site.manifest, config, snapshot)
    }

    suspend fun submitOrQueue(site: RestoredSite, moduleId: String, action: String, payload: JsonObject): SubmitResult {
        val token = tokens.load(site.manifest.siteId) ?: throw SecurityException("Session de l'appareil absente.")
        val clientRequestId = UUID.randomUUID().toString()
        val change = ChangeRequest(moduleId, action, clientRequestId, payload)
        return try {
            SubmitResult.Sent(api.submitChange(site.manifest, token, change))
        } catch (e: IOException) {
            if (!site.config.policy.allowOfflineQueue) throw e
            db.queue().upsert(
                QueuedChangeEntity(
                    clientRequestId = clientRequestId,
                    siteId = site.manifest.siteId,
                    moduleId = moduleId,
                    action = action,
                    payloadJson = api.json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                    lastError = e.message.orEmpty(),
                ),
            )
            scheduleQueue()
            SubmitResult.Queued(clientRequestId)
        }
    }

    suspend fun flushQueue(): Boolean {
        var allOk = true
        db.queue().all().forEach { queued ->
            val siteEntity = db.sites().get(queued.siteId)
            val token = tokens.load(queued.siteId)
            if (siteEntity == null || token == null) {
                db.queue().fail(queued.clientRequestId, "Site ou session introuvable")
                allOk = false
                return@forEach
            }
            val manifest = runCatching { api.json.decodeFromString<DiscoveryManifest>(siteEntity.manifestJson) }.getOrNull()
            val payload = runCatching { api.json.decodeFromString<JsonObject>(queued.payloadJson) }.getOrNull()
            if (manifest == null || payload == null) {
                db.queue().fail(queued.clientRequestId, "Données locales invalides")
                allOk = false
                return@forEach
            }
            try {
                api.submitChange(manifest, token, ChangeRequest(queued.moduleId, queued.action, queued.clientRequestId, payload))
                db.queue().delete(queued.clientRequestId)
            } catch (e: Exception) {
                db.queue().fail(queued.clientRequestId, e.message.orEmpty().take(300))
                allOk = false
            }
        }
        return allOk
    }

    suspend fun queuedCount(): Int = db.queue().count()

    suspend fun disconnect(siteId: String) {
        tokens.remove(siteId)
        db.sites().delete(siteId)
        if (preferences.activeSiteId() == siteId) preferences.setActiveSite(null)
    }

    private suspend fun saveCache(manifest: DiscoveryManifest, config: SiteConfig, snapshot: SnapshotResponse) {
        val origin = api.normalizeOrigin(manifest.apiBaseUrl).toString()
        db.sites().upsert(
            SiteEntity(
                siteId = manifest.siteId,
                origin = origin,
                displayName = manifest.displayName,
                apiBaseUrl = manifest.apiBaseUrl,
                manifestJson = api.json.encodeToString(manifest),
                configJson = api.json.encodeToString(config),
                configVersion = config.configVersion,
                snapshotJson = api.json.encodeToString(snapshot),
                snapshotRevision = snapshot.revision,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun scheduleQueue() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<PendingChangesWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "lepotager-pending-changes",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
