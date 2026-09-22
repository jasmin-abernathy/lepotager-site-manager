package org.lepotager.sitemanager.media

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.serialization.json.JsonObject
import org.lepotager.sitemanager.model.ChangeResponse
import org.lepotager.sitemanager.network.SiteApi
import org.lepotager.sitemanager.network.SiteProtocolException
import org.lepotager.sitemanager.repository.IdGenerator
import org.lepotager.sitemanager.repository.SiteRepository
import org.lepotager.sitemanager.repository.TokenStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AndroidMediaUploader(
    private val context: Context,
    private val api: SiteApi,
    private val tokens: TokenStore,
    private val ids: IdGenerator,
) {
    private fun normalizeJpegOrientation(bytes: ByteArray): ByteArray {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bytes
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw SiteProtocolException("Impossible de décoder cette photo.")
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val corrected = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { source ->
            try {
                android.graphics.Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.width,
                    source.height,
                    matrix,
                    true,
                )
            } finally {
                if (source !== bitmap) source.recycle()
            }
        } ?: run {
            bitmap.recycle()
            throw SiteProtocolException("Impossible de corriger l’orientation de cette photo.")
        }
        bitmap.recycle()
        val out = ByteArrayOutputStream()
        try {
            if (!corrected.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)) {
                throw SiteProtocolException("Impossible de préparer la photo.")
            }
            return out.toByteArray()
        } finally {
            corrected.recycle()
            out.close()
        }
    }

    suspend fun uploadMedia(
        site: SiteRepository.RestoredSite,
        moduleId: String,
        itemId: String,
        uri: Uri,
        metadata: JsonObject,
    ): ChangeResponse {
        val module = site.config.modules.firstOrNull { it.id == moduleId }
            ?: throw SiteProtocolException("Module introuvable.")
        val media = module.media?.takeIf { it.uploadEnabled }
            ?: throw SiteProtocolException("Ce site n’autorise pas l’ajout de média dans cette rubrique.")
        if (itemId.isBlank()) throw SiteProtocolException("Élément cible invalide.")

        val resolver = context.contentResolver
        var mime = resolver.getType(uri)?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (mime.isBlank() || mime !in media.acceptedMimeTypes.map { it.lowercase() }) {
            throw SiteProtocolException("Format de fichier non accepté par ce site.")
        }

        var displayName = "media"
        var declaredSize = -1L
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: displayName
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
            }
        }
        if (declaredSize > media.maxBytes) {
            throw SiteProtocolException("Ce fichier dépasse la taille maximale autorisée par le site.")
        }
        if (media.maxBytes !in 1..(32L * 1024L * 1024L)) {
            throw SiteProtocolException("Limite média du site invalide.")
        }

        var bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > media.maxBytes) {
                    throw SiteProtocolException("Ce fichier dépasse la taille maximale autorisée par le site.")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw SiteProtocolException("Impossible de lire le fichier sélectionné.")
        if (bytes.isEmpty()) throw SiteProtocolException("Le fichier sélectionné est vide.")

        if (mime == "image/jpeg") {
            bytes = normalizeJpegOrientation(bytes)
            displayName = displayName.substringBeforeLast('.', displayName) + ".jpg"
            mime = "image/jpeg"
        }
        if (bytes.size.toLong() > media.maxBytes) {
            throw SiteProtocolException("La photo préparée dépasse la taille maximale autorisée par le site.")
        }

        val token = tokens.load(site.manifest.siteId)
            ?: throw SecurityException("Session de l’appareil absente.")
        return api.uploadMedia(
            manifest = site.manifest,
            token = token,
            moduleId = moduleId,
            itemId = itemId,
            clientRequestId = ids.newId(),
            metadata = metadata,
            fileName = displayName,
            mimeType = mime,
            bytes = bytes,
        )
    }
}
