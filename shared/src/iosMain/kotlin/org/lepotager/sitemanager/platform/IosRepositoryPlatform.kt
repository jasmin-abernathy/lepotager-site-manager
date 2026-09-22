package org.lepotager.sitemanager.platform

import org.lepotager.sitemanager.DeviceNameProvider
import org.lepotager.sitemanager.PairingInvitation
import org.lepotager.sitemanager.PairingLinkParser
import org.lepotager.sitemanager.network.isIosNetworkFailure
import org.lepotager.sitemanager.repository.IdGenerator
import org.lepotager.sitemanager.repository.NetworkFailureClassifier
import org.lepotager.sitemanager.repository.QueueScheduler
import org.lepotager.sitemanager.repository.TimeProvider
import platform.Foundation.NSDate
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

object IosIdGenerator : IdGenerator {
    override fun newId(): String = NSUUID().UUIDString()
}

object IosTimeProvider : TimeProvider {
    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

object IosNetworkFailureClassifier : NetworkFailureClassifier {
    override fun isNetworkFailure(error: Throwable): Boolean = isIosNetworkFailure(error)
}

/**
 * The queue is already persisted. iOS flushes it when the runtime starts;
 * this hook intentionally does not emulate Android WorkManager.
 */
object IosQueueScheduler : QueueScheduler {
    override fun schedule() = Unit
}

object IosDeviceNameProvider : DeviceNameProvider {
    override fun deviceName(): String = UIDevice.currentDevice.model.ifBlank { "iPhone" }
}

object IosPairingLinkParser : PairingLinkParser {
    override fun parse(raw: String): PairingInvitation {
        val components = NSURLComponents(string = raw.trim())
            ?: throw IllegalArgumentException("Ce QR code n’est pas une invitation Le Potager valide.")
        if (components.scheme != "lepotager-manager" || components.host != "pair") {
            throw IllegalArgumentException("Ce QR code n’est pas une invitation Le Potager valide.")
        }
        val items = components.queryItems.orEmpty().filterIsInstance<NSURLQueryItem>()
        val site = items.firstOrNull { it.name == "site" }?.value?.trim().orEmpty()
        val code = items.firstOrNull { it.name == "code" }?.value
            ?.filter(Char::isDigit)
            .orEmpty()
        return PairingInvitation(siteUrl = site, code = code)
    }
}
