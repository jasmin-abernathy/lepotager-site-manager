package org.lepotager.sitemanager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthFlowTest {
    @Test fun pairingOnlyOpensCodeFieldImmediately() {
        assertEquals(AuthFlow.PAIRING, authFlowForMethods(listOf("pairing_code")))
        assertEquals(AuthFlow.PAIRING, authFlowForMethods(listOf("pairing_code"), choosePairing = false))
    }
    @Test fun passwordOnlyOpensPasswordField() {
        assertEquals(AuthFlow.PASSWORD, authFlowForMethods(listOf("password_totp")))
    }
    @Test fun combinedMethodsDefaultToPasswordButCanChoosePairing() {
        val both = listOf("password_totp", "pairing_code")
        assertEquals(AuthFlow.PASSWORD, authFlowForMethods(both))
        assertEquals(AuthFlow.PAIRING, authFlowForMethods(both, choosePairing = true))
        assertEquals(AuthFlow.PASSWORD, authFlowForMethods(both, choosePairing = false))
    }
    @Test fun unknownMethodsNeverPretendToAuthorize() {
        assertEquals(AuthFlow.NONE, authFlowForMethods(listOf("email_otp")))
        assertEquals(AuthFlow.NONE, authFlowForMethods(emptyList()))
    }
}
