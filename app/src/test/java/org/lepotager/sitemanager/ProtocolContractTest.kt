package org.lepotager.sitemanager

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lepotager.sitemanager.model.DiscoveryManifest

class ProtocolContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun discoveryExampleParses() {
        val manifest = json.decodeFromString<DiscoveryManifest>(
            """{"schema_version":1,"site_id":"bmh-renovation","display_name":"BMH Rénovation","api_base_url":"https://www.bmh-renovation-16.fr/mobile-api/","auth_methods":["password_totp","pairing_code"]}""",
        )
        assertEquals(1, manifest.schemaVersion)
        assertEquals("bmh-renovation", manifest.siteId)
        assertTrue("password_totp" in manifest.authMethods)
    }

    @Test
    fun protocolDoesNotNeedCentralRegistry() {
        val manifest = json.decodeFromString<DiscoveryManifest>(
            """{"schema_version":1,"site_id":"example","display_name":"Example","api_base_url":"https://example.test/api/","auth_methods":["pairing_code"]}""",
        )
        assertEquals("https://example.test/api/", manifest.apiBaseUrl)
    }
}
