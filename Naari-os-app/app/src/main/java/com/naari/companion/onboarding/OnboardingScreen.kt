package com.naari.companion.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naari.companion.ble.CompanionDeviceManagerHelper
import com.naari.companion.data.AppDatabase
import com.naari.companion.data.Guardian
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.ReadinessBadge
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.designsystem.TealAccent
import com.naari.companion.guardians.DefaultGuardiansRepository
import com.naari.companion.permissions.AndroidPermissionsRepository
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.service.CompanionServiceDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Multi-step Onboarding setup wizard conforming to PRD Section 1 & Master Prompt Task 14:
 * install → pair → grant capabilities → set guardians → verify readiness → wear watch.
 */
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    viewModel: OnboardingViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveViewModel = viewModel ?: remember(context) {
        val onboardingRepo = DefaultOnboardingRepository(context)
        val permissionsRepo = AndroidPermissionsRepository(context)
        val db = AppDatabase.getInstance(context)
        val guardiansRepo = DefaultGuardiansRepository(db.guardiansDao())
        val bleSession = CompanionServiceDependencies.getBleSessionManager(context)
        val cdmHelper = CompanionDeviceManagerHelper(context)

        OnboardingViewModel(
            onboardingRepository = onboardingRepo,
            permissionsRepository = permissionsRepo,
            guardiansRepository = guardiansRepo,
            bleSessionManager = bleSession,
            cdmHelper = cdmHelper
        )
    }

    val state by effectiveViewModel.uiState.collectAsState()

    // Activity launcher for CompanionDeviceManager system device chooser
    val cdmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val helper = CompanionDeviceManagerHelper(context)
            val device = helper.extractDeviceFromResult(result.data)
            if (device != null) {
                val name = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            device.name ?: "Naari Watch"
                        } else "Naari Watch"
                    } else {
                        device.name ?: "Naari Watch"
                    }
                } catch (_: SecurityException) {
                    "Naari Watch"
                }
                effectiveViewModel.onDeviceDiscovered(device.address, name)
                effectiveViewModel.confirmAndPair(device.address, name)
            } else {
                effectiveViewModel.onPairingError("No watch selected. Please try again.")
            }
        } else {
            effectiveViewModel.cancelSearching()
        }
    }

    // Launch CDM system chooser when intent sender is emitted
    LaunchedEffect(Unit) {
        effectiveViewModel.cdmIntentSender.collect { intentSender ->
            cdmLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build()
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Step progress header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STEP ${state.currentStep.stepNumber} OF ${OnboardingStep.allSteps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.currentStep.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val progress = (state.currentStep.stepNumber.toFloat()) / OnboardingStep.allSteps.size
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .semantics {
                        contentDescription = "Setup progress: Step ${state.currentStep.stepNumber} of ${OnboardingStep.allSteps.size}"
                    },
                color = TealAccent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(NaariSpacing.sm))

        // Step Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "OnboardingStepTransition"
            ) { targetStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
                ) {
                    when (targetStep) {
                        is OnboardingStep.Welcome -> WelcomeStepContent()
                        is OnboardingStep.PairWatch -> PairWatchStepContent(
                            visualState = state.pairingVisualState,
                            pairedAddress = state.pairedAddress,
                            pairedName = state.pairedName,
                            isPairing = state.isPairing,
                            pairingMessage = state.pairingMessage,
                            onStartSearch = { effectiveViewModel.startSearching() },
                            onCancelSearch = { effectiveViewModel.cancelSearching() },
                            onConfirmPair = { addr, name -> effectiveViewModel.confirmAndPair(addr, name) },
                            onManualPair = { addr, name -> effectiveViewModel.pairDevice(addr, name) },
                            onUnpair = { effectiveViewModel.clearPairedDevice() }
                        )
                        is OnboardingStep.GrantCapabilities -> GrantCapabilitiesStepContent(
                            permissions = state.permissions,
                            onRefreshPermissions = { effectiveViewModel.refreshPermissions() }
                        )
                        is OnboardingStep.SetGuardians -> SetGuardiansStepContent(
                            guardians = state.guardians,
                            onSaveGuardian = { slot, name, phone -> effectiveViewModel.saveGuardian(slot, name, phone) },
                            onClearGuardian = { slot -> effectiveViewModel.clearGuardian(slot) }
                        )
                        is OnboardingStep.VerifyReadiness -> VerifyReadinessStepContent(
                            state = state,
                            onReviewCapabilities = { effectiveViewModel.goToStep(OnboardingStep.GrantCapabilities) }
                        )
                        is OnboardingStep.WearWatch -> WearWatchStepContent()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NaariSpacing.sm))

        // Navigation Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.currentStep.stepIndex > 0) {
                SecondaryButton(
                    text = "Back",
                    onClick = { effectiveViewModel.previousStep() },
                    modifier = Modifier.weight(1f),
                    contentDescription = "Navigate to previous setup step"
                )
            }

            if (state.currentStep is OnboardingStep.WearWatch) {
                PrimaryButton(
                    text = "Complete Setup & Launch",
                    onClick = { effectiveViewModel.completeOnboarding(onFinishOnboarding) },
                    modifier = Modifier.weight(1f),
                    contentDescription = "Finish setup and open Naari Home dashboard"
                )
            } else {
                PrimaryButton(
                    text = if (state.currentStep is OnboardingStep.Welcome) "Begin Setup" else "Continue",
                    onClick = { effectiveViewModel.nextStep() },
                    modifier = Modifier.weight(1f),
                    contentDescription = "Continue to next setup step"
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepContent() {
    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Welcome to Naari",
            subtitle = "Direct emergency action companion for your Naari Watch"
        )

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Direct Bluetooth Safety Link",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Naari pairs directly over authenticated Bluetooth LE. When you trigger emergency SOS or fall detection on your watch, this companion executes phone calls, SMS alerts, and location dispatches instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "100% On-Device Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No cloud servers, no account registration, and zero third-party telemetry. All guardian contacts, activity history, and audio recordings remain strictly on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "The 6-Step Setup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "1. Install app\n2. Pair physical watch\n3. Grant safety capabilities\n4. Configure guardian contacts\n5. Verify mutual readiness\n6. Wear your watch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PairWatchStepContent(
    visualState: PairingVisualState,
    pairedAddress: String?,
    pairedName: String?,
    isPairing: Boolean,
    pairingMessage: String?,
    onStartSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onConfirmPair: (String, String) -> Unit,
    onManualPair: (String, String) -> Unit,
    onUnpair: () -> Unit
) {
    var showManualFallback by remember { mutableStateOf(false) }
    var manualAddress by remember { mutableStateOf("00:11:22:33:44:55") }
    var manualName by remember { mutableStateOf("Naari Watch") }

    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Pair Your Watch",
            subtitle = "Bluetooth Companion Device association"
        )

        when (visualState) {
            is PairingVisualState.Connected -> {
                NaariCard {
                    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paired Watch",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = "Bonded & Linked",
                                tone = StatusTone.Positive
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                        ) {
                            Icon(
                                imageVector = NaariIcons.Watch,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = visualState.name.ifBlank { "Naari Watch" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = visualState.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Authenticated NimBLE link is established. Watch is ready for background synchronization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SecondaryButton(
                            text = "Unpair Watch",
                            onClick = onUnpair,
                            contentDescription = "Unpair currently connected watch"
                        )
                    }
                }
            }

            is PairingVisualState.Searching -> {
                NaariCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = NaariSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
                    ) {
                        CircularProgressIndicator(
                            color = TealAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Searching for Naari Watch...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Looking for nearby Naari BLE devices. Keep your watch awake with Settings → Bluetooth open.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        SecondaryButton(
                            text = "Cancel Search",
                            onClick = onCancelSearch,
                            contentDescription = "Cancel search for watch"
                        )
                    }
                }
            }

            is PairingVisualState.DeviceFound -> {
                NaariCard {
                    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Watch Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = "Ready to Pair",
                                tone = StatusTone.Positive
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                        ) {
                            Icon(
                                imageVector = NaariIcons.Watch,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = visualState.name.ifBlank { "Naari Watch" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = visualState.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Tap Pair below to exchange security keys and confirm the 6-digit passkey on your watch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PrimaryButton(
                            text = "Pair & Confirm Passkey",
                            onClick = { onConfirmPair(visualState.address, visualState.name) },
                            contentDescription = "Pair with discovered watch"
                        )

                        SecondaryButton(
                            text = "Search Again",
                            onClick = onStartSearch,
                            contentDescription = "Search again for watch"
                        )
                    }
                }
            }

            is PairingVisualState.Pairing -> {
                NaariCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pairing Watch",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = "Exchanging Keys",
                                tone = StatusTone.Neutral
                            )
                        }

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = TealAccent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = visualState.stepMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Please check your watch: if a 6-digit pairing code appears, confirm that it matches the prompt on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is PairingVisualState.Error -> {
                NaariCard {
                    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pairing Issue",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = "Not Paired",
                                tone = StatusTone.Critical
                            )
                        }

                        InlineError(message = visualState.message)

                        Text(
                            text = "Troubleshooting checklist:\n• Make sure the watch is powered on and near your phone\n• Open Settings → Bluetooth on the watch\n• If already paired in Android Bluetooth settings, unpair it first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PrimaryButton(
                            text = "Search Again",
                            onClick = onStartSearch,
                            contentDescription = "Retry pairing search"
                        )
                    }
                }
            }

            is PairingVisualState.Idle -> {
                NaariCard {
                    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pair Your Watch",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = "Not Paired",
                                tone = StatusTone.Warning
                            )
                        }

                        Text(
                            text = "1. Turn on your Naari Watch and open Settings → Bluetooth.\n2. Tap 'Search for Watch' below to find your device.\n3. Confirm the 6-digit passkey on both screens.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PrimaryButton(
                            text = if (isPairing) "Pairing..." else "Search for Watch",
                            onClick = onStartSearch,
                            contentDescription = "Search for nearby Naari Watch"
                        )

                        if (pairingMessage != null) {
                            Text(
                                text = pairingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TealAccent
                            )
                        }
                    }
                }

                // Collapsible manual entry fallback for debugging / test setups
                TextButton(
                    onClick = { showManualFallback = !showManualFallback },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showManualFallback) "Hide manual entry" else "Trouble finding watch? Enter address manually",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showManualFallback) {
                    NaariCard {
                        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                            Text(
                                text = "Manual Device Entry",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = manualAddress,
                                onValueChange = { manualAddress = it },
                                label = { Text("Watch MAC Address") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Device Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            SecondaryButton(
                                text = "Connect via Address",
                                onClick = { onManualPair(manualAddress, manualName) },
                                contentDescription = "Connect using manual MAC address"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantCapabilitiesStepContent(
    permissions: PermissionsSnapshot,
    onRefreshPermissions: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { onRefreshPermissions() }
    )

    val ungrantedMandatory = mutableListOf<String>()
    if (!permissions.bluetoothGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ungrantedMandatory.add(Manifest.permission.BLUETOOTH_SCAN)
            ungrantedMandatory.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            ungrantedMandatory.add(Manifest.permission.BLUETOOTH)
            ungrantedMandatory.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    if (!permissions.smsGranted) ungrantedMandatory.add(Manifest.permission.SEND_SMS)
    if (!permissions.callGranted) ungrantedMandatory.add(Manifest.permission.CALL_PHONE)
    if (permissions.locationState == LocationPermissionState.Denied) {
        ungrantedMandatory.add(Manifest.permission.ACCESS_FINE_LOCATION)
        ungrantedMandatory.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (!permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ungrantedMandatory.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!permissions.contactsGranted) {
        ungrantedMandatory.add(Manifest.permission.READ_CONTACTS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Grant Capabilities",
            subtitle = "Each capability is requested with full transparency"
        )

        // All-in-one grant banner for streamlined onboarding
        if (ungrantedMandatory.isNotEmpty()) {
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "One-Tap Setup",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusChip(
                            label = "${ungrantedMandatory.distinct().size} Pending",
                            tone = StatusTone.Warning
                        )
                    }
                    Text(
                        text = "Grant all recommended safety capabilities in one step to ensure complete emergency coverage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PrimaryButton(
                        text = "Grant All Capabilities",
                        onClick = {
                            permissionLauncher.launch(ungrantedMandatory.distinct().toTypedArray())
                        },
                        contentDescription = "Grant all pending safety permissions"
                    )
                }
            }
        }

        // 1. Nearby Devices / Bluetooth
        CapabilityCard(
            title = "Bluetooth & Nearby Devices",
            description = "Enables encrypted communication, telemetry sync, and emergency request reception from the watch.",
            isGranted = permissions.bluetoothGranted,
            degradationWarning = "Without Bluetooth, your phone cannot connect to your watch or receive any SOS alerts.",
            onRequest = {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
                }
                permissionLauncher.launch(perms)
            }
        )

        // 2. Send SMS
        CapabilityCard(
            title = "Emergency SMS Messages",
            description = "Dispatches SMS alerts with coordinates to your configured guardians when watch SOS is triggered.",
            isGranted = permissions.smsGranted,
            degradationWarning = "Without SMS permission, emergency text alerts cannot be dispatched to your guardians.",
            onRequest = {
                permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
            }
        )

        // 3. Phone Calls
        CapabilityCard(
            title = "Direct Emergency Calls",
            description = "Initiates immediate emergency phone calls to your primary guardian. Emergency numbers 112/1091 open the dialer.",
            isGranted = permissions.callGranted,
            degradationWarning = "Without call permission, Naari will open your phone dialer for manual calling instead of direct dialing.",
            onRequest = {
                permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
            }
        )

        // 4. Location
        val locationGranted = permissions.locationState == LocationPermissionState.Precise
        val locationStatus = when (permissions.locationState) {
            LocationPermissionState.Precise -> "Precise GPS"
            LocationPermissionState.Approximate -> "Approximate Only"
            LocationPermissionState.Denied -> "Denied"
        }
        val locationDegradation = when (permissions.locationState) {
            LocationPermissionState.Precise -> null
            LocationPermissionState.Approximate -> "Approximate GPS: Location is accurate to ~2 km. Precise location recommended for safety."
            LocationPermissionState.Denied -> "Without location permission, emergency SMS will send with an explicit 'Location unavailable' notice."
        }
        CapabilityCard(
            title = "Precise GPS Location",
            description = "Attaches live GPS coordinates and map links in emergency SMS messages so guardians can find you.",
            isGranted = locationGranted,
            customStatus = locationStatus,
            degradationWarning = locationDegradation,
            onRequest = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        // 5. Notifications
        CapabilityCard(
            title = "Foreground Service & Notifications",
            description = "Maintains persistent background BLE connectivity and shows live readiness alerts in notification shade.",
            isGranted = permissions.notificationsGranted,
            degradationWarning = "Without notifications, Android may stop the background service when the screen is off.",
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
        )

        // 6. Contacts
        CapabilityCard(
            title = "Contacts (Address Book)",
            description = "Allows selecting emergency contacts directly from your phone address book when setting guardians.",
            isGranted = permissions.contactsGranted,
            customStatus = if (permissions.contactsGranted) "Granted" else "Recommended",
            degradationWarning = null,
            onRequest = {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
        )
    }
}

@Composable
private fun CapabilityCard(
    title: String,
    description: String,
    isGranted: Boolean,
    customStatus: String? = null,
    degradationWarning: String? = null,
    onRequest: () -> Unit
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusChip(
                    label = customStatus ?: if (isGranted) "Granted" else "Required",
                    tone = if (isGranted) StatusTone.Positive else StatusTone.Warning
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted && degradationWarning != null) {
                Text(
                    text = degradationWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(4.dp))
                SecondaryButton(
                    text = "Grant Permission",
                    onClick = onRequest,
                    contentDescription = "Grant $title permission"
                )
            }
        }
    }
}

@Composable
private fun SetGuardiansStepContent(
    guardians: List<Guardian>,
    onSaveGuardian: (Int, String, String) -> Unit,
    onClearGuardian: (Int) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Configure Guardians",
            subtitle = "Exactly 2 hardware contact slots stored on watch firmware"
        )

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Hardware Profile Limit",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Naari Watch firmware stores exactly 2 emergency contacts in non-volatile storage. Slot 1 is the Primary guardian (called first during SOS). Slot 2 is the Secondary guardian.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (guardians.isEmpty() && !isAdding) {
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Text(
                        text = "No Guardians Configured",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "You must configure at least 1 guardian contact for emergency SOS alerts to function.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton(
                        text = "Add Primary Guardian",
                        onClick = { isAdding = true },
                        contentDescription = "Add primary guardian contact"
                    )
                }
            }
        } else {
            guardians.forEach { guardian ->
                NaariCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${guardian.name} ${if (guardian.slotIndex == 0) "(Primary)" else "(Secondary)"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = guardian.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                label = "Slot ${guardian.slotIndex + 1}",
                                tone = StatusTone.Positive
                            )
                            TextButton(onClick = { onClearGuardian(guardian.slotIndex) }) {
                                Text(
                                    text = "Remove",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            if (guardians.size < 2 && !isAdding) {
                SecondaryButton(
                    text = "Add Secondary Guardian",
                    onClick = { isAdding = true },
                    contentDescription = "Add secondary guardian contact"
                )
            }
        }

        // Inline Guardian Quick Editor
        if (isAdding) {
            val slot = if (guardians.isEmpty()) 0 else 1
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Text(
                        text = "Add Guardian (Slot ${slot + 1})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Guardian Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it },
                        label = { Text("Phone Number (+15555550101)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (inputError != null) {
                        InlineError(message = inputError!!)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                    ) {
                        SecondaryButton(
                            text = "Cancel",
                            onClick = {
                                isAdding = false
                                inputError = null
                            },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Cancel adding guardian"
                        )

                        PrimaryButton(
                            text = "Save Guardian",
                            onClick = {
                                val trimmedName = inputName.trim()
                                val trimmedPhone = inputPhone.trim()
                                if (trimmedName.isBlank()) {
                                    inputError = "Guardian name cannot be blank"
                                } else if (trimmedName.toByteArray(Charsets.UTF_8).size > 32) {
                                    inputError = "Name cannot exceed 32 bytes (hardware firmware limit)"
                                } else if (trimmedPhone.length < 3 || trimmedPhone.length > 20) {
                                    inputError = "Phone number must be between 3 and 20 digits"
                                } else {
                                    onSaveGuardian(slot, trimmedName, trimmedPhone)
                                    isAdding = false
                                    inputName = ""
                                    inputPhone = ""
                                    inputError = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Save guardian contact"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyReadinessStepContent(
    state: OnboardingUiState,
    onReviewCapabilities: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Verify Readiness",
            subtitle = "Truthful multi-factor system protection check"
        )

        // Readiness badge banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Protection Status",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            ReadinessBadge(readiness = state.readinessState)
        }

        // Contextual Readiness Explanation Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                when (state.readinessState) {
                    com.naari.companion.ble.ReadinessState.Ready -> {
                        Text(
                            text = "All Systems Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = TealAccent
                        )
                        Text(
                            text = "Mutual safety handshake confirmed. Your watch and phone are communicating, and emergency calls, SMS, and GPS map links are fully armed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    com.naari.companion.ble.ReadinessState.PermissionLimited -> {
                        Text(
                            text = "Permissions Limited",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Some safety capabilities are missing. Emergency actions will degrade gracefully (e.g. opening dialer instead of direct call, or SMS without coordinates).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SecondaryButton(
                            text = "Review Capabilities",
                            onClick = onReviewCapabilities,
                            contentDescription = "Review missing safety capabilities"
                        )
                    }
                    com.naari.companion.ble.ReadinessState.BluetoothOff -> {
                        Text(
                            text = "Bluetooth Disabled",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Bluetooth is turned off on your phone. Please enable Bluetooth in your phone settings to connect with your watch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = "Truthful State Invariant",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Naari never claims 'Ready' merely because Bluetooth is connected. Mutual hello handshake freshness (< 45s) and all safety permissions are strictly required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Itemized Checklist Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                ChecklistItem(
                    label = "Bluetooth Radio Enabled",
                    passed = state.permissions.bluetoothAdapterEnabled
                )
                ChecklistItem(
                    label = "Watch Paired & Bonded",
                    passed = state.pairedAddress != null
                )
                ChecklistItem(
                    label = "Emergency SMS Permission",
                    passed = state.permissions.smsGranted
                )
                ChecklistItem(
                    label = "Phone Call Permission",
                    passed = state.permissions.callGranted
                )
                ChecklistItem(
                    label = "Precise Location Permission",
                    passed = state.permissions.locationState == LocationPermissionState.Precise
                )
                ChecklistItem(
                    label = "At least 1 Guardian Configured",
                    passed = state.guardians.isNotEmpty()
                )
                ChecklistItem(
                    label = "Mutual Hello Handshake (<45s)",
                    passed = state.readinessState == com.naari.companion.ble.ReadinessState.Ready
                )
            }
        }
    }
}

@Composable
private fun ChecklistItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        StatusChip(
            label = if (passed) "Pass" else "Pending",
            tone = if (passed) StatusTone.Positive else StatusTone.Warning
        )
    }
}

@Composable
private fun WearWatchStepContent() {
    Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
        SectionHeader(
            title = "Wear Your Watch",
            subtitle = "Proper fit and emergency operation guide"
        )

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "1. Snug Wrist Placement",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Wear your watch snugly about one finger-width above the wrist bone. A secure fit ensures reliable impact sensing for fall detection and prevents accidental SOS clicks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "2. Physical SOS Trigger",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Press and hold the physical side button for 3 seconds to initiate SOS. The watch starts a 30-second cancellation countdown before dispatching calls and SMS alerts via your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "3. Fall Detection Notice",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "The wrist motion heuristic can miss some falls or trigger accidentally. It is an assistive safety tool and not a certified medical diagnostic device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
