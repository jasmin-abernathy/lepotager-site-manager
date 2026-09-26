package org.lepotager.sitemanager.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun PlatformMediaPickerButton(
    acceptedMimeTypes: List<String>,
    enabled: Boolean,
    hasSelection: Boolean,
    onPicked: (PickedMedia?) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        var name = ""
        var size: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        onPicked(PickedMedia(uri.toString(), name, mime, size))
    }
    OutlinedButton(
        onClick = {
            picker.launch(if (acceptedMimeTypes.size == 1) acceptedMimeTypes.first() else "image/*")
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (hasSelection) "Changer de photo" else "Choisir une photo")
    }
}

internal actual fun releasePlatformPickedMedia(platformRef: String) = Unit
