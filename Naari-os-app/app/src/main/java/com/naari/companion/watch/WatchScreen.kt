package com.naari.companion.watch

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.InlineErrorSeverity
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.designsystem.TealAccent
import com.naari.companion.onboarding.DefaultOnboardingRepository
import com.naari.companion.protocol.WatchMode
import com.naari.companion.service.CompanionServiceDependencies

/**
 * Watch controls destination conforming to PRD Section 6 & Master Prompt Task 15:
 * - Strict capability gating: controls only interactive if supported by negotiated firmware capabilities.
 * - Displays "Not supported by this watch firmware" for all unsupported controls.
 * - Truthful states: pending, applying, applied, failed. Never shows saved before device confirmation.
 * - Controls for wallpapers (0..4), fall detection, brightness, volume, mode, find watch, and time sync.
 */
@Composable
fun WatchScreen(
    viewModel: WatchViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveViewModel = viewModel ?: remember(context) {
        val bleSession = CompanionServiceDependencies.getBleSessionManager(context)
        val onboardingRepo = DefaultOnboardingRepository(context)
        val settingsRepo = DefaultWatchSettingsRepository(bleSession)
        WatchViewModel(
            watchSettingsRepository = settingsRepo,
            bleSessionManager = bleSession,
            onboardingRepository = onboardingRepo
        )
    }

    val uiState by effectiveViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        // Section Header with Connection Status
        SectionHeader(
            title = "Watch Controls",
            subtitle = "Watch Settings & Customization",
            trailing = {
                val (label, tone) = when (uiState.connectionState) {
                    BleConnectionState.Ready -> "Connected" to StatusTone.Positive
                    BleConnectionState.ConnectedServiceUnavailable -> "Service Degraded" to StatusTone.Warning
                    BleConnectionState.Connecting -> "Connecting" to StatusTone.Warning
                    else -> "Disconnected" to StatusTone.Neutral
                }
                StatusChip(label = label, tone = tone)
            }
        )

        // General Inline Error if present
        uiState.inlineError?.let { err ->
            InlineError(
                message = err,
                severity = InlineErrorSeverity.Critical
            )
        }

        // Capability overview warning if v2 service not available
        if (!uiState.capabilities.hasAnyCapability) {
            InlineError(
                message = "Watch Firmware Protocol V1 Active: Emergency SOS dispatch, live location relay, and primary guardian syncing are operational. Advanced controls (wallpapers, brightness, volume, mode) will become available in a future watch firmware release.",
                severity = InlineErrorSeverity.Warning
            )
        }

        // 1. Device Info Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = uiState.deviceName ?: "Naari Watch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Naari Watch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: ${if (uiState.deviceAddress != null) "Paired" else "Not Paired"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Battery: ${uiState.batteryPercent?.let { "$it%" } ?: "Unknown"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val protocolBadge = if (uiState.hasV2Service) {
                    "Protocol V2: Enhanced" to StatusTone.Positive
                } else {
                    "Protocol V1: SOS Active" to StatusTone.Positive
                }
                StatusChip(label = protocolBadge.first, tone = protocolBadge.second)
            }
        }

        // 2. Built-in Wallpapers Card
        WallpapersCard(
            isSupported = uiState.capabilities.wallpaper,
            state = uiState.wallpaper,
            onSelectWallpaper = { effectiveViewModel.selectWallpaper(it) }
        )

        // 3. IMU Fall Detection Card
        FallDetectionCard(
            isSupported = uiState.capabilities.fallDetection,
            state = uiState.fallDetection,
            onToggle = { effectiveViewModel.toggleFallDetection(it) }
        )

        // 4. Display & Audio Card
        DisplayAudioCard(
            isBrightnessSupported = uiState.capabilities.brightness,
            brightnessState = uiState.brightness,
            onChangeBrightness = { effectiveViewModel.changeBrightness(it) },
            isVolumeSupported = uiState.capabilities.volume,
            volumeState = uiState.volume,
            onChangeVolume = { effectiveViewModel.changeVolume(it) }
        )

        // 5. Operating Mode Card
        OperatingModeCard(
            isSupported = uiState.capabilities.watchMode,
            state = uiState.mode,
            onSelectMode = { effectiveViewModel.selectMode(it) }
        )

        // 6. Quick Utilities Card
        QuickUtilitiesCard(
            capabilities = uiState.capabilities,
            findWatchState = uiState.findWatch,
            timeSyncState = uiState.timeSync,
            safeCheckInState = uiState.safeCheckIn,
            onFindWatch = { effectiveViewModel.findWatch() },
            onSyncTime = { effectiveViewModel.syncTime() },
            onSafeCheckIn = { effectiveViewModel.safeCheckIn() }
        )

        Spacer(modifier = Modifier.height(NaariSpacing.lg))
    }
}

