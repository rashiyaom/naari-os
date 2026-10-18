package com.naari.companion.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.naari.companion.designsystem.DangerButton
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.onboarding.DefaultOnboardingRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToLanding: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onboardingRepo = remember(context) { DefaultOnboardingRepository(context) }
    val pairedAddress by onboardingRepo.pairedDeviceAddress.collectAsState(initial = null)
    val isPaired = pairedAddress != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        SectionHeader(
            title = "App Settings",
            subtitle = "Safety rules, SIM preferences, and hardware link"
        )

        // 1. Hardware Watch Link Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Hardware Watch Link",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isPaired) {
                        "Connected to Naari Watch ($pairedAddress). Emergency protection and contact sync are active."
                    } else {
                        "No watch paired. Connect your physical watch or start demo mode from the landing page."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(
                    label = if (isPaired) "Connected" else "Not Paired",
                    tone = if (isPaired) StatusTone.Positive else StatusTone.Warning
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (isPaired) {
                    DangerButton(
                        text = "Unpair Watch & Reset Setup",
                        onClick = {
                            coroutineScope.launch {
                                onboardingRepo.resetOnboarding()
                                onNavigateToLanding()
                            }
                        },
                        contentDescription = "Unpair current watch and return to landing page"
                    )
                }

                SecondaryButton(
                    text = "Open Landing & Pairing Guide",
                    onClick = onNavigateToLanding,
                    contentDescription = "Navigate to landing page"
                )
            }
        }

        // 2. SMS SIM
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "SMS Dispatch SIM",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Uses your active phone SIM card for emergency texts. If multiple SIMs are detected, you will be prompted to choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(label = "System Default SIM", tone = StatusTone.Positive)
            }
        }

        // 3. Emergency Dialing
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Emergency Dialing Policy",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Per Android safety requirements, calling emergency numbers (such as 112, 1091, or 911) opens directly in your phone's dialer so you can connect with emergency personnel immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(label = "Dialer Protection Active", tone = StatusTone.Positive)
            }
        }

        // 4. Privacy & Data Storage
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Privacy & Data Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zero analytics, zero cloud tracking. All records are stored strictly on your phone in secure local storage and excluded from cloud backups.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(label = "Local Storage Only", tone = StatusTone.Positive)
                Spacer(modifier = Modifier.height(4.dp))
                SecondaryButton(
                    text = "View Terms & Privacy Policy",
                    onClick = onNavigateToTerms,
                    contentDescription = "Read terms of service and privacy policy"
                )
            }
        }

        // 5. Diagnostics & Technical Info (Hidden Developer Section)
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Diagnostics & Technical Info",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "View hardware UUIDs, Bluetooth queue status, safety ledger parameters, and masked protocol logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                SecondaryButton(
                    text = "Open System Diagnostics",
                    onClick = onNavigateToDiagnostics,
                    contentDescription = "Open system diagnostics and technical parameters"
                )
            }
        }
    }
}
