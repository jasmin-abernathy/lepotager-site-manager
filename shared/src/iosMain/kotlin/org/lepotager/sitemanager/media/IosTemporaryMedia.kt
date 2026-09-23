@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.lepotager.sitemanager.media

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

private const val TEMP_MEDIA_DIRECTORY = "mon-manager-web-media"

internal fun newIosTemporaryMediaPath(fileName: String): String? {
    val directory = iosTemporaryMediaDirectory(create = true) ?: return null
    val safeName = fileName.substringAfterLast('/').trim().takeIf { it.isNotBlank() } ?: return null
    return normalizeAbsolutePosixPath("$directory/$safeName")
}

internal fun normalizedOwnedIosTemporaryMediaPath(path: String): String? {
    val normalized = normalizeAbsolutePosixPath(path) ?: return null
    val directory = iosTemporaryMediaDirectory(create = false) ?: return null
    return normalized.takeIf { it != directory && it.startsWith("$directory/") }
}

internal fun removeOwnedIosTemporaryMedia(path: String) {
    val normalized = normalizedOwnedIosTemporaryMediaPath(path) ?: return
    val manager = NSFileManager.defaultManager
    if (manager.fileExistsAtPath(normalized)) {
        manager.removeItemAtPath(normalized, error = null)
    }
}

private fun iosTemporaryMediaDirectory(create: Boolean): String? {
    val root = normalizeAbsolutePosixPath(NSTemporaryDirectory()) ?: return null
    val directory = "$root/$TEMP_MEDIA_DIRECTORY"
    if (!create) return directory
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(directory)) {
        val created = manager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!created && !manager.fileExistsAtPath(directory)) return null
    }
    return directory
}

private fun normalizeAbsolutePosixPath(raw: String): String? {
    val path = raw.trim()
    if (!path.startsWith('/')) return null
    val parts = mutableListOf<String>()
    for (part in path.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> {
                if (parts.isEmpty()) return null
                parts.removeAt(parts.lastIndex)
            }
            else -> parts += part
        }
    }
    return "/" + parts.joinToString("/")
}
