package org.lepotager.sitemanager.data

import org.lepotager.sitemanager.repository.CachedSiteRecord
import org.lepotager.sitemanager.repository.PendingChangeRecord
import org.lepotager.sitemanager.repository.PendingChangeStore
import org.lepotager.sitemanager.repository.SiteCache

class RoomSiteCache(private val database: LocalDatabase) : SiteCache {
    override suspend fun upsert(site: CachedSiteRecord) {
        database.sites().upsert(site.toEntity())
    }

    override suspend fun get(siteId: String): CachedSiteRecord? =
        database.sites().get(siteId)?.toRecord()

    override suspend fun delete(siteId: String) {
        database.sites().delete(siteId)
    }

    private fun CachedSiteRecord.toEntity() = SiteEntity(
        siteId = siteId,
        origin = origin,
        displayName = displayName,
        apiBaseUrl = apiBaseUrl,
        manifestJson = manifestJson,
        configJson = configJson,
        configVersion = configVersion,
        snapshotJson = snapshotJson,
        snapshotRevision = snapshotRevision,
        updatedAt = updatedAt,
    )

    private fun SiteEntity.toRecord() = CachedSiteRecord(
        siteId = siteId,
        origin = origin,
        displayName = displayName,
        apiBaseUrl = apiBaseUrl,
        manifestJson = manifestJson,
        configJson = configJson,
        configVersion = configVersion,
        snapshotJson = snapshotJson,
        snapshotRevision = snapshotRevision,
        updatedAt = updatedAt,
    )
}

class RoomPendingChangeStore(private val database: LocalDatabase) : PendingChangeStore {
    override suspend fun upsert(change: PendingChangeRecord) {
        database.queue().upsert(change.toEntity())
    }

    override suspend fun all(): List<PendingChangeRecord> =
        database.queue().all().map { it.toRecord() }

    override suspend fun delete(id: String) {
        database.queue().delete(id)
    }

    override suspend fun fail(id: String, error: String) {
        database.queue().fail(id, error)
    }

    override suspend fun count(): Int = database.queue().count()

    private fun PendingChangeRecord.toEntity() = QueuedChangeEntity(
        clientRequestId = clientRequestId,
        siteId = siteId,
        moduleId = moduleId,
        action = action,
        payloadJson = payloadJson,
        createdAt = createdAt,
        attempts = attempts,
        lastError = lastError,
    )

    private fun QueuedChangeEntity.toRecord() = PendingChangeRecord(
        clientRequestId = clientRequestId,
        siteId = siteId,
        moduleId = moduleId,
        action = action,
        payloadJson = payloadJson,
        createdAt = createdAt,
        attempts = attempts,
        lastError = lastError,
    )
}
