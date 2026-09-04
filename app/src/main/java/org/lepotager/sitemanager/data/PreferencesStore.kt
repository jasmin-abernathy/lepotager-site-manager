package org.lepotager.sitemanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class PreferencesStore(private val context: Context) {
    private val activeSite = stringPreferencesKey("active_site")

    suspend fun activeSiteId(): String? = context.settingsDataStore.data.first()[activeSite]

    suspend fun setActiveSite(siteId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (siteId == null) prefs.remove(activeSite) else prefs[activeSite] = siteId
        }
    }
}
