package org.lepotager.sitemanager.repository

data class CachedSiteRecord(
    val siteId: String,
    val origin: String,
    val displayName: String,
    val apiBaseUrl: String,
    val manifestJson: String,
    val configJson: String,
    val configVersion: Long,
    val snapshotJson: String,
    val snapshotRevision: Long,
    val updatedAt: Long,
)

data class PendingChangeRecord(
    val clientRequestId: String,
    val siteId: String,
    val moduleId: String,
    val action: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String = "",
)

interface SiteCache {
    suspend fun upsert(site: CachedSiteRecord)
    suspend fun get(siteId: String): CachedSiteRecord?
    suspend fun delete(siteId: String)
}

interface PendingChangeStore {
    suspend fun upsert(change: PendingChangeRecord)
    suspend fun all(): List<PendingChangeRecord>
    suspend fun delete(id: String)
    suspend fun fail(id: String, error: String)
    suspend fun count(): Int
}

interface ActiveSiteStore {
    suspend fun activeSiteId(): String?
    suspend fun setActiveSite(siteId: String?)
}

interface TokenStore {
    suspend fun save(siteId: String, token: String)
    suspend fun load(siteId: String): String?
    suspend fun remove(siteId: String)
}

fun interface IdGenerator {
    fun newId(): String
}

fun interface TimeProvider {
    fun nowMillis(): Long
}

fun interface QueueScheduler {
    fun schedule()
}

fun interface NetworkFailureClassifier {
    fun isNetworkFailure(error: Throwable): Boolean
}
