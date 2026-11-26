package com.wifix.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifix.app.ui.components.GlassCard
import com.wifix.app.ui.components.StatusPill
import com.wifix.app.ui.components.WiFiXBackdrop
import com.wifix.app.ui.dashboard.DashboardScreen
import com.wifix.app.ui.dashboard.DashboardViewModel
import com.wifix.app.ui.theme.WiFiXColors
import kotlinx.coroutines.launch

enum class WiFiXDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val phaseNote: String
) {
    DASHBOARD("dashboard", "Home Dashboard", Icons.Rounded.Dashboard, "Phase 1 Active"),
    SPEED_TEST("speed_test", "Speed Test", Icons.Rounded.Speed, "Scheduled for Phase 2"),
    DOWNLOADS("downloads", "Downloads", Icons.Rounded.Download, "Scheduled for Phase 3"),
    BROWSER("browser", "Browser", Icons.Rounded.Public, "Scheduled for Phase 5"),
    WIFI_OPT("wifi_opt", "Wi-Fi Optimisation", Icons.Rounded.Wifi, "Scheduled for Phase 6"),
    BONDING("bonding", "Network Bonding", Icons.Rounded.Hub, "Scheduled for Phase 4"),
    ROUTER("router", "Router Manager", Icons.Rounded.Router, "Scheduled for Phase 6"),
    AI_DOCTOR("ai_doctor", "AI Network Doctor", Icons.Rounded.AutoAwesome, "Scheduled for Phase 7"),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, "Phase 1 Diagnostics Active")
}

private val BottomNavDestinations = listOf(
    WiFiXDestination.DASHBOARD,
    WiFiXDestination.SPEED_TEST,
    WiFiXDestination.DOWNLOADS,
    WiFiXDestination.BONDING,
    WiFiXDestination.WIFI_OPT
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiXApp(
    dashboardViewModel: DashboardViewModel,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: WiFiXDestination.DASHBOARD.route
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = WiFiXColors.SurfaceDeep,
                drawerContentColor = WiFiXColors.TextPrimary
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "WiFiX Suite",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    text = "Build 0.1.0 - Phase 1",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                WiFiXDestination.entries.forEach { dest ->
                    NavigationDrawerItem(
                        label = { Text(dest.label) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(WiFiXDestination.DASHBOARD.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = WiFiXColors.AccentBlue.copy(alpha = 0.2f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = WiFiXColors.TextPrimary,
                            unselectedTextColor = WiFiXColors.TextSecondary,
                            selectedIconColor = WiFiXColors.AccentBlueSoft,
                            unselectedIconColor = WiFiXColors.TextMuted
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        WiFiXBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            val activeDest = WiFiXDestination.entries.firstOrNull {
                                it.route == currentRoute
                            } ?: WiFiXDestination.DASHBOARD
                            Text(text = activeDest.label, style = MaterialTheme.typography.titleMedium)
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Open Navigation",
                                    tint = WiFiXColors.TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = WiFiXColors.SurfaceDeep.copy(alpha = 0.92f)
                    ) {
                        BottomNavDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(WiFiXDestination.DASHBOARD.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label.split(" ").first()) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = WiFiXDestination.DASHBOARD.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(WiFiXDestination.DASHBOARD.route) {
                        DashboardScreen(
                            state = dashboardState,
                            onRunLatencyProbe = { dashboardViewModel.runLatencyProbe() },
                            onRequestPermissions = onRequestPermissions
                        )
                    }

                    composable(WiFiXDestination.SETTINGS.route) {
                        SettingsPhase1Screen(
                            state = dashboardState,
                            onRequestPermissions = onRequestPermissions
                        )
                    }

                    WiFiXDestination.entries
                        .filter { it != WiFiXDestination.DASHBOARD && it != WiFiXDestination.SETTINGS }
                        .forEach { dest ->
                            composable(dest.route) {
                                UpcomingPhaseScreen(destination = dest)
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun SettingsPhase1Screen(
    state: com.wifix.app.ui.dashboard.DashboardUiState,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Permission Diagnostics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            PermissionLine("Fine Location (Wi-Fi SSID / Band)", state.permissions.locationGranted)
            PermissionLine("Phone State (4G / 5G Detection)", state.permissions.phoneStateGranted)
            PermissionLine("Notifications (Download Service)", state.permissions.notificationsGranted)
            if (!state.permissions.allCoreGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.Button(onClick = onRequestPermissions) {
                    Text("Request Missing Permissions")
                }
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Build & Architecture", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Phase 1 Foundation: Live ConnectivityManager dual-path telemetry, TrafficStats meter, TCP RTT/Jitter probe, and Material 3 Glassmorphism shell.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(
            label = if (granted) "Granted" else "Not Granted",
            dotColor = if (granted) WiFiXColors.StatusGood else WiFiXColors.StatusWarn
        )
    }
}

@Composable
private fun UpcomingPhaseScreen(destination: WiFiXDestination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill(label = destination.phaseNote, dotColor = WiFiXColors.AccentViolet)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = destination.label, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "This module is intentionally unstubbed in Phase 1 so no simulated controls or fake network actions exist. Its full native implementation will be delivered in its designated phase.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
