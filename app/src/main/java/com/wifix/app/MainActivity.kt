package com.wifix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.wifix.app.core.permissions.PermissionHelper
import com.wifix.app.ui.dashboard.DashboardViewModel
import com.wifix.app.ui.navigation.WiFiXApp
import com.wifix.app.ui.speedtest.SpeedTestViewModel
import com.wifix.app.ui.theme.WiFiXTheme

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val speedTestViewModel: SpeedTestViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        dashboardViewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            WiFiXTheme {
                WiFiXApp(
                    dashboardViewModel = dashboardViewModel,
                    speedTestViewModel = speedTestViewModel,
                    onRequestPermissions = {
                        permissionLauncher.launch(PermissionHelper.requiredRuntimePermissions())
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.refreshPermissions()
    }
}
