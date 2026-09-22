package org.lepotager.sitemanager.ui

import androidx.compose.runtime.Composable

internal data class PickedMedia(
    val platformRef: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

@Composable
internal expect fun PlatformMediaPickerButton(
    acceptedMimeTypes: List<String>,
    enabled: Boolean,
    hasSelection: Boolean,
    onPicked: (PickedMedia?) -> Unit,
)