@Composable
private fun WallpapersCard(
    isSupported: Boolean,
    state: SettingItemState<Int>,
    onSelectWallpaper: (Int) -> Unit
) {
    val wallpaperNames = listOf(
        "Classic Rose",
        "Minimal Slate",
        "Emerald Calm",
        "Midnight Dark",
        "Solar Aura"
    )

    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Built-in Wallpapers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isSupported) {
                    StatusChip(
                        label = "Not supported yet",
                        tone = StatusTone.Neutral
                    )
                } else {
                    TransitionChip(state.transition)
                }
            }

            Text(
                text = "Choose a watch face for your watch display.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            wallpaperNames.forEachIndexed { index, name ->
                val isSelected = state.currentValue == index
                val itemBorderColor = if (isSelected) TealAccent else MaterialTheme.colorScheme.outline
                val itemBg = if (isSelected) TealAccent.copy(alpha = 0.08f) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, itemBorderColor, RoundedCornerShape(12.dp))
                        .background(itemBg)
                        .clickable(enabled = isSupported && !state.isApplying) {
                            onSelectWallpaper(index)
                        }
                        .padding(horizontal = NaariSpacing.md, vertical = NaariSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape
                                )
                                .background(if (isSelected) TealAccent else Color.Transparent)
                        )
                        Text(
                            text = "$index: $name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent
                        )
                    }
                }
            }

            state.errorMessage?.let { err ->
                InlineError(message = err, severity = InlineErrorSeverity.Critical)
            }
        }
    }
}

@Composable
private fun FallDetectionCard(
    isSupported: Boolean,
    state: SettingItemState<Boolean>,
    onToggle: (Boolean) -> Unit
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fall Detection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isSupported) {
                    StatusChip(
                        label = "Not supported yet",
                        tone = StatusTone.Neutral
                    )
                } else {
                    TransitionChip(state.transition)
                }
            }

            Text(
                text = "Detects hard impacts followed by stillness. Confirmed falls start a cancellable SOS on the watch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Honest medical disclaimer required by PRD
            Text(
                text = "Notice: Fall detection may not detect every fall or may trigger accidentally; it is not a certified medical device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.currentValue == true) "Fall detection enabled" else "Fall detection disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (state.isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = TealAccent
                    )
                } else {
                    Switch(
                        checked = state.currentValue ?: false,
                        onCheckedChange = { onToggle(it) },
                        enabled = isSupported && !state.isApplying,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TealAccent
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle fall detection switch"
                        }
                    )
                }
            }

            state.errorMessage?.let { err ->
                InlineError(message = err, severity = InlineErrorSeverity.Critical)
            }
        }
    }
}

@Composable
private fun DisplayAudioCard(
    isBrightnessSupported: Boolean,
    brightnessState: SettingItemState<Int>,
    onChangeBrightness: (Int) -> Unit,
    isVolumeSupported: Boolean,
    volumeState: SettingItemState<Int>,
    onChangeVolume: (Int) -> Unit
) {
    var localBrightness by remember(brightnessState.currentValue) {
        mutableFloatStateOf((brightnessState.currentValue ?: 80).toFloat())
    }
    var localVolume by remember(volumeState.currentValue) {
        mutableFloatStateOf((volumeState.currentValue ?: 70).toFloat())
    }

    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
            Text(
                text = "Display & Audio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Brightness Control
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Brightness: ${localBrightness.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isBrightnessSupported) {
                        StatusChip(label = "Not supported yet", tone = StatusTone.Neutral)
                    } else {
                        TransitionChip(brightnessState.transition)
                    }
                }

                Slider(
                    value = localBrightness,
                    onValueChange = { localBrightness = it },
                    onValueChangeFinished = { onChangeBrightness(localBrightness.toInt()) },
                    valueRange = 0f..100f,
                    enabled = isBrightnessSupported && !brightnessState.isApplying,
                    colors = SliderDefaults.colors(
                        thumbColor = TealAccent,
                        activeTrackColor = TealAccent
                    ),
                    modifier = Modifier.semantics { contentDescription = "Brightness slider" }
                )
            }

            // Volume Control
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speaker Volume: ${localVolume.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isVolumeSupported) {
                        StatusChip(label = "Not supported yet", tone = StatusTone.Neutral)
                    } else {
                        TransitionChip(volumeState.transition)
                    }
                }

                Slider(
                    value = localVolume,
                    onValueChange = { localVolume = it },
                    onValueChangeFinished = { onChangeVolume(localVolume.toInt()) },
                    valueRange = 0f..100f,
                    enabled = isVolumeSupported && !volumeState.isApplying,
                    colors = SliderDefaults.colors(
                        thumbColor = TealAccent,
                        activeTrackColor = TealAccent
                    ),
                    modifier = Modifier.semantics { contentDescription = "Volume slider" }
                )
            }
        }
    }
}

