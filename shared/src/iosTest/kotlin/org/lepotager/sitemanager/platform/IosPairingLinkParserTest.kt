package org.lepotager.sitemanager.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosPairingLinkParserTest {
    @Test
    fun parsesValidPairingUrlAndDecodesSite() {
        val invitation = IosPairingLinkParser.parse(
            "lepotager-manager://pair?site=https%3A%2F%2Fexample.test%2F&code=12-34-56",
        )

        assertEquals("https://example.test/", invitation.siteUrl)
        assertEquals("123456", invitation.code)
    }

    @Test
    fun trimsOuterWhitespace() {
        val invitation = IosPairingLinkParser.parse(
            "  lepotager-manager://pair?site=https%3A%2F%2Fexample.test&code=654321  ",
        )

        assertEquals("https://example.test", invitation.siteUrl)
        assertEquals("654321", invitation.code)
    }

    @Test
    fun rejectsUnexpectedScheme() {
        assertFailsWith<IllegalArgumentException> {
            IosPairingLinkParser.parse(
                "https://pair?site=https%3A%2F%2Fexample.test&code=123456",
            )
        }
    }

    @Test
    fun rejectsUnexpectedHost() {
        assertFailsWith<IllegalArgumentException> {
            IosPairingLinkParser.parse(
                "lepotager-manager://other?site=https%3A%2F%2Fexample.test&code=123456",
            )
        }
    }
}
