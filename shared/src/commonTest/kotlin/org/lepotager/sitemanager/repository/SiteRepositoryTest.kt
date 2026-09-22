package org.lepotager.sitemanager.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.lepotager.sitemanager.model.AuthStartResponse
import org.lepotager.sitemanager.model.AuthTokenResponse
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SiteIdentity
import org.lepotager.sitemanager.model.SitePolicy
import org.lepotager.sitemanager.model.SnapshotResponse
import org.lepotager.sitemanager.network.SiteApi
import org.lepotager.sitemanager.network.SiteProtocolException

class SiteRepositoryTest {
    @Test
    fun networkFailureQueuesOnlyWhenPolicyAllowsIt() = runTest {
        val fixture = Fixture(submitFailure = FakeNetworkException("offline"))
        val result = fixture.repository.submitOrQueue(
            fixture.site,
            moduleId = "home",
            action = "update_fields",
            payload = buildJsonObject { put("title", "Hello") },
        )

        assertIs<SiteRepository.SubmitResult.Queued>(result)
        assertEquals(1, fixture.queue.items.size)
        assertEquals("fixed-id", fixture.queue.items.single().clientRequestId)
        assertEquals(1234L, fixture.queue.items.single().createdAt)
        assertEquals(1, fixture.scheduler.calls)
    }

