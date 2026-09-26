package org.lepotager.sitemanager.ui

import androidx.compose.runtime.Composable
import org.lepotager.sitemanager.AppUiState
import org.lepotager.sitemanager.ManagerUiActions

@Composable
fun ManagerApp(state: AppUiState, actions: ManagerUiActions) {
    val primary = state.site?.config?.branding?.primary
        ?: state.manifest?.brandingPreview?.primary
    BrandTheme(primaryHex = primary) {
        SiteManagerRoot(state = state, actions = actions)
    }
}