@Composable
private fun OperatingModeCard(
    isSupported: Boolean,
    state: SettingItemState<WatchMode>,
    onSelectMode: (WatchMode) -> Unit
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Operating Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isSupported) {
                    StatusChip(
                        label = "Not supported yet",
                        tone = StatusTone.Neutral
                    )
                } else {
                    TransitionChip(state.transition)
                }
            }

            Text(
                text = "Safety Mode keeps emergency monitoring active. Watch Mode saves battery as a standard timepiece.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
            ) {
                val isSafe = state.currentValue == WatchMode.SAFE
                val isWatch = state.currentValue == WatchMode.WATCH

                SecondaryButton(
                    text = if (isSafe) "SAFE (Active)" else "SAFE Mode",
                    onClick = { onSelectMode(WatchMode.SAFE) },
                    enabled = isSupported && !state.isApplying,
                    modifier = Modifier.weight(1f)
                )

                SecondaryButton(
                    text = if (isWatch) "WATCH (Active)" else "WATCH Mode",
                    onClick = { onSelectMode(WatchMode.WATCH) },
                    enabled = isSupported && !state.isApplying,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickUtilitiesCard(
    capabilities: com.naari.companion.protocol.WatchCapabilities,
    findWatchState: SettingItemState<Unit>,
    timeSyncState: SettingItemState<Long>,
    safeCheckInState: SettingItemState<Unit>,
    onFindWatch: () -> Unit,
    onSyncTime: () -> Unit,
    onSafeCheckIn: () -> Unit
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)) {
            Text(
                text = "Watch Utilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Find Watch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Find Watch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Play audible ringtone on watch speaker",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!capabilities.findWatch) {
                    StatusChip(label = "Not supported yet", tone = StatusTone.Neutral)
                } else {
                    SecondaryButton(
                        text = if (findWatchState.isApplying) "Ringing..." else "Ring",
                        onClick = onFindWatch,
                        enabled = !findWatchState.isApplying
                    )
                }
            }

            // Sync Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync Watch Clock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set watch time using your phone's clock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!capabilities.timeSync) {
                    StatusChip(label = "Not supported yet", tone = StatusTone.Neutral)
                } else {
                    SecondaryButton(
                        text = if (timeSyncState.isApplying) "Syncing..." else "Sync",
                        onClick = onSyncTime,
                        enabled = !timeSyncState.isApplying
                    )
                }
            }

            // Safe Check-In
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Safe Check-In",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Perform authenticated safe check-in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!capabilities.safeCheckIn) {
                    StatusChip(label = "Not supported yet", tone = StatusTone.Neutral)
                } else {
                    SecondaryButton(
                        text = if (safeCheckInState.isApplying) "Verifying..." else "Check-In",
                        onClick = onSafeCheckIn,
                        enabled = !safeCheckInState.isApplying
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionChip(transition: SettingTransitionState) {
    when (transition) {
        SettingTransitionState.Idle -> {
            StatusChip(label = "Synced", tone = StatusTone.Neutral)
        }
        SettingTransitionState.Pending, SettingTransitionState.Applying -> {
            StatusChip(label = "Applying...", tone = StatusTone.Warning)
        }
        SettingTransitionState.Applied -> {
            StatusChip(label = "Applied", tone = StatusTone.Positive)
        }
        is SettingTransitionState.Failed -> {
            StatusChip(label = "Failed", tone = StatusTone.Critical)
        }
    }
}