    @Test
    fun cancellationDuringSubmitIsNeverQueued() = runTest {
        val fixture = Fixture(submitFailure = CancellationException("cancelled"))
        var cancelled = false
        try {
            fixture.repository.submitOrQueue(
                fixture.site,
                moduleId = "home",
                action = "update_fields",
                payload = JsonObject(emptyMap()),
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertTrue(fixture.queue.items.isEmpty())
        assertEquals(0, fixture.scheduler.calls)
    }

    @Test
    fun protocolFailureIsNeverQueued() = runTest {
        val fixture = Fixture(submitFailure = SiteProtocolException("refused"))
        var thrown = false
        try {
            fixture.repository.submitOrQueue(
                fixture.site,
                moduleId = "home",
                action = "update_fields",
                payload = JsonObject(emptyMap()),
            )
        } catch (_: SiteProtocolException) {
            thrown = true
        }

        assertTrue(thrown)
        assertTrue(fixture.queue.items.isEmpty())
        assertEquals(0, fixture.scheduler.calls)
    }

    @Test
    fun callerMayForbidOfflineReplay() = runTest {
        val fixture = Fixture(submitFailure = FakeNetworkException("offline"))
        var thrown = false
        try {
            fixture.repository.submitOrQueue(
                fixture.site,
                moduleId = "appointments",
                action = "cancel",
                payload = JsonObject(emptyMap()),
                allowOffline = false,
            )
        } catch (_: FakeNetworkException) {
            thrown = true
        }

        assertTrue(thrown)
        assertTrue(fixture.queue.items.isEmpty())
        assertEquals(0, fixture.scheduler.calls)
    }

    @Test
    fun activationPersistsTokenCacheAndActiveSite() = runTest {
        val fixture = Fixture()
        val restored = fixture.repository.activate(fixture.manifest, "secret-token")

        assertEquals("secret-token", fixture.tokens.values["example-site"])
        assertEquals("example-site", fixture.preferences.active)
        assertEquals("example-site", fixture.sites.values.keys.single())
        assertEquals("Example", restored.config.site.displayName)
        assertFalse(fixture.sites.values.values.single().manifestJson.isBlank())
    }

    @Test
    fun concurrentFlushesDoNotSubmitTheSameChangeTwice() = runTest {
        val fixture = Fixture()
        fixture.prepareQueuedChange()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.api.submitStarted = started
        fixture.api.submitGate = release

        val first = async { fixture.repository.flushQueue() }
        started.await()
        val second = async { fixture.repository.flushQueue() }
        yield()

        assertEquals(1, fixture.api.submitCalls)
        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, fixture.api.submitCalls)
        assertEquals(listOf("queued-id"), fixture.api.submittedRequestIds)
        assertTrue(fixture.queue.items.isEmpty())
    }

    @Test
    fun networkFailureDuringFlushKeepsThePendingChange() = runTest {
        val fixture = Fixture()
        fixture.prepareQueuedChange()
        fixture.api.submitFailure = FakeNetworkException("still offline")

        assertFalse(fixture.repository.flushQueue())
        val pending = fixture.queue.items.single()
        assertEquals("queued-id", pending.clientRequestId)
        assertEquals(1, pending.attempts)
        assertEquals("still offline", pending.lastError)
    }

    @Test
    fun cancellationDuringFlushDoesNotMarkThePendingChangeAsFailed() = runTest {
        val fixture = Fixture()
        fixture.prepareQueuedChange()
        fixture.api.submitFailure = CancellationException("cancelled")
        var cancelled = false

        try {
            fixture.repository.flushQueue()
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        val pending = fixture.queue.items.single()
        assertEquals(0, pending.attempts)
        assertEquals("", pending.lastError)
    }

    @Test
    fun missingTokenForQueuedSiteNeverUsesAnotherSiteSession() = runTest {
        val fixture = Fixture()
        fixture.prepareQueuedChange()
        fixture.tokens.remove(fixture.manifest.siteId)
        fixture.tokens.values["other-site"] = "wrong-token"

        assertFalse(fixture.repository.flushQueue())
        assertEquals(0, fixture.api.submitCalls)
        assertEquals(1, fixture.queue.items.single().attempts)
        assertEquals("Site ou session introuvable", fixture.queue.items.single().lastError)
    }

    private class FakeNetworkException(message: String) : Exception(message)

    private class Fixture(submitFailure: Throwable? = null) {
        val manifest = DiscoveryManifest(
            schemaVersion = 1,
            siteId = "example-site",
            displayName = "Example",
            apiBaseUrl = "https://example.test/mobile-api/",
            authMethods = listOf("password_totp"),
        )
        private val config = SiteConfig(
            schemaVersion = 1,
            configVersion = 1,
            site = SiteIdentity("example-site", "Example"),
            policy = SitePolicy(allowOfflineQueue = true),
        )
        private val snapshot = SnapshotResponse(revision = 3)
        val api = FakeApi(config, snapshot, submitFailure)
        val sites = FakeSiteCache()
        val queue = FakeQueue()
        val preferences = FakePreferences()
        val tokens = FakeTokens()
        val scheduler = FakeScheduler()
        val repository = SiteRepository(
            api = api,
            sites = sites,
            queue = queue,
            preferences = preferences,
            tokens = tokens,
            ids = IdGenerator { "fixed-id" },
            time = TimeProvider { 1234L },
            queueScheduler = scheduler,
            networkFailures = NetworkFailureClassifier { it is FakeNetworkException },
        )
        val site = SiteRepository.RestoredSite(manifest, config, snapshot)

        init {
            tokens.values[manifest.siteId] = "existing-token"
        }

        suspend fun prepareQueuedChange() {
            repository.activate(manifest, "existing-token")
            queue.upsert(
                PendingChangeRecord(
                    clientRequestId = "queued-id",
                    siteId = manifest.siteId,
                    moduleId = "home",
                    action = "update_fields",
                    payloadJson = """{"title":"Offline"}""",
                    createdAt = 1000L,
                ),
            )
        }
    }

    private class FakeApi(
        private val config: SiteConfig,
        private val snapshot: SnapshotResponse,
        var submitFailure: Throwable?,
    ) : SiteApi {
        var submitCalls = 0
        var submitStarted: CompletableDeferred<Unit>? = null
        var submitGate: CompletableDeferred<Unit>? = null
        val submittedRequestIds = mutableListOf<String>()

        override suspend fun discover(address: String) = error("unused")
        override suspend fun startAuth(
            manifest: DiscoveryManifest,
            username: String,
            password: String,
            deviceName: String,
        ) = AuthStartResponse(status = "authenticated", deviceToken = "token")
        override suspend fun verifyTotp(
            manifest: DiscoveryManifest,
            challengeId: String,
            code: String,
        ) = AuthTokenResponse(status = "authenticated", deviceToken = "token")
        override suspend fun pair(
            manifest: DiscoveryManifest,
            code: String,
            deviceName: String,
        ) = AuthTokenResponse(status = "authenticated", deviceToken = "token")
        override suspend fun fetchConfig(manifest: DiscoveryManifest, token: String) = config
        override suspend fun fetchSnapshot(manifest: DiscoveryManifest, token: String) = snapshot
        override suspend fun submitChange(
            manifest: DiscoveryManifest,
            token: String,
            change: ChangeRequest,
        ): ChangeResponse {
            submitCalls += 1
            submittedRequestIds += change.clientRequestId
            submitStarted?.complete(Unit)
            submitGate?.await()
            submitFailure?.let { throw it }
            return ChangeResponse(status = "applied")
        }
        override suspend fun uploadMedia(
            manifest: DiscoveryManifest,
            token: String,
            moduleId: String,
            itemId: String,
            clientRequestId: String,
            metadata: JsonObject,
            fileName: String,
            mimeType: String,
            bytes: ByteArray,
        ) = ChangeResponse(status = "applied")
        override fun canonicalOrigin(address: String) = "https://example.test/"
    }

    private class FakeSiteCache : SiteCache {
        val values = linkedMapOf<String, CachedSiteRecord>()
        override suspend fun upsert(site: CachedSiteRecord) { values[site.siteId] = site }
        override suspend fun get(siteId: String) = values[siteId]
        override suspend fun delete(siteId: String) { values.remove(siteId) }
    }

    private class FakeQueue : PendingChangeStore {
        val items = mutableListOf<PendingChangeRecord>()
        override suspend fun upsert(change: PendingChangeRecord) {
            items.removeAll { it.clientRequestId == change.clientRequestId }
            items += change
        }
        override suspend fun all() = items.toList()
        override suspend fun delete(id: String) { items.removeAll { it.clientRequestId == id } }
        override suspend fun fail(id: String, error: String) {
            val index = items.indexOfFirst { it.clientRequestId == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    attempts = items[index].attempts + 1,
                    lastError = error,
                )
            }
        }
        override suspend fun count() = items.size
    }

    private class FakePreferences : ActiveSiteStore {
        var active: String? = null
        override suspend fun activeSiteId() = active
        override suspend fun setActiveSite(siteId: String?) { active = siteId }
    }

    private class FakeTokens : TokenStore {
        val values = mutableMapOf<String, String>()
        override suspend fun save(siteId: String, token: String) { values[siteId] = token }
        override suspend fun load(siteId: String) = values[siteId]
        override suspend fun remove(siteId: String) { values.remove(siteId) }
    }

    private class FakeScheduler : QueueScheduler {
        var calls = 0
        override fun schedule() { calls += 1 }
    }
}
