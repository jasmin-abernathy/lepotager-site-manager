package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

/**
 * MMW v1 has older gallery adapters (`image_url`, e.g. Oscura), newer CMS
 * media entries (`thumb`/`path`), and optional album photo lists. Render
 * their images without requiring site-specific application code.
 */
internal fun galleryImageUrl(item: JsonObject, siteOrigin: String): String? {
    fun safe(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val uri = try {
            val input = URI(value.trim())
            if (input.scheme == null) {
                if (!value.startsWith("/") || value.startsWith("//")) return null
                URI(siteOrigin).resolve(input)
            } else input
        } catch (_: Exception) { return null }
        return uri.toString().takeIf {
            uri.scheme.equals("https", true) && uri.host != null &&
                uri.rawUserInfo == null && uri.port in -1..65535
        }
    }
    fun value(key: String): String? =
        (item[key] as? JsonPrimitive)?.contentOrNull

    for (field in listOf("thumb", "thumbnail_url", "image_url", "path", "url")) {
        safe(value(field))?.let { return it }
    }
    val variants = item["variants"] as? JsonObject
    for (field in listOf("thumb", "web")) {
        safe((variants?.get(field) as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull })?.let {
            return it
        }
    }
    val photos = item["photos"] as? JsonArray
    for (entry in photos.orEmpty()) {
        when (entry) {
            is JsonPrimitive -> safe(entry.contentOrNull)?.let { return it }
            is JsonObject -> {
                // Albums may contain photo objects; avoid unbounded recursion.
                for (field in listOf("thumb", "image_url", "url", "path")) {
                    safe((entry[field] as? JsonPrimitive)?.contentOrNull)?.let { return it }
                }
            }
            else -> Unit
        }
    }
    return null
}

internal fun galleryAlbumName(item: JsonObject): String =
    ((item["album"] ?: item["album_title"]) as? JsonPrimitive)
        ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Sans album"

internal fun galleryPhotoLabel(item: JsonObject): String =
    listOf("alt_text", "alt", "title")
        .firstNotNullOfOrNull { (item[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        ?: "Photo du site"
