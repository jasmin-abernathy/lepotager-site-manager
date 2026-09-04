package org.lepotager.sitemanager

import android.app.Application
import org.lepotager.sitemanager.data.LocalDatabase
import org.lepotager.sitemanager.data.PreferencesStore
import org.lepotager.sitemanager.network.SiteApiClient
import org.lepotager.sitemanager.repository.SiteRepository
import org.lepotager.sitemanager.security.TokenVault

class SiteManagerApplication : Application() {
    lateinit var repository: SiteRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SiteRepository(
            context = this,
            api = SiteApiClient(),
            db = LocalDatabase.get(this),
            preferences = PreferencesStore(this),
            tokens = TokenVault(this),
        )
    }
}
