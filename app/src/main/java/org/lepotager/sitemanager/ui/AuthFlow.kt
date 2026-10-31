package org.lepotager.sitemanager.ui

/**
 * Every accepted site must expose a usable authentication form immediately.
 * A site that only supports pairing (for example Oscura) must never be shown
 * a password form or a blank "Site détecté" card.
 */
internal enum class AuthFlow { PASSWORD, PAIRING, NONE }

internal fun authFlowForMethods(methods: List<String>, choosePairing: Boolean = false): AuthFlow {
    val password = "password_totp" in methods
    val pairing = "pairing_code" in methods
    return when {
        pairing && (!password || choosePairing) -> AuthFlow.PAIRING
        password -> AuthFlow.PASSWORD
        else -> AuthFlow.NONE
    }
}
