package com.naari.companion.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naari.companion.designsystem.MinimalistBackground
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
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone

/**
 * Dedicated Terms of Service & Privacy Policy screen.
 * Conforms to PRD Section 4 & Section 8 transparency requirements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "Terms of Service" else "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(NaariShapes.iconSquircle)
                            .background(MinimalistBlack)
                            .semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = NaariIcons.ArrowBack,
                            contentDescription = null,
                            tint = MinimalistWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MinimalistBackground,
                    titleContentColor = MinimalistTextPrimary
                )
            )
        },
        containerColor = MinimalistBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MinimalistSurface,
                contentColor = MinimalistTextPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MinimalistBlack
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Terms of Service",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) MinimalistTextPrimary else MinimalistTextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Privacy Policy",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) MinimalistTextPrimary else MinimalistTextSecondary
                        )
                    }
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(NaariSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
            ) {
                if (selectedTab == 0) {
                    TermsOfServiceContent()
                } else {
                    PrivacyPolicyContent()
                }

                Spacer(modifier = Modifier.height(NaariSpacing.lg))
            }
        }
    }
}

@Composable
private fun TermsOfServiceContent() {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Naari Safety System",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MinimalistTextPrimary
                )
                StatusChip(label = "Version 1.0", tone = StatusTone.Neutral)
            }
            Text(
                text = "Last updated: October 2026. Please read these terms carefully before using the Naari OS Companion application.",
                style = MaterialTheme.typography.bodySmall,
                color = MinimalistTextSecondary
            )
        }
    }

    LegalSectionCard(
        title = "1. Scope of Safety Assistance",
        body = "Naari is designed as a companion safety tool that bridges your physical Naari Watch to your phone's native telephony, SMS, and GPS subsystems. While Naari facilitates rapid emergency dispatch to your nominated guardians, it is not a replacement for professional emergency first responders or guaranteed telecommunications services."
    )

    LegalSectionCard(
        title = "2. Emergency Number Interception",
        body = "For safety and legal compliance, Naari strictly NEVER places automated calls directly to public emergency services (e.g., 112, 1091, 911, 999). If a distress event targets a government emergency line, Naari will safely launch your phone's native dialer pre-populated with the number, requiring deliberate user initiation."
    )

    LegalSectionCard(
        title = "3. Cellular Network & GPS Availability",
        body = "Emergency SMS and location sharing depend entirely on your mobile carrier signal, cellular account balance, and device GPS reception. If GPS is unavailable (e.g. deep indoors or underground), alerts will send with an explicit 'Location unavailable' label rather than failing silently."
    )

    LegalSectionCard(
        title = "4. Hardware Guardian Memory",
        body = "You are responsible for keeping your two designated emergency guardian contacts accurate and formatted with valid country codes. Guardian information is synced to the watch's non-volatile memory so alerts can be triggered even when disconnected from the phone."
    )

    LegalSectionCard(
        title = "5. Background Reliability",
        body = "To monitor for watch triggers while your phone is in your pocket or locked, Naari operates an ongoing Android foreground service. You should disable aggressive OS battery optimization for Naari to prevent Android from terminating the Bluetooth background listener."
    )
}

@Composable
private fun PrivacyPolicyContent() {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy First Commitment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MinimalistTextPrimary
                )
                StatusChip(label = "Zero Tracking", tone = StatusTone.Positive)
            }
            Text(
                text = "Naari is engineered with a strict offline-first privacy architecture. We believe your safety data belongs solely to you.",
                style = MaterialTheme.typography.bodySmall,
                color = MinimalistTextSecondary
            )
        }
    }

    LegalSectionCard(
        title = "1. Zero Cloud Uploads",
        body = "Naari contains NO third-party trackers, analytics SDKs, advertising libraries, or cloud telemetry backends. Your emergency logs, guardian contacts, location fixes, and identity never leave your personal devices."
    )

    LegalSectionCard(
        title = "2. Local Device Storage",
        body = "All activity ledgers, incident records, and guardian settings are stored strictly in a private, sandboxed SQLite database on your phone. No external servers have access to your personal safety history."
    )

    LegalSectionCard(
        title = "3. Audio Voice Memos",
        body = "Incident audio memos captured by the watch are streamed directly over encrypted Bluetooth to your phone's private storage (filesDir/recordings). Voice memos are never uploaded, and deleting a recording on your phone removes only the local copy, preserving watch hardware integrity."
    )

    LegalSectionCard(
        title = "4. Contacts & Address Book",
        body = "Contacts permission is used strictly to provide an optional contact picker when configuring your two guardians. Naari never reads, scans, or syncs your wider address book."
    )

    LegalSectionCard(
        title = "5. GPS & Location Data",
        body = "Location permission is requested solely to fetch a one-shot GPS fix when an SOS emergency alert is triggered, allowing your designated guardians to locate you quickly. Background location is never continuously tracked."
    )
}

@Composable
private fun LegalSectionCard(
    title: String,
    body: String
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MinimalistTextPrimary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MinimalistTextSecondary
            )
        }
    }
}
