package org.lepotager.sitemanager

import android.app.Application
import org.lepotager.sitemanager.data.LocalDatabase
import org.lepotager.sitemanager.data.PreferencesStore
import org.lepotager.sitemanager.data.RoomPendingChangeStore
import org.lepotager.sitemanager.data.RoomSiteCache
import org.lepotager.sitemanager.media.AndroidMediaUploader
import org.lepotager.sitemanager.network.SiteApiClient
import org.lepotager.sitemanager.platform.AndroidIdGenerator
import org.lepotager.sitemanager.platform.AndroidNetworkFailureClassifier
import org.lepotager.sitemanager.platform.AndroidQueueScheduler
import org.lepotager.sitemanager.platform.AndroidTimeProvider
import org.lepotager.sitemanager.repository.SiteRepository
import org.lepotager.sitemanager.security.TokenVault

class SiteManagerApplication : Application() {
    lateinit var repository: SiteRepository
        private set
    lateinit var mediaUploader: AndroidMediaUploader
        private set

    override fun onCreate() {
        super.onCreate()
        val api = SiteApiClient()
        val database = LocalDatabase.get(this)
        val tokens = TokenVault(this)
        val ids = AndroidIdGenerator

        repository = SiteRepository(
            api = api,
            sites = RoomSiteCache(database),
            queue = RoomPendingChangeStore(database),
            preferences = PreferencesStore(this),
            tokens = tokens,
            ids = ids,
            time = AndroidTimeProvider,
            queueScheduler = AndroidQueueScheduler(this),
            networkFailures = AndroidNetworkFailureClassifier,
        )
        mediaUploader = AndroidMediaUploader(
            context = this,
            api = api,
            tokens = tokens,
            ids = ids,
        )
    }
}
