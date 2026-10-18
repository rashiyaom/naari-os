package com.naari.companion.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naari.companion.R
import com.naari.companion.data.AppDatabase
import com.naari.companion.designsystem.MinimalistBlack
import com.naari.companion.designsystem.MinimalistBorder
import com.naari.companion.designsystem.MinimalistSurface
import com.naari.companion.designsystem.MinimalistSurfaceSecondary
import com.naari.companion.designsystem.MinimalistTextMuted
import com.naari.companion.designsystem.MinimalistTextPrimary
import com.naari.companion.designsystem.MinimalistTextSecondary
import com.naari.companion.designsystem.MinimalistWhite
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariShapes
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.guardians.DefaultGuardiansRepository
import com.naari.companion.onboarding.DefaultOnboardingRepository
import kotlinx.coroutines.launch

/**
 * Calm, modern, user-friendly Landing & Watch Pairing Gateway Screen.
 * Conforms to PRD Section 1 & Section 4.
 */
@Composable
fun LandingScreen(
    onStartSetup: () -> Unit,
    onEnterDashboard: () -> Unit,
    onNavigateToGuardians: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onboardingRepo = remember(context) { DefaultOnboardingRepository(context) }
    val db = remember(context) { AppDatabase.getInstance(context) }
    val guardiansRepo = remember(db) { DefaultGuardiansRepository(db.guardiansDao()) }

    val pairedAddress by onboardingRepo.pairedDeviceAddress.collectAsState(initial = null)
    val pairedName by onboardingRepo.pairedDeviceName.collectAsState(initial = null)
    val slot0 by guardiansRepo.slot0.collectAsState()
    val slot1 by guardiansRepo.slot1.collectAsState()
    val configuredGuardiansCount = (if (slot0 != null) 1 else 0) + (if (slot1 != null) 1 else 0)

    val isPaired = pairedAddress != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NaariSpacing.md, vertical = NaariSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        Spacer(modifier = Modifier.height(NaariSpacing.sm))

        // 1. Hero Brand Emblem
        Image(
            painter = painterResource(id = R.drawable.naari_logo),
            contentDescription = "Naari OS Logo",
            modifier = Modifier
                .size(92.dp)
                .clip(NaariShapes.iconSquircle)
                .border(1.dp, MinimalistBorder, NaariShapes.iconSquircle)
        )

        // 2. Welcoming Headline & Subtitle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
        ) {
            Text(
                text = "Welcome to Naari OS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MinimalistTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Personal safety, offline protection, and rapid emergency response right from your wrist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MinimalistTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = NaariSpacing.sm)
            )
        }

        // 3. Status Badges Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(label = "Works Offline", tone = StatusTone.Positive)
            Spacer(modifier = Modifier.width(NaariSpacing.xs))
            StatusChip(label = "100% Private", tone = StatusTone.Positive)
            Spacer(modifier = Modifier.width(NaariSpacing.xs))
            StatusChip(
                label = if (isPaired) "Watch Paired" else "Pairing Required",
                tone = if (isPaired) StatusTone.Positive else StatusTone.Warning
            )
        }

        Spacer(modifier = Modifier.height(NaariSpacing.xs))

        // 4. Main Action & Setup Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                if (isPaired) {
                    // State: Watch is already paired
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pairedName ?: "Naari Watch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MinimalistTextPrimary
                        )
                        StatusChip(label = "Ready & Connected", tone = StatusTone.Positive)
                    }

                    Text(
                        text = "Your watch is connected ($pairedAddress). Emergency SOS triggers, dual guardian alerts, and live location sharing are active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PrimaryButton(
                        text = "Open Companion Dashboard",
                        onClick = onEnterDashboard,
                        contentDescription = "Navigate to companion dashboard"
                    )

                    SecondaryButton(
                        text = "Pair a Different Watch",
                        onClick = {
                            coroutineScope.launch {
                                onboardingRepo.resetOnboarding()
                                onStartSetup()
                            }
                        },
                        contentDescription = "Reset pairing and pair another watch"
                    )
                } else {
                    // State: Watch is NOT paired yet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connect Your Watch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MinimalistTextPrimary
                        )
                        StatusChip(label = "Step 1", tone = StatusTone.Warning)
                    }

                    Text(
                        text = "To protect you in an emergency, let's connect your Naari Watch with this phone via secure Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PrimaryButton(
                        text = "Pair Your Watch",
                        onClick = onStartSetup,
                        contentDescription = "Start guided watch pairing and setup wizard"
                    )

                    SecondaryButton(
                        text = "Explore in Demo Mode",
                        onClick = {
                            coroutineScope.launch {
                                onboardingRepo.savePairedDevice("00:11:22:33:44:55", "Naari Watch (Demo)")
                                onboardingRepo.setOnboardingCompleted(true)
                                onEnterDashboard()
                            }
                        },
                        contentDescription = "Simulate a connected watch for demonstration"
                    )
                }
            }
        }

        // 5. How Naari Protects You
        SectionHeader(
            title = "How Naari Protects You",
            subtitle = "Three layers of reliable emergency assistance"
        )

        ProtectFeatureCard(
            stepNumber = "1",
            title = "Instant Wrist SOS",
            description = "Hold your watch button for 3 seconds to immediately send emergency SMS messages with your live GPS location to your guardians."
        )

        ProtectFeatureCard(
            stepNumber = "2",
            title = "Dual Offline Guardians",
            description = "Stores two trusted emergency contacts directly inside your watch's memory, ensuring you can call for help even if your phone screen is locked."
        )

        ProtectFeatureCard(
            stepNumber = "3",
            title = "Completely Private & Offline",
            description = "Zero accounts, zero trackers, and zero cloud uploads. Your safety history, incident ledgers, and audio memos stay entirely on your personal devices."
        )

        Spacer(modifier = Modifier.height(NaariSpacing.sm))

        // 6. Footer Links: Terms of Service & Privacy Policy
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(bottom = NaariSpacing.md)
        ) {
            Text(
                text = "By using Naari, you agree to our policies.",
                style = MaterialTheme.typography.bodySmall,
                color = MinimalistTextMuted,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onNavigateToTerms) {
                Text(
                    text = "Terms of Service & Privacy Policy",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimalistTextPrimary
                )
            }
        }
    }
}

@Composable
private fun ProtectFeatureCard(
    stepNumber: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    NaariCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(NaariShapes.iconSquircle)
                    .background(MinimalistBlack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MinimalistWhite
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MinimalistTextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinimalistTextSecondary
                )
            }
        }
    }
}
