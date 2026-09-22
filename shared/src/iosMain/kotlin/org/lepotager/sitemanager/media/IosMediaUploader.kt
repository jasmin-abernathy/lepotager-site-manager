@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.lepotager.sitemanager.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.network.SiteApi
import org.lepotager.sitemanager.network.SiteProtocolException
import org.lepotager.sitemanager.repository.IdGenerator
import org.lepotager.sitemanager.repository.SiteRepository
import org.lepotager.sitemanager.repository.SiteSessionException
import org.lepotager.sitemanager.repository.TokenStore
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.posix.memcpy

internal class IosMediaUploader(
    private val api: SiteApi,
    private val tokens: TokenStore,
    private val ids: IdGenerator,
) {
    suspend fun uploadMedia(
        site: SiteRepository.RestoredSite,
        moduleId: String,
        itemId: String,
        platformRef: String,
        metadata: JsonObject,
    ): ChangeResponse {
        val module = site.config.modules.firstOrNull { it.id == moduleId }
            ?: throw SiteProtocolException("Module introuvable.")
        val media = module.media?.takeIf { it.uploadEnabled }
            ?: throw SiteProtocolException("Ce site n’autorise pas l’ajout de média dans cette rubrique.")
        if (itemId.isBlank()) throw SiteProtocolException("Élément cible invalide.")
        if (media.maxBytes !in 1..(32L * 1024L * 1024L)) {
            throw SiteProtocolException("Limite média du site invalide.")
        }

        val tempRoot = NSTemporaryDirectory()
        if (!platformRef.startsWith(tempRoot)) {
            throw SiteProtocolException("Référence de fichier iOS invalide.")
        }
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(platformRef)) {
            throw SiteProtocolException("Le fichier sélectionné n’est plus disponible.")
        }

        val declaredSize = (manager.attributesOfItemAtPath(platformRef, error = null)
            ?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        if (declaredSize <= 0L) throw SiteProtocolException("Le fichier sélectionné est vide.")
        if (declaredSize > media.maxBytes) {
            throw SiteProtocolException("Ce fichier dépasse la taille maximale autorisée par le site.")
        }

        val fileName = platformRef.substringAfterLast('/').ifBlank { "media" }
        val mime = mimeFromFileName(fileName)
        if (mime.isBlank() || mime !in media.acceptedMimeTypes.map { it.lowercase() }) {
            throw SiteProtocolException("Format de fichier non accepté par ce site.")
        }

        val token = tokens.load(site.manifest.siteId)
            ?: throw SiteSessionException("Session de l’appareil absente.")
        return try {
            val data = NSData.dataWithContentsOfFile(platformRef)
                ?: throw SiteProtocolException("Impossible de lire le fichier sélectionné.")
            val bytes = data.toByteArray()
            if (bytes.isEmpty()) throw SiteProtocolException("Le fichier sélectionné est vide.")
            if (bytes.size.toLong() > media.maxBytes) {
                throw SiteProtocolException("Ce fichier dépasse la taille maximale autorisée par le site.")
            }
            api.uploadMedia(
                manifest = site.manifest,
                token = token,
                moduleId = moduleId,
                itemId = itemId,
                clientRequestId = ids.newId(),
                metadata = metadata,
                fileName = fileName,
                mimeType = mime,
                bytes = bytes,
            )
        } finally {
            manager.removeItemAtPath(platformRef, error = null)
        }
    }
}

private fun mimeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> ""
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}
