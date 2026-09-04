package org.lepotager.sitemanager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.lepotager.sitemanager.model.AuthStartRequest
import org.lepotager.sitemanager.model.AuthStartResponse
import org.lepotager.sitemanager.model.AuthTokenResponse
import org.lepotager.sitemanager.model.AuthVerifyRequest
import org.lepotager.sitemanager.model.ChangeRequest
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.model.DiscoveryManifest
import org.lepotager.sitemanager.model.PairRequest
import org.lepotager.sitemanager.model.SiteConfig
import org.lepotager.sitemanager.model.SnapshotResponse
import java.util.concurrent.TimeUnit

// Important : une erreur de protocole / validation n'est PAS une panne réseau. Le repository
// ne doit donc jamais la mettre dans la file offline et réessayer indéfiniment une requête refusée.
class SiteProtocolException(message: String) : RuntimeException(message)

class SiteApiClient {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun discover(address: String): DiscoveryManifest = withContext(Dispatchers.IO) {
        val origin = normalizeOrigin(address)
        val url = origin.newBuilder()
            .encodedPath("/.well-known/lepotager-site-manager.json")
            .query(null)
            .fragment(null)
            .build()
        val manifest = executeJson<DiscoveryManifest>(Request.Builder().url(url).get().build(), 64 * 1024)
        validateManifest(origin, manifest)
        manifest
    }

    suspend fun startAuth(manifest: DiscoveryManifest, username: String, password: String, deviceName: String): AuthStartResponse =
        post(manifest, "v1/auth/start", AuthStartRequest(username, password, deviceName))

    suspend fun verifyTotp(manifest: DiscoveryManifest, challengeId: String, code: String): AuthTokenResponse =
        post(manifest, "v1/auth/verify", AuthVerifyRequest(challengeId = challengeId, code = code))

    suspend fun pair(manifest: DiscoveryManifest, code: String, deviceName: String): AuthTokenResponse =
        post(manifest, "v1/auth/pair", PairRequest(code.filter(Char::isDigit), deviceName))

    suspend fun fetchConfig(manifest: DiscoveryManifest, token: String): SiteConfig =
        getAuthorized(manifest, "v1/config", token)

    suspend fun fetchSnapshot(manifest: DiscoveryManifest, token: String): SnapshotResponse =
        getAuthorized(manifest, "v1/snapshot", token)

    suspend fun submitChange(manifest: DiscoveryManifest, token: String, change: ChangeRequest): ChangeResponse =
        postAuthorized(manifest, "v1/changes", token, change)

    fun normalizeOrigin(input: String): HttpUrl {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Renseignez l'adresse du site." }
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull() ?: throw SiteProtocolException("Adresse de site invalide.")
        if (parsed.scheme != "https") throw SiteProtocolException("HTTPS est obligatoire.")
        return parsed.newBuilder().encodedPath("/").query(null).fragment(null).build()
    }

    private fun validateManifest(origin: HttpUrl, manifest: DiscoveryManifest) {
        if (manifest.schemaVersion != 1 || manifest.protocolMin > 1 || manifest.protocolMax < 1) {
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
        val api = manifest.apiBaseUrl.toHttpUrlOrNull() ?: throw SiteProtocolException("API invalide.")
        if (api.scheme != "https") throw SiteProtocolException("L'API doit utiliser HTTPS.")
        if (api.host != origin.host) {
            throw SiteProtocolException("Pour la version 1, l'API doit être hébergée sur le même domaine que le site.")
        }
    }

    private fun apiUrl(manifest: DiscoveryManifest, path: String): HttpUrl {
        val base = manifest.apiBaseUrl.toHttpUrlOrNull() ?: throw SiteProtocolException("API invalide.")
        return base.resolve(path) ?: throw SiteProtocolException("Endpoint API invalide.")
    }

    private suspend inline fun <reified T, reified R> post(manifest: DiscoveryManifest, path: String, body: T): R =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(manifest, path))
                .header("X-Lepotager-Protocol", "1")
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
                .build()
            executeJson(request)
        }

    private suspend inline fun <reified R> getAuthorized(manifest: DiscoveryManifest, path: String, token: String): R =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(manifest, path))
                .header("X-Lepotager-Protocol", "1")
                .header("X-Lepotager-Device-Token", token)
                .get()
                .build()
            executeJson(request)
        }

    private suspend inline fun <reified T, reified R> postAuthorized(
        manifest: DiscoveryManifest,
        path: String,
        token: String,
        body: T,
    ): R = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl(manifest, path))
            .header("X-Lepotager-Protocol", "1")
            .header("X-Lepotager-Device-Token", token)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
            .build()
        executeJson(request)
    }

    private inline fun <reified T> executeJson(request: Request, maxBytes: Int = 2 * 1024 * 1024): T {
        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                throw SiteProtocolException("Redirection refusée pendant la communication sécurisée. Utilisez l'adresse canonique du site.")
            }
            val body = response.body ?: throw SiteProtocolException("Réponse vide du serveur.")
            val length = body.contentLength()
            if (length > maxBytes) throw SiteProtocolException("Réponse serveur trop volumineuse.")
            val bytes = body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw SiteProtocolException("Réponse serveur trop volumineuse.")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val text = bytes.toString(Charsets.UTF_8)
            if (!response.isSuccessful) {
                throw SiteProtocolException("Erreur ${response.code} : ${extractMessage(text)}")
            }
            return try {
                json.decodeFromString(text)
            } catch (e: Exception) {
                throw SiteProtocolException("Réponse JSON invalide : ${e.message ?: "format inconnu"}")
            }
        }
    }

    private fun extractMessage(raw: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(raw).let { it as? kotlinx.serialization.json.JsonObject }
            obj?.get("message")?.toString()?.trim('"')
                ?: obj?.get("error")?.toString()?.trim('"')
        }.getOrNull() ?: "réponse refusée"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
