package org.lepotager.sitemanager.ui

internal fun initialPairingMode(passwordAuth: Boolean, pairAuth: Boolean): Boolean =
    pairAuth && !passwordAuth
