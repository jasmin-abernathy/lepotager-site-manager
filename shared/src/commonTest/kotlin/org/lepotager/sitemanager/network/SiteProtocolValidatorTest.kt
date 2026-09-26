package org.lepotager.sitemanager.network

import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SiteIdentity

class SiteProtocolValidatorTest {
    private val site = SiteOrigin("https", "example.test", 443)
    private val api = SiteOrigin("https", "example.test", 443)

    @Test
    fun validManifestIsAccepted() {
        SiteProtocolValidator.validateManifest(site, api, manifest())
    }

    @Test
    fun crossOriginApiIsRejected() {
        assertFailsWith<SiteProtocolException> {
            SiteProtocolValidator.validateManifest(
                site,
                SiteOrigin("https", "api.example.test", 443),
                manifest(),
            )
        }
    }

    @Test
    fun insecureApiIsRejected() {
        assertFailsWith<SiteProtocolException> {
            SiteProtocolValidator.validateManifest(
                site,
                SiteOrigin("http", "example.test", 80),
                manifest(),
            )
        }
    }

    @Test
    fun unsupportedProtocolRangeIsRejected() {
        assertFailsWith<SiteProtocolException> {
            SiteProtocolValidator.validateManifest(site, api, manifest(protocolMin = 2, protocolMax = 2))
        }
    }

    @Test
    fun invalidSiteIdIsRejected() {
        assertFailsWith<SiteProtocolException> {
            SiteProtocolValidator.validateManifest(site, api, manifest(siteId = "INVALID ID"))
        }
    }

    @Test
    fun unsupportedConfigVersionIsRejected() {
        assertFailsWith<SiteProtocolException> {
            SiteProtocolValidator.validateConfig(
                SiteConfig(
                    schemaVersion = 2,
                    configVersion = 1,
                    site = SiteIdentity(id = "example", displayName = "Example"),
                ),
            )
        }
    }

    private fun manifest(
        siteId: String = "example-site",
        protocolMin: Int = 1,
        protocolMax: Int = 1,
    ) = DiscoveryManifest(
        schemaVersion = 1,
        siteId = siteId,
        displayName = "Example",
        apiBaseUrl = "https://example.test/mobile-api/",
        authMethods = listOf("password_totp", "pairing_code"),
        protocolMin = protocolMin,
        protocolMax = protocolMax,
    )
}
