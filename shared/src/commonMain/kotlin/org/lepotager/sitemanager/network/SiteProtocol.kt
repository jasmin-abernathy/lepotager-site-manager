package org.lepotager.sitemanager.network

import kotlinx.serialization.json.Json
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.SiteConfig

// Une erreur de protocole / validation n’est pas une panne réseau et ne doit
// jamais être rejouée indéfiniment depuis la file offline.
class SiteProtocolException(message: String) : RuntimeException(message)

object SiteJson {
    val codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
}

data class SiteOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
    val hasUserInfo: Boolean = false,
)

object SiteProtocolValidator {
    const val PROTOCOL_VERSION = 1

    fun validateManifest(
        siteOrigin: SiteOrigin,
        apiOrigin: SiteOrigin,
        manifest: DiscoveryManifest,
    ) {
        if (manifest.schemaVersion != PROTOCOL_VERSION ||
            manifest.protocolMin > PROTOCOL_VERSION ||
            manifest.protocolMax < PROTOCOL_VERSION
        ) {
            throw SiteProtocolException("Version du protocole non prise en charge.")
        }
        if (!Regex("^[a-z0-9][a-z0-9._-]{1,63}$").matches(manifest.siteId)) {
            throw SiteProtocolException("Identifiant de site invalide.")
        }
        if (manifest.displayName.isBlank() || manifest.displayName.length > 100) {
            throw SiteProtocolException("Nom du site invalide.")
        }
        if (manifest.authMethods.none { it == "password_totp" || it == "pairing_code" }) {
            throw SiteProtocolException("Aucune méthode de connexion compatible.")
        }
        if (siteOrigin.scheme != "https") throw SiteProtocolException("HTTPS est obligatoire.")
        if (siteOrigin.hasUserInfo) {
            throw SiteProtocolException("Les identifiants ne doivent pas être placés dans l’adresse du site.")
        }
        if (apiOrigin.scheme != "https") throw SiteProtocolException("L’API doit utiliser HTTPS.")
        if (apiOrigin.hasUserInfo) throw SiteProtocolException("API invalide.")
        if (apiOrigin.host != siteOrigin.host || apiOrigin.port != siteOrigin.port) {
            throw SiteProtocolException("Pour la version 1, l’API doit être hébergée sur le même domaine que le site.")
        }
    }

    fun validateConfig(config: SiteConfig) {
        if (config.schemaVersion != PROTOCOL_VERSION) {
            throw SiteProtocolException("Configuration du site non prise en charge.")
        }
    }
}
