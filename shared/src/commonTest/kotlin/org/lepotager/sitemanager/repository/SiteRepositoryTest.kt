package org.lepotager.sitemanager.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
    fun concurrentFlushesSendEachPendingRequestOnce() = runTest {
        val fixture = Fixture()
        fixture.seedPending()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.api.beforeSubmit = {
            entered.complete(Unit)
            release.await()
        }

        val first = async { fixture.repository.flushQueue() }
        entered.await()
        val secondStarted = CompletableDeferred<Unit>()
        val second = async {
            secondStarted.complete(Unit)
            fixture.repository.flushQueue()
        }
        secondStarted.await()
        yield()
        release.complete(Unit)

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf("queued-id"), fixture.api.submittedIds)
        assertTrue(fixture.queue.items.isEmpty())
    }

    @Test
    fun cancelledFlushPreservesEntryAndReleasesLock() = runTest {
        val fixture = Fixture()
        fixture.seedPending()
        val entered = CompletableDeferred<Unit>()
        fixture.api.beforeSubmit = {
            entered.complete(Unit)
            awaitCancellation()
        }

        val flush = launch { fixture.repository.flushQueue() }
        entered.await()
        flush.cancelAndJoin()

        assertEquals(0, fixture.queue.items.single().attempts)
        assertEquals("", fixture.queue.items.single().lastError)
        fixture.api.beforeSubmit = {}
        assertTrue(fixture.repository.flushQueue())
        assertEquals(listOf("queued-id", "queued-id"), fixture.api.submittedIds)
        assertTrue(fixture.queue.items.isEmpty())
    }

    @Test
    fun failedFlushRetainsRequestIdUntilSuccessfulRetry() = runTest {
        val fixture = Fixture()
        fixture.seedPending()
        fixture.api.beforeSubmit = { throw FakeNetworkException("offline") }

        assertFalse(fixture.repository.flushQueue())
        assertEquals("queued-id", fixture.queue.items.single().clientRequestId)
        assertEquals(1, fixture.queue.items.single().attempts)
        assertEquals("offline", fixture.queue.items.single().lastError)

        fixture.api.beforeSubmit = {}
        assertTrue(fixture.repository.flushQueue())
        assertEquals(listOf("queued-id", "queued-id"), fixture.api.submittedIds)
        assertTrue(fixture.queue.items.isEmpty())
    }

    @Test
    fun cancelledSubmissionIsNeverQueuedEvenWithBroadFailureClassifier() = runTest {
        val cancellation = CancellationException("cancelled")
        val fixture = Fixture(
            submitFailure = cancellation,
            networkFailures = NetworkFailureClassifier { true },
        )
        var observed: CancellationException? = null
        try {
            fixture.repository.submitOrQueue(
                fixture.site,
                moduleId = "home",
                action = "update_fields",
                payload = JsonObject(emptyMap()),
            )
        } catch (error: CancellationException) {
            observed = error
        }

        assertTrue(observed === cancellation)
        assertTrue(fixture.queue.items.isEmpty())
        assertEquals(0, fixture.scheduler.calls)
    }

    private class FakeNetworkException(message: String) : Exception(message)

    private class Fixture(
        submitFailure: Throwable? = null,
        networkFailures: NetworkFailureClassifier = NetworkFailureClassifier { it is FakeNetworkException },
    ) {
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
            networkFailures = networkFailures,
        )
        val site = SiteRepository.RestoredSite(manifest, config, snapshot)

        suspend fun seedPending() {
            repository.activate(manifest, "existing-token")
            queue.upsert(
                PendingChangeRecord(
                    clientRequestId = "queued-id",
                    siteId = manifest.siteId,
                    moduleId = "home",
                    action = "update_fields",
                    payloadJson = "{}",
                    createdAt = 1234L,
                ),
            )
        }

        init {
            tokens.values[manifest.siteId] = "existing-token"
        }
    }

    private class FakeApi(
        private val config: SiteConfig,
        private val snapshot: SnapshotResponse,
        private val submitFailure: Throwable?,
    ) : SiteApi {
        val submittedIds = mutableListOf<String>()
        var beforeSubmit: suspend () -> Unit = {}

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
            submittedIds += change.clientRequestId
            beforeSubmit()
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
        override suspend fun upsert(change: PendingChangeRecord) { items.removeAll { it.clientRequestId == change.clientRequestId }; items += change }
        override suspend fun all() = items.toList()
        override suspend fun delete(id: String) { items.removeAll { it.clientRequestId == id } }
        override suspend fun fail(id: String, error: String) {
            val index = items.indexOfFirst { it.clientRequestId == id }
            if (index >= 0) items[index] = items[index].copy(attempts = items[index].attempts + 1, lastError = error)
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
