package org.lepotager.sitemanager.platform

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.lepotager.sitemanager.DeviceNameProvider
import org.lepotager.sitemanager.PairingInvitation
import org.lepotager.sitemanager.PairingLinkParser
import org.lepotager.sitemanager.repository.IdGenerator
import org.lepotager.sitemanager.repository.NetworkFailureClassifier
import org.lepotager.sitemanager.repository.QueueScheduler
import org.lepotager.sitemanager.repository.TimeProvider
import org.lepotager.sitemanager.worker.PendingChangesWorker
import java.io.IOException
import java.util.UUID

object AndroidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

object AndroidTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

object AndroidNetworkFailureClassifier : NetworkFailureClassifier {
    override fun isNetworkFailure(error: Throwable): Boolean = error is IOException
}

class AndroidQueueScheduler(context: Context) : QueueScheduler {
    private val appContext = context.applicationContext

    override fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingChangesWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "lepotager-pending-changes",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

object AndroidDeviceNameProvider : DeviceNameProvider {
    override fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Téléphone Android" }
}

object AndroidPairingLinkParser : PairingLinkParser {
    override fun parse(raw: String): PairingInvitation {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme != "lepotager-manager" || uri.host != "pair") {
            throw IllegalArgumentException("Ce QR code n’est pas une invitation Le Potager valide.")
        }
        val siteUrl = uri.getQueryParameter("site")?.trim().orEmpty()
        val code = uri.getQueryParameter("code")?.filter(Char::isDigit).orEmpty()
        return PairingInvitation(siteUrl = siteUrl, code = code)
    }
}
