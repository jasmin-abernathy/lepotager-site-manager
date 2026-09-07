package org.lepotager.sitemanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.lepotager.sitemanager.AppStage
import org.lepotager.sitemanager.AppUiState
import org.lepotager.sitemanager.MainViewModel

@Composable
fun SiteManagerRoot(state: AppUiState, vm: MainViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(targetState = state.stage, label = "app-stage") { stage ->
                when (stage) {
                    AppStage.DISCOVERY -> DiscoveryScreen(state, vm)
                    AppStage.AUTH -> AuthScreen(state, vm)
                    AppStage.TOTP -> TotpScreen(state, vm)
                    AppStage.READY -> ReadyScreen(state, vm)
                }
            }
            if (state.loading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                ) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
}
