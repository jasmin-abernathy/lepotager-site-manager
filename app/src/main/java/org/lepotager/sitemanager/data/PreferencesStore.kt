package org.lepotager.sitemanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.lepotager.sitemanager.repository.ActiveSiteStore
import org.lepotager.sitemanager.repository.FormDraftStore

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class PreferencesStore(private val context: Context) : ActiveSiteStore {
    private val activeSite = stringPreferencesKey("active_site")

    override suspend fun activeSiteId(): String? = context.settingsDataStore.data.first()[activeSite]

    override suspend fun setActiveSite(siteId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (siteId == null) prefs.remove(activeSite) else prefs[activeSite] = siteId
        }
    }
}


class AndroidFormDraftStore(private val context: Context) : FormDraftStore {
    private fun key(siteId: String, moduleId: String) =
        stringPreferencesKey("form_draft.$siteId.$moduleId")

    override suspend fun load(siteId: String, moduleId: String): String? =
        context.settingsDataStore.data.first()[key(siteId, moduleId)]

    override suspend fun save(siteId: String, moduleId: String, payloadJson: String) {
        context.settingsDataStore.edit { it[key(siteId, moduleId)] = payloadJson }
    }

    override suspend fun delete(siteId: String, moduleId: String) {
        context.settingsDataStore.edit { it.remove(key(siteId, moduleId)) }
    }
}
