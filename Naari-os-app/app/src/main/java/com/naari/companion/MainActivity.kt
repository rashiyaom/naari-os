package com.naari.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naari.companion.designsystem.NaariTheme
import com.naari.companion.navigation.NaariAppShell
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.naari.companion.service.CompanionServiceController
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.onboarding.DefaultOnboardingRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isVisible: Boolean = false
            private set
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        val permissions = CompanionServiceDependencies.getPermissionsRepository(applicationContext)
        permissions.refreshPermissions()
        lifecycleScope.launch {
            if (permissions.permissions.value.canConnectBluetooth &&
                DefaultOnboardingRepository(applicationContext).pairedDeviceAddress.first() != null) {
                runCatching { CompanionServiceController.start(applicationContext) }
            }
        }
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NaariTheme {
                NaariAppShell()
            }
        }
    }
}
