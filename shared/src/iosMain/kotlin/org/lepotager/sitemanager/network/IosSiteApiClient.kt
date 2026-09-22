package org.lepotager.sitemanager.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
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

class IosSiteApiClient : SiteApi {
    private val json = SiteJson.codec
    private val client = HttpClient(Darwin) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 25_000
            requestTimeoutMillis = 45_000
        }
    }

    override suspend fun discover(address: String): DiscoveryManifest {
        val origin = normalizeOrigin(address)
        val discovery = URLBuilder().takeFrom(origin).apply {
            encodedPath = "/.well-known/lepotager-site-manager.json"
            parameters.clear()
            fragment = ""
        }.build()
        val manifest = executeJson<DiscoveryManifest>(client.get(discovery), 64 * 1024)
        val api = parseUrl(manifest.apiBaseUrl, "API invalide.")
        SiteProtocolValidator.validateManifest(origin.toSiteOrigin(), api.toSiteOrigin(), manifest)
        return manifest
    }

    override suspend fun startAuth(
        manifest: DiscoveryManifest,
        username: String,
        password: String,
        deviceName: String,
    ): AuthStartResponse = postJson(
        manifest,
        "v1/auth/start",
        AuthStartRequest(username, password, deviceName),
    )

    override suspend fun verifyTotp(
        manifest: DiscoveryManifest,
        challengeId: String,
        code: String,
    ): AuthTokenResponse = postJson(
        manifest,
        "v1/auth/verify",
        AuthVerifyRequest(challengeId = challengeId, code = code),
    )

    override suspend fun pair(
        manifest: DiscoveryManifest,
        code: String,
        deviceName: String,
    ): AuthTokenResponse = postJson(
        manifest,
        "v1/auth/pair",
        PairRequest(code.filter(Char::isDigit), deviceName),
    )

    override suspend fun fetchConfig(manifest: DiscoveryManifest, token: String): SiteConfig =
        getAuthorized(manifest, "v1/config", token)

    override suspend fun fetchSnapshot(manifest: DiscoveryManifest, token: String): SnapshotResponse =
        getAuthorized(manifest, "v1/snapshot", token)

    override suspend fun submitChange(
        manifest: DiscoveryManifest,
        token: String,
        change: ChangeRequest,
    ): ChangeResponse = postAuthorized(manifest, "v1/changes", token, change)

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
    ): ChangeResponse {
        val safeName = fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "media" }
        val response = client.post(apiUrl(manifest, "v1/media")) {
            protocolHeaders(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("module_id", moduleId)
                        append("item_id", itemId)
                        append("client_request_id", clientRequestId)
                        append("metadata", json.encodeToString(metadata))
                        append(
                            "media",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                            },
                        )
                    },
                ),
            )
        }
        return executeJson(response)
    }

    override fun canonicalOrigin(address: String): String = normalizeOrigin(address).toString()

    private fun normalizeOrigin(input: String): Url {
        val trimmed = input.trim()
        if (trimmed.isBlank()) throw SiteProtocolException("Renseignez l’adresse du site.")
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = parseUrl(withScheme, "Adresse de site invalide.")
        if (parsed.protocol != URLProtocol.HTTPS) throw SiteProtocolException("HTTPS est obligatoire.")
        if (!parsed.user.isNullOrBlank() || !parsed.password.isNullOrBlank()) {
            throw SiteProtocolException("Les identifiants ne doivent pas être placés dans l’adresse du site.")
        }
        return URLBuilder().takeFrom(parsed).apply {
            encodedPath = "/"
            parameters.clear()
            fragment = ""
        }.build()
    }

    private fun parseUrl(raw: String, errorMessage: String): Url =
        try {
            Url(raw)
        } catch (_: Exception) {
            throw SiteProtocolException(errorMessage)
        }

    private fun Url.toSiteOrigin(): SiteOrigin = SiteOrigin(
        scheme = protocol.name.lowercase(),
        host = host,
        port = port,
        hasUserInfo = !user.isNullOrBlank() || !password.isNullOrBlank(),
    )

    private fun apiUrl(manifest: DiscoveryManifest, path: String): Url {
        val base = parseUrl(manifest.apiBaseUrl, "API invalide.")
        if (base.protocol != URLProtocol.HTTPS) throw SiteProtocolException("L’API doit utiliser HTTPS.")
        val prefix = base.encodedPath.trimEnd('/')
        return URLBuilder().takeFrom(base).apply {
            encodedPath = "$prefix/${path.trimStart('/')}"
            parameters.clear()
            fragment = ""
        }.build()
    }

    private suspend inline fun <reified T, reified R> postJson(
        manifest: DiscoveryManifest,
        path: String,
        body: T,
    ): R {
        val response = client.post(apiUrl(manifest, path)) {
            header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }
        return executeJson(response)
    }

    private suspend inline fun <reified R> getAuthorized(
        manifest: DiscoveryManifest,
        path: String,
        token: String,
    ): R {
        val response = client.get(apiUrl(manifest, path)) { protocolHeaders(token) }
        return executeJson(response)
    }

    private suspend inline fun <reified T, reified R> postAuthorized(
        manifest: DiscoveryManifest,
        path: String,
        token: String,
        body: T,
    ): R {
        val response = client.post(apiUrl(manifest, path)) {
            protocolHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }
        return executeJson(response)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.protocolHeaders(token: String? = null) {
        header("X-Lepotager-Protocol", SiteProtocolValidator.PROTOCOL_VERSION.toString())
        if (token != null) header("X-Lepotager-Device-Token", token)
    }

    private suspend inline fun <reified T> executeJson(
        response: HttpResponse,
        maxBytes: Int = 2 * 1024 * 1024,
    ): T {
        val status = response.status.value
        if (status in 300..399) {
            throw SiteProtocolException(
                "Redirection refusée pendant la communication sécurisée. Utilisez l’adresse canonique du site.",
            )
        }
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > maxBytes) {
            throw SiteProtocolException("Réponse serveur trop volumineuse.")
        }
        val text = response.bodyAsText()
        if (text.encodeToByteArray().size > maxBytes) {
            throw SiteProtocolException("Réponse serveur trop volumineuse.")
        }
        if (status !in 200..299) {
            throw SiteProtocolException("Erreur $status : ${extractMessage(text)}")
        }
        return try {
            json.decodeFromString(text)
        } catch (e: Exception) {
            throw SiteProtocolException("Réponse JSON invalide : ${e.message ?: "format inconnu"}")
        }
    }

    private fun extractMessage(raw: String): String =
        runCatching {
            val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
            obj?.get("message")?.toString()?.removeSurrounding("\"")
                ?: obj?.get("error")?.toString()?.removeSurrounding("\"")
        }.getOrNull() ?: "réponse refusée"
}

internal fun isIosNetworkFailure(error: Throwable): Boolean =
    error is DarwinHttpRequestException || error is HttpRequestTimeoutException
