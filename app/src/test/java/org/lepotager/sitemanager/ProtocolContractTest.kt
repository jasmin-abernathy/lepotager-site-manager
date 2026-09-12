package org.lepotager.sitemanager

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig

class ProtocolContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun discoveryExampleParses() {
        val manifest = json.decodeFromString<DiscoveryManifest>(
            """{"schema_version":1,"site_id":"example-site","display_name":"Example Site","api_base_url":"https://example.test/mobile-api/","auth_methods":["password_totp","pairing_code"]}""",
        )
        assertEquals(1, manifest.schemaVersion)
        assertEquals("example-site", manifest.siteId)
        assertTrue("password_totp" in manifest.authMethods)
    }

    @Test
    fun protocolDoesNotNeedCentralRegistry() {
        val manifest = json.decodeFromString<DiscoveryManifest>(
            """{"schema_version":1,"site_id":"example","display_name":"Example","api_base_url":"https://example.test/api/","auth_methods":["pairing_code"]}""",
        )
        assertEquals("https://example.test/api/", manifest.apiBaseUrl)
    }

    @Test
    fun mediaContractIsServerDescribed() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":7,
              "site":{"id":"example-site","display_name":"Example Site"},
              "modules":[{
                "id":"gallery",
                "kind":"gallery",
                "title":"Galerie",
                "writable":true,
                "media":{
                  "upload_enabled":true,
                  "max_bytes":12582912,
                  "accepted_mime_types":["image/jpeg","image/png","image/webp"],
                  "fields":[{
                    "id":"kind",
                    "type":"single_choice",
                    "label":"Type de photo",
                    "required":true,
                    "choices":[
                      {"value":"normal","label":"Galerie"},
                      {"value":"before","label":"Avant"},
                      {"value":"after","label":"Après"}
                    ]
                  }]
                }
              }]
            }
            """.trimIndent(),
        )

        val media = config.modules.single().media ?: error("Media config missing")
        assertTrue(media.uploadEnabled)
        assertEquals(12L * 1024L * 1024L, media.maxBytes)
        assertTrue("image/webp" in media.acceptedMimeTypes)
        assertEquals("before", media.fields.single().choices[1].value)
    }

    @Test
    fun mediaDefaultsDoNotEnableUploadUnexpectedly() {
        val config = json.decodeFromString<SiteConfig>(
            """{"schema_version":1,"config_version":1,"site":{"id":"example","display_name":"Example"},"modules":[{"id":"gallery","kind":"gallery","title":"Galerie"}]}""",
        )
        assertFalse(config.modules.single().media?.uploadEnabled ?: false)
    }

    @Test
    fun clientRequestIdIsSerializedForServerIdempotence() {
        val requestId = "123e4567-e89b-12d3-a456-426614174000"
        val request = ChangeRequest(
            moduleId = "home",
            action = "update_fields",
            clientRequestId = requestId,
            payload = buildJsonObject { put("home_title", "Nouveau titre") },
        )
        val encoded = json.encodeToString(request)
        val roundTrip = json.decodeFromString<ChangeRequest>(encoded)
        assertEquals(requestId, roundTrip.clientRequestId)
        assertEquals("home", roundTrip.moduleId)
    }
}
