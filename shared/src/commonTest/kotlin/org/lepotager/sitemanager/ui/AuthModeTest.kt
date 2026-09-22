package org.lepotager.sitemanager.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthModeTest {
    @Test
    fun pairingOnlySiteStartsInPairingMode() {
        assertTrue(initialPairingMode(passwordAuth = false, pairAuth = true))
    }

    @Test
    fun passwordAndPairingSiteStartsWithPassword() {
        assertFalse(initialPairingMode(passwordAuth = true, pairAuth = true))
    }

    @Test
    fun passwordOnlySiteDoesNotEnterPairingMode() {
        assertFalse(initialPairingMode(passwordAuth = true, pairAuth = false))
    }
}
