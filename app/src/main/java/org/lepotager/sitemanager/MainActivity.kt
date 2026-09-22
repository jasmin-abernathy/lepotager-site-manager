package org.lepotager.sitemanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lepotager.sitemanager.ui.ManagerApp

class MainActivity : ComponentActivity() {
    private val incomingPairingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingPairingLink.value = intent?.dataString
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val link = incomingPairingLink.value

            LaunchedEffect(link) {
                if (!link.isNullOrBlank()) {
                    vm.pairFromLink(link)
                    incomingPairingLink.value = null
                }
            }

            ManagerApp(state = state, actions = vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPairingLink.value = intent.dataString
    }
}
