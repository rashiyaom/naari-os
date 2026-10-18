package com.naari.companion.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naari.companion.designsystem.MinimalistBackground
import com.naari.companion.designsystem.MinimalistBlack
import com.naari.companion.designsystem.MinimalistBorder
import com.naari.companion.designsystem.MinimalistSurface
import com.naari.companion.designsystem.MinimalistTextMuted
import com.naari.companion.designsystem.MinimalistTextPrimary
import com.naari.companion.designsystem.MinimalistTextSecondary
import com.naari.companion.designsystem.MinimalistWhite
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariShapes
import com.naari.companion.designsystem.TealAccent
import com.naari.companion.guardians.GuardiansScreen
import com.naari.companion.history.HistoryScreen
import com.naari.companion.home.HomeScreen
import com.naari.companion.landing.LandingScreen
import com.naari.companion.onboarding.DefaultOnboardingRepository
import com.naari.companion.onboarding.OnboardingScreen
import com.naari.companion.permissions.PermissionsScreen
import com.naari.companion.recordings.RecordingsScreen
import com.naari.companion.legal.TermsScreen
import com.naari.companion.settings.SettingsScreen
import com.naari.companion.watch.WatchScreen

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import com.naari.companion.designsystem.MinimalistSurfaceSecondary
import com.naari.companion.designsystem.ModernAmber
import com.naari.companion.designsystem.ModernEmerald
import com.naari.companion.designsystem.ModernRed
import com.naari.companion.designsystem.NaariBottomSheet
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.diagnostics.DiagnosticsScreen
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main application shell hosting edge-to-edge navigation, top action bar, and bottom navigation.
 * Conforms to PRD Section 4 & Task 3 specifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaariAppShell(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel? = null
) {
    val shellViewModel: ShellViewModel = viewModel ?: hiltViewModel()
    val deviceStatus by shellViewModel.deviceStatus.collectAsState()
    val batteryPercent by shellViewModel.batteryPercent.collectAsState()
    val pairedDeviceName by shellViewModel.pairedDeviceName.collectAsState()
    val pairedDeviceAddress by shellViewModel.pairedDeviceAddress.collectAsState()
    val syncStatus by shellViewModel.syncStatus.collectAsState()
    val lastSyncTimestampMs by shellViewModel.lastSyncTimestampMs.collectAsState()
    var isStatusSheetOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val onboardingRepo = remember(context) { DefaultOnboardingRepository(context) }
    val isPaired = onboardingRepo.pairedAddressSync != null
    val startDestination = remember {
        if (onboardingRepo.isCompletedSync && isPaired) {
            BottomNavDestination.Home.route
        } else {
            AppDestinations.LANDING
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    // Strict safety gate: If watch was never paired before, stay on Landing screen only
    LaunchedEffect(pairedDeviceAddress) {
        if (pairedDeviceAddress == null && currentRoute != AppDestinations.LANDING && currentRoute != AppDestinations.ONBOARDING && currentRoute != AppDestinations.TERMS) {
            navController.navigate(AppDestinations.LANDING) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isTopLevelDestination = BottomNavDestination.items.filterNotNull().any { it.route == currentRoute }
    val isBottomBarVisible = isTopLevelDestination
    val isTopBarVisible = currentRoute != AppDestinations.LANDING && currentRoute != AppDestinations.ONBOARDING && currentRoute != AppDestinations.TERMS

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isTopBarVisible) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (currentRoute) {
                                    BottomNavDestination.Home.route -> "Naari OS"
                                    BottomNavDestination.Guardians.route -> "Guardians"
                                    BottomNavDestination.Recordings.route -> "Recordings"
                                    BottomNavDestination.Watch.route -> "Watch"
                                    AppDestinations.SETTINGS -> "Settings"
                                    AppDestinations.PERMISSIONS -> "Permissions"
                                    AppDestinations.HISTORY -> "Activity History"
                                    AppDestinations.ONBOARDING -> "Setup Guide"
                                    AppDestinations.INCIDENTS -> "Safety Incidents"
                                    AppDestinations.DIAGNOSTICS -> "Diagnostics"
                                    else -> "Naari OS"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isTopLevelDestination && pairedDeviceName != null) {
                                Spacer(modifier = Modifier.width(10.dp))
                                DeviceStatusBadge(
                                    status = deviceStatus,
                                    batteryPercent = batteryPercent,
                                    onClick = { isStatusSheetOpen = true }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (!isTopLevelDestination) {
                            IconButton(
                                onClick = { navController.popBackStack() },
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
                        }
                    },
                    actions = {
                        if (isTopLevelDestination) {
                            IconButton(
                                onClick = { navController.navigate(AppDestinations.HISTORY) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(NaariShapes.iconSquircle)
                                    .background(MinimalistSurface)
                                    .border(1.dp, MinimalistBorder, NaariShapes.iconSquircle)
                                    .semantics { contentDescription = "Open Activity History" }
                            ) {
                                Icon(
                                    imageVector = NaariIcons.History,
                                    contentDescription = null,
                                    tint = MinimalistTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { navController.navigate(AppDestinations.SETTINGS) },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(40.dp)
                                    .clip(NaariShapes.iconSquircle)
                                    .background(MinimalistSurface)
                                    .border(1.dp, MinimalistBorder, NaariShapes.iconSquircle)
                                    .semantics { contentDescription = "Open Settings" }
                            ) {
                                Icon(
                                    imageVector = NaariIcons.Settings,
                                    contentDescription = null,
                                    tint = MinimalistTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MinimalistBackground,
                        titleContentColor = MinimalistTextPrimary
                    ),
                    windowInsets = WindowInsets.statusBars
                )
            }
        },
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    modifier = Modifier
                        .border(1.dp, MinimalistBorder)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MinimalistSurface,
                    tonalElevation = 0.dp
                ) {
                    BottomNavDestination.items.filterNotNull().forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    if (destination.route == BottomNavDestination.Home.route) {
                                        val popped = navController.popBackStack(BottomNavDestination.Home.route, inclusive = false)
                                        if (!popped) {
                                            navController.navigate(BottomNavDestination.Home.route) {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    } else {
                                        navController.navigate(destination.route) {
                                            popUpTo(BottomNavDestination.Home.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MinimalistWhite,
                                selectedTextColor = MinimalistTextPrimary,
                                indicatorColor = MinimalistBlack,
                                unselectedIconColor = MinimalistTextMuted,
                                unselectedTextColor = MinimalistTextSecondary
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = destination.contentDescription
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppDestinations.LANDING) {
                    LandingScreen(
                        onStartSetup = {
                            navController.navigate(AppDestinations.ONBOARDING)
                        },
                        onEnterDashboard = {
                            navController.navigate(BottomNavDestination.Home.route) {
                                popUpTo(AppDestinations.LANDING) { inclusive = true }
                            }
                        },
                        onNavigateToGuardians = {
                            navController.navigate(BottomNavDestination.Guardians.route)
                        },
                        onNavigateToPermissions = {
                            navController.navigate(AppDestinations.PERMISSIONS)
                        },
                        onNavigateToTerms = {
                            navController.navigate(AppDestinations.TERMS)
                        }
                    )
                }
                composable(BottomNavDestination.Home.route) {
                    HomeScreen(
                        onNavigateToPermissions = { navController.navigate(AppDestinations.PERMISSIONS) },
                        onNavigateToHistory = { navController.navigate(AppDestinations.HISTORY) },
                        onNavigateToGuardians = { navController.navigate(BottomNavDestination.Guardians.route) },
                        onNavigateToWatch = { navController.navigate(BottomNavDestination.Watch.route) },
                        onNavigateToOnboarding = { navController.navigate(AppDestinations.ONBOARDING) },
                        onNavigateToIncidents = { navController.navigate(AppDestinations.INCIDENTS) },
                        onNavigateToLanding = { navController.navigate(AppDestinations.LANDING) }
                    )
                }
                composable(BottomNavDestination.Guardians.route) {
                    GuardiansScreen()
                }
                composable(BottomNavDestination.Recordings.route) {
                    RecordingsScreen()
                }
                composable(BottomNavDestination.Watch.route) {
                    WatchScreen()
                }
                composable(AppDestinations.SETTINGS) {
                    SettingsScreen(
                        onNavigateToLanding = { navController.navigate(AppDestinations.LANDING) },
                        onNavigateToDiagnostics = { navController.navigate(AppDestinations.DIAGNOSTICS) },
                        onNavigateToTerms = { navController.navigate(AppDestinations.TERMS) }
                    )
                }
                composable(AppDestinations.PERMISSIONS) {
                    PermissionsScreen()
                }
                composable(AppDestinations.HISTORY) {
                    HistoryScreen()
                }
                composable(AppDestinations.ONBOARDING) {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            navController.navigate(BottomNavDestination.Home.route) {
                                popUpTo(AppDestinations.LANDING) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppDestinations.INCIDENTS) {
                    com.naari.companion.incidents.IncidentsScreen()
                }
                composable(AppDestinations.DIAGNOSTICS) {
                    DiagnosticsScreen()
                }
                composable(AppDestinations.TERMS) {
                    TermsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            if (isStatusSheetOpen) {
                NaariBottomSheet(
                    onDismissRequest = { isStatusSheetOpen = false }
                ) {
                    DeviceStatusSheetContent(
                        status = deviceStatus,
                        syncStatus = syncStatus,
                        pairedName = pairedDeviceName ?: "Naari Watch",
                        pairedAddress = pairedDeviceAddress,
                        batteryPercent = batteryPercent,
                        lastSyncTimestampMs = lastSyncTimestampMs,
                        onNavigateToWatch = {
                            isStatusSheetOpen = false
                            navController.navigate(BottomNavDestination.Watch.route)
                        },
                        onNavigateToPermissions = {
                            isStatusSheetOpen = false
                            navController.navigate(AppDestinations.PERMISSIONS)
                        },
                        onNavigateToHistory = {
                            isStatusSheetOpen = false
                            navController.navigate(AppDestinations.HISTORY)
                        },
                        onNavigateToDiagnostics = {
                            isStatusSheetOpen = false
                            navController.navigate(AppDestinations.DIAGNOSTICS)
                        },
                        onClose = { isStatusSheetOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusBadge(
    status: DeviceStatus,
    batteryPercent: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, labelText) = when (status) {
        DeviceStatus.Ready -> ModernEmerald to (batteryPercent?.let { "$it%" } ?: "Ready")
        DeviceStatus.Connected -> TealAccent to (batteryPercent?.let { "$it%" } ?: "Connected")
        DeviceStatus.Connecting -> ModernAmber to "Connecting"
        DeviceStatus.Searching -> ModernAmber to "Searching"
        is DeviceStatus.NeedsAttention -> ModernRed to "Attention"
        DeviceStatus.BluetoothOff -> ModernRed to "Bluetooth Off"
        DeviceStatus.NotPaired -> MinimalistTextMuted to "Unpaired"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MinimalistSurfaceSecondary)
            .border(1.dp, MinimalistBorder, RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Inspect watch connection details"
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "Watch status: ${status.userMessage}. Tap to inspect status details."
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MinimalistTextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceStatusSheetContent(
    status: DeviceStatus,
    syncStatus: SyncStatus,
    pairedName: String,
    pairedAddress: String?,
    batteryPercent: Int?,
    lastSyncTimestampMs: Long?,
    onNavigateToWatch: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = pairedName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MinimalistTextPrimary
                )
                Text(
                    text = pairedAddress?.let { "Device Address: $it" } ?: "Not paired with watch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MinimalistTextSecondary
                )
            }
            StatusChip(
                label = status.userMessage,
                tone = when (status) {
                    DeviceStatus.Ready -> StatusTone.Positive
                    DeviceStatus.Connected -> StatusTone.Positive
                    is DeviceStatus.NeedsAttention -> StatusTone.Critical
                    DeviceStatus.BluetoothOff -> StatusTone.Critical
                    else -> StatusTone.Neutral
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Readiness & Battery Summary Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MinimalistSurfaceSecondary)
                .border(1.dp, MinimalistBorder, RoundedCornerShape(16.dp))
                .padding(NaariSpacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Watch Battery",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextSecondary
                    )
                    Text(
                        text = batteryPercent?.let { "$it%" } ?: "Unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Contact Sync Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextSecondary
                    )
                    Text(
                        text = syncStatus.displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Last Contact",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextSecondary
                    )
                    Text(
                        text = lastSyncTimestampMs?.let {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "Never",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinimalistTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
        ) {
            SecondaryButton(
                text = "Watch Controls",
                onClick = onNavigateToWatch,
                modifier = Modifier.weight(1f),
                contentDescription = "Navigate to watch controls"
            )
            SecondaryButton(
                text = "Capabilities",
                onClick = onNavigateToPermissions,
                modifier = Modifier.weight(1f),
                contentDescription = "Navigate to permissions and capabilities"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
        ) {
            SecondaryButton(
                text = "Activity History",
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f),
                contentDescription = "Navigate to activity history"
            )
            SecondaryButton(
                text = "Diagnostics",
                onClick = onNavigateToDiagnostics,
                modifier = Modifier.weight(1f),
                contentDescription = "Navigate to diagnostics parameters"
            )
        }

        PrimaryButton(
            text = "Done",
            onClick = onClose,
            contentDescription = "Close status inspection sheet"
        )
    }
}
