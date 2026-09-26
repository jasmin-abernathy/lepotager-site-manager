package org.lepotager.sitemanager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

class SiteApiClient : SiteApi {
    private val json = SiteJson.codec

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun discover(address: String): DiscoveryManifest = withContext(Dispatchers.IO) {
        val origin = normalizeOrigin(address)
        val url = origin.newBuilder()
            .encodedPath("/.well-known/lepotager-site-manager.json")
            .query(null)
            .fragment(null)
            .build()
        val manifest = executeJson<DiscoveryManifest>(Request.Builder().url(url).get().build(), 64 * 1024)
        val api = manifest.apiBaseUrl.toHttpUrlOrNull() ?: throw SiteProtocolException("API invalide.")
        SiteProtocolValidator.validateManifest(origin.toSiteOrigin(), api.toSiteOrigin(), manifest)
        manifest
    }

    override suspend fun startAuth(manifest: DiscoveryManifest, username: String, password: String, deviceName: String): AuthStartResponse =
        post(manifest, "v1/auth/start", AuthStartRequest(username, password, deviceName))

    override suspend fun verifyTotp(manifest: DiscoveryManifest, challengeId: String, code: String): AuthTokenResponse =
        post(manifest, "v1/auth/verify", AuthVerifyRequest(challengeId = challengeId, code = code))

    override suspend fun pair(manifest: DiscoveryManifest, code: String, deviceName: String): AuthTokenResponse =
        post(manifest, "v1/auth/pair", PairRequest(code.filter(Char::isDigit), deviceName))

    override suspend fun fetchConfig(manifest: DiscoveryManifest, token: String): SiteConfig =
        getAuthorized(manifest, "v1/config", token)

    override suspend fun fetchSnapshot(manifest: DiscoveryManifest, token: String): SnapshotResponse =
        getAuthorized(manifest, "v1/snapshot", token)

    override suspend fun submitChange(manifest: DiscoveryManifest, token: String, change: ChangeRequest): ChangeResponse =
        postAuthorized(manifest, "v1/changes", token, change)

    override suspend fun uploadMedia(
        manifest: DiscoveryManifest,
        token: String,
        moduleId: String,
        itemId: String,
        clientRequestId: String,
        metadata: JsonObject,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ChangeResponse = withContext(Dispatchers.IO) {
        val safeName = fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "media" }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("module_id", moduleId)
            .addFormDataPart("item_id", itemId)
            .addFormDataPart("client_request_id", clientRequestId)
            .addFormDataPart("metadata", json.encodeToString(metadata))
            .addFormDataPart("media", safeName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(apiUrl(manifest, "v1/media"))
            .header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
            .header("X-Lepotager-Device-Token", token)
            .post(body)
            .build()
        executeJson(request)
    }

    override fun canonicalOrigin(address: String): String = normalizeOrigin(address).toString()

    private fun normalizeOrigin(input: String): HttpUrl {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Renseignez l'adresse du site." }
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull() ?: throw SiteProtocolException("Adresse de site invalide.")
        if (parsed.scheme != "https") throw SiteProtocolException("HTTPS est obligatoire.")
        if (parsed.username.isNotBlank() || parsed.password.isNotBlank()) {
            throw SiteProtocolException("Les identifiants ne doivent pas être placés dans l'adresse du site.")
        }
        return parsed.newBuilder().encodedPath("/").query(null).fragment(null).build()
    }


    private fun HttpUrl.toSiteOrigin(): SiteOrigin = SiteOrigin(
        scheme = scheme,
        host = host,
        port = port,
        hasUserInfo = username.isNotBlank() || password.isNotBlank(),
    )

    private fun apiUrl(manifest: DiscoveryManifest, path: String): HttpUrl {
        val base = manifest.apiBaseUrl.toHttpUrlOrNull() ?: throw SiteProtocolException("API invalide.")
        return base.resolve(path) ?: throw SiteProtocolException("Endpoint API invalide.")
    }

    private suspend inline fun <reified T, reified R> post(manifest: DiscoveryManifest, path: String, body: T): R =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(manifest, path))
                .header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA))
                .build()
            executeJson(request)
        }

    private suspend inline fun <reified R> getAuthorized(manifest: DiscoveryManifest, path: String, token: String): R =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(manifest, path))
                .header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
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
            .header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
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