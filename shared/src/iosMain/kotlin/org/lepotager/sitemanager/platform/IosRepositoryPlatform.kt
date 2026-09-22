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
import platform.Foundation.NSUUID
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
 * iOS ne possède pas d’équivalent direct à WorkManager.
 * La file est persistée et vidée au prochain démarrage/retour dans le runtime ;
 * un scheduler réseau natif pourra être ajouté sans toucher au repository commun.
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
        val items = components.queryItems.orEmpty()
        val site = items.firstOrNull { it.name == "site" }?.value?.trim().orEmpty()
        val code = items.firstOrNull { it.name == "code" }?.value
            ?.filter(Char::isDigit)
            .orEmpty()
        return PairingInvitation(siteUrl = site, code = code)
    }
}
