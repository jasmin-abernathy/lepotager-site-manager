package org.lepotager.sitemanager.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformQrScannerButton(
    enabled: Boolean,
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
)
