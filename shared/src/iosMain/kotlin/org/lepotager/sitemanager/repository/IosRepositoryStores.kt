@file:OptIn(\n    com.russhwolf.settings.ExperimentalSettingsApi::class,\n    com.russhwolf.settings.ExperimentalSettingsImplementation::class,\n    kotlinx.cinterop.BetaInteropApi::class,\n    kotlinx.cinterop.ExperimentalForeignApi::class,\n)

package org.lepotager.sitemanager.repository

import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import org.lepotager.sitemanager.network.SiteJson
import platform.Foundation.NSUserDefaults

private const val ACTIVE_SITE_KEY = "manager.active_site"
private const val QUEUE_KEY = "manager.pending_queue"
private const val SITE_KEY_PREFIX = "manager.site."
private const val TOKEN_SERVICE = "org.lepotager.sitemanager.device-tokens"

class IosActiveSiteStore(
    private val settings: Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
) : ActiveSiteStore {
    override suspend fun activeSiteId(): String? = settings.getStringOrNull(ACTIVE_SITE_KEY)

    override suspend fun setActiveSite(siteId: String?) {
        if (siteId == null) settings.remove(ACTIVE_SITE_KEY)
        else settings.putString(ACTIVE_SITE_KEY, siteId)
    }
}

class IosSiteCache(
    private val settings: Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
) : SiteCache {
    override suspend fun upsert(site: CachedSiteRecord) {
        settings.putString(siteKey(site.siteId), SiteJson.codec.encodeToString(site))
    }

    override suspend fun get(siteId: String): CachedSiteRecord? {
        val key = siteKey(siteId)
        val raw = settings.getStringOrNull(key) ?: return null
        return runCatching { SiteJson.codec.decodeFromString<CachedSiteRecord>(raw) }
            .getOrElse {
                settings.remove(key)
                null
            }
    }

    override suspend fun delete(siteId: String) {
        settings.remove(siteKey(siteId))
    }

    private fun siteKey(siteId: String): String = SITE_KEY_PREFIX + siteId
}

class IosPendingChangeStore(
    private val settings: Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
) : PendingChangeStore {
    override suspend fun upsert(change: PendingChangeRecord) {
        val items = readAll().toMutableList()
        val index = items.indexOfFirst { it.clientRequestId == change.clientRequestId }
        if (index >= 0) items[index] = change else items += change
        writeAll(items)
    }

    override suspend fun all(): List<PendingChangeRecord> = readAll()

    override suspend fun delete(id: String) {
        writeAll(readAll().filterNot { it.clientRequestId == id })
    }

    override suspend fun fail(id: String, error: String) {
        val updated = readAll().map { item ->
            if (item.clientRequestId == id) {
                item.copy(attempts = item.attempts + 1, lastError = error)
            } else item
        }
        writeAll(updated)
    }

    override suspend fun count(): Int = readAll().size

    private fun readAll(): List<PendingChangeRecord> {
        val raw = settings.getStringOrNull(QUEUE_KEY) ?: return emptyList()
        return runCatching { SiteJson.codec.decodeFromString<List<PendingChangeRecord>>(raw) }
            .getOrElse {
                settings.remove(QUEUE_KEY)
                emptyList()
            }
    }

    private fun writeAll(items: List<PendingChangeRecord>) {
        if (items.isEmpty()) settings.remove(QUEUE_KEY)
        else settings.putString(QUEUE_KEY, SiteJson.codec.encodeToString(items))
    }
}

class IosTokenStore(
    private val settings: Settings = IOS_TOKEN_SETTINGS,
) : TokenStore {
    override suspend fun save(siteId: String, token: String) {
        settings.putString(tokenKey(siteId), token)
    }

    override suspend fun load(siteId: String): String? = settings.getStringOrNull(tokenKey(siteId))

    override suspend fun remove(siteId: String) {
        settings.remove(tokenKey(siteId))
    }

    private fun tokenKey(siteId: String): String = "token.$siteId"
}
