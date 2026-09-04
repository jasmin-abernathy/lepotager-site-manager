package org.lepotager.sitemanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class BrandingPreview(
    val primary: String = "#4D7C59",
    @SerialName("logo_url") val logoUrl: String? = null,
)

@Serializable
data class DiscoveryManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("site_id") val siteId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("api_base_url") val apiBaseUrl: String,
    @SerialName("auth_methods") val authMethods: List<String>,
    @SerialName("branding_preview") val brandingPreview: BrandingPreview? = null,
    @SerialName("support_url") val supportUrl: String? = null,
    @SerialName("protocol_min") val protocolMin: Int = 1,
    @SerialName("protocol_max") val protocolMax: Int = 1,
)

@Serializable
data class AuthStartRequest(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class AuthStartResponse(
    val status: String,
    @SerialName("challenge_id") val challengeId: String? = null,
    val methods: List<String> = emptyList(),
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("device_token") val deviceToken: String? = null,
    val message: String? = null,
)

@Serializable
data class AuthVerifyRequest(
    @SerialName("challenge_id") val challengeId: String,
    val method: String = "totp",
    val code: String,
)

@Serializable
data class PairRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class AuthTokenResponse(
    val status: String,
    @SerialName("device_token") val deviceToken: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class SiteIdentity(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class BrandingConfig(
    val primary: String = "#4D7C59",
    val secondary: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("wordmark_url") val wordmarkUrl: String? = null,
)

@Serializable
data class SitePolicy(
    @SerialName("review_before_publish") val reviewBeforePublish: Boolean = true,
    @SerialName("allow_offline_queue") val allowOfflineQueue: Boolean = true,
)

@Serializable
data class ChoiceOption(
    val value: String,
    val label: String,
)

@Serializable
data class UiField(
    val id: String,
    val type: String,
    val label: String,
    val hint: String? = null,
    val required: Boolean = false,
    @SerialName("max_length") val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val choices: List<ChoiceOption> = emptyList(),
)

@Serializable
data class ModuleConfig(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val icon: String? = null,
    val order: Int = 0,
    val writable: Boolean = false,
    val fields: List<UiField> = emptyList(),
    val options: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class SiteConfig(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("config_version") val configVersion: Long,
    val site: SiteIdentity,
    val branding: BrandingConfig = BrandingConfig(),
    val policy: SitePolicy = SitePolicy(),
    val modules: List<ModuleConfig> = emptyList(),
)

@Serializable
data class SnapshotResponse(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val revision: Long = 0,
    val data: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ChangeRequest(
    @SerialName("module_id") val moduleId: String,
    val action: String,
    @SerialName("client_request_id") val clientRequestId: String,
    val payload: JsonObject,
)

@Serializable
data class ChangeResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val message: String? = null,
    val result: JsonElement? = null,
)
