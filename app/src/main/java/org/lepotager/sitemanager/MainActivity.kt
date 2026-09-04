package org.lepotager.sitemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lepotager.sitemanager.ui.BrandTheme
import org.lepotager.sitemanager.ui.SiteManagerRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val primary = state.site?.config?.branding?.primary ?: state.manifest?.brandingPreview?.primary
            BrandTheme(primaryHex = primary) {
                SiteManagerRoot(state = state, vm = vm)
            }
        }
    }
}
