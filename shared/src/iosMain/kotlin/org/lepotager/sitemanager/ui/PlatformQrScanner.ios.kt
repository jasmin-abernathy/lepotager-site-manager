@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.lepotager.sitemanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@Composable
internal actual fun PlatformQrScannerButton(
    enabled: Boolean,
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnError by rememberUpdatedState(onError)

    fun openScanner() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> scanning = true
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) scanning = true
                        else currentOnError("L’accès à la caméra est nécessaire pour scanner le QR d’association.")
                    }
                }
            }
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                currentOnError("Autorisez l’accès à la caméra dans Réglages pour scanner un QR d’association.")
            }
            else -> currentOnError("La caméra n’est pas disponible pour le scan QR.")
        }
    }

    OutlinedButton(
        onClick = ::openScanner,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Scanner le QR d’association")
    }

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false }) {
            Surface(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Placez le QR d’association devant la caméra.")
                    Box(Modifier.fillMaxWidth().height(420.dp).padding(vertical = 10.dp)) {
                        QrCameraPreview(
                            onScanned = { raw ->
                                scanning = false
                                currentOnScanned(raw)
                            },
                            onUnavailable = { message ->
                                scanning = false
                                currentOnError(message)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TextButton(onClick = { scanning = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Annuler")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    onScanned: (String) -> Unit,
    onUnavailable: (String) -> Unit,
    modifier: Modifier,
) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    val controller = remember { QrCaptureController { currentOnScanned(it) } }

    LaunchedEffect(controller.ready) {
        if (!controller.ready) onUnavailable("Aucune caméra compatible avec le scan QR n’est disponible.")
    }
    DisposableEffect(controller) {
        if (controller.ready) controller.start()
        onDispose { controller.stop() }
    }
    UIKitView(
        factory = { controller.view },
        modifier = modifier,
    )
}

private class QrCaptureController(onScanned: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    private val sessionQueue = dispatch_queue_create("org.lepotager.sitemanager.qr", null)
    private var delivered = false

    val view: UIView = object : UIView(frame = CGRectZero.readValue()) {
        override fun layoutSubviews() {
            super.layoutSubviews()
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            previewLayer.setFrame(bounds)
            CATransaction.commit()
        }
    }.apply {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection,
        ) {
            if (delivered) return
            val raw = didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstOrNull { it.type == AVMetadataObjectTypeQRCode }
                ?.stringValue
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return
            delivered = true
            stop()
            onScanned(raw)
        }
    }

    val ready: Boolean

    init {
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        var configured = input != null && session.canAddInput(input)
        if (configured && input != null) {
            session.addInput(input)
            val output = AVCaptureMetadataOutput()
            configured = session.canAddOutput(output)
            if (configured) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
                configured = AVMetadataObjectTypeQRCode in output.availableMetadataObjectTypes
                if (configured) output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }
        }
        ready = configured
    }

    fun start() {
        delivered = false
        dispatch_async(sessionQueue) {
            if (!session.running) session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
        }
    }
}
