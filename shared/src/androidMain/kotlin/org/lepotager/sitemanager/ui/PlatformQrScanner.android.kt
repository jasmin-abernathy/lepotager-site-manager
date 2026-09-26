package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
internal actual fun PlatformQrScannerButton(
    enabled: Boolean,
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val options = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, options) { GmsBarcodeScanning.getClient(context, options) }

    OutlinedButton(
        onClick = {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue?.trim()
                    if (raw.isNullOrBlank()) onError("Ce QR code ne contient aucune invitation exploitable.")
                    else onScanned(raw)
                }
                .addOnCanceledListener { }
                .addOnFailureListener { error ->
                    onError(error.message ?: "Impossible d’ouvrir le scanner QR sur cet appareil.")
                }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Scanner le QR d’association")
    }
}
