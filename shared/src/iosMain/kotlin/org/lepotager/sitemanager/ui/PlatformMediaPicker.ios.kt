package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformMediaPickerButton(
    acceptedMimeTypes: List<String>,
    enabled: Boolean,
    hasSelection: Boolean,
    onPicked: (PickedMedia?) -> Unit,
) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sélection photo iOS à connecter")
    }
}
