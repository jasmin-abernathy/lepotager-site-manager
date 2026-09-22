@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.LocalUIViewController
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.conformsToType
import platform.UniformTypeIdentifiers.loadFileRepresentationForContentType
import platform.UniformTypeIdentifiers.registeredContentTypes
import platform.darwin.NSObject

@Composable
internal actual fun PlatformMediaPickerButton(
    acceptedMimeTypes: List<String>,
    enabled: Boolean,
    hasSelection: Boolean,
    onPicked: (PickedMedia?) -> Unit,
) {
    val presenter = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentAccepted by rememberUpdatedState(acceptedMimeTypes.map { it.lowercase() })

    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                val result = didFinishPicking.filterIsInstance<PHPickerResult>().firstOrNull()
                if (result == null) {
                    scope.launch { currentOnPicked(null) }
                    return
                }

                val provider = result.itemProvider
                val types = provider.registeredContentTypes().filterIsInstance<UTType>()
                val accepted = currentAccepted
                val type = types.firstOrNull { candidate ->
                    val mime = candidate.preferredMIMEType?.lowercase()
                    mime != null && (accepted.isEmpty() || mime in accepted)
                } ?: types.firstOrNull { it.conformsToType(UTTypeImage) }

                if (type == null) {
                    scope.launch { currentOnPicked(null) }
                    return
                }

                provider.loadFileRepresentationForContentType(
                    contentType = type,
                    openInPlace = false,
                ) { sourceUrl, _, error ->
                    if (error != null || sourceUrl == null) {
                        scope.launch { currentOnPicked(null) }
                        return@loadFileRepresentationForContentType
                    }

                    val stored = persistPickerFile(
                        sourceUrl = sourceUrl,
                        suggestedName = provider.suggestedName,
                        extension = type.preferredFilenameExtension,
                    )
                    if (stored == null) {
                        scope.launch { currentOnPicked(null) }
                        return@loadFileRepresentationForContentType
                    }

                    val path = stored.path ?: run {
                        scope.launch { currentOnPicked(null) }
                        return@loadFileRepresentationForContentType
                    }
                    val displayName = pickerDisplayName(
                        provider.suggestedName,
                        type.preferredFilenameExtension,
                    )
                    val mime = type.preferredMIMEType?.lowercase().orEmpty()
                    val size = fileSize(path)
                    scope.launch {
                        currentOnPicked(
                            PickedMedia(
                                platformRef = path,
                                displayName = displayName,
                                mimeType = mime,
                                sizeBytes = size.takeIf { it > 0L },
                            ),
                        )
                    }
                }
            }
        }
    }

    OutlinedButton(
        onClick = {
            val configuration = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
                setFilter(PHPickerFilter.imagesFilter)
                setPreferredAssetRepresentationMode(PHPickerConfigurationAssetRepresentationModeCompatible)
                setSelectionLimit(1)
            }
            val controller = PHPickerViewController(configuration = configuration).apply {
                setDelegate(delegate)
            }
            presenter.presentViewController(controller, animated = true, completion = null)
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (hasSelection) "Changer de photo" else "Choisir une photo")
    }
}

private fun persistPickerFile(
    sourceUrl: NSURL,
    suggestedName: String?,
    extension: String?,
): NSURL? {
    val ext = extension?.trim()?.trimStart('.')?.takeIf { it.isNotBlank() }
        ?: sourceUrl.pathExtension?.takeIf { it.isNotBlank() }
        ?: "img"
    val stem = suggestedName
        ?.substringBeforeLast('.', suggestedName)
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?.take(80)
        ?.ifBlank { null }
        ?: "photo"
    val destination = NSURL.fileURLWithPath(
        "${NSTemporaryDirectory().trimEnd('/')}/manager-pick-${NSUUID().UUIDString}-$stem.$ext",
    )
    return if (NSFileManager.defaultManager.copyItemAtURL(sourceUrl, destination, null)) destination else null
}

private fun pickerDisplayName(suggestedName: String?, extension: String?): String {
    val name = suggestedName?.trim().orEmpty()
    if (name.isNotBlank() && '.' in name) return name
    val ext = extension?.trim()?.trimStart('.').orEmpty()
    return when {
        name.isNotBlank() && ext.isNotBlank() -> "$name.$ext"
        name.isNotBlank() -> name
        ext.isNotBlank() -> "photo.$ext"
        else -> "photo"
    }
}

private fun fileSize(path: String): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
    return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
}
