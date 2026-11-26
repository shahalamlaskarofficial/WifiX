package com.wifix.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.ui.components.GlassCard
import com.wifix.app.ui.components.LiveSparklineGraph
import com.wifix.app.ui.components.StatusPill
import com.wifix.app.ui.theme.WiFiXColors

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRunLatencyProbe: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WiFiX",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Real-Time Network Telemetry",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val statusColor = when {
                state.snapshot.isInternetValidated -> WiFiXColors.StatusGood
                state.snapshot.activeTransport.name != "NONE" -> WiFiXColors.StatusWarn
                else -> WiFiXColors.StatusError
            }
            StatusPill(
                label = if (state.snapshot.isInternetValidated) {
                    "${state.snapshot.activeTransport.displayLabel} Online"
                } else {
                    state.snapshot.activeTransport.displayLabel
                },
                dotColor = statusColor
            )
        }

        // Permission Readiness Card (shown only when optional diagnostic permissions are missing)
        if (!state.permissions.allCoreGranted) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = WiFiXColors.StatusWarn,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnostic Permissions Needed",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android requires Location permission to read Wi-Fi SSID/band details and Phone State permission to detect 4G/5G radio types.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WiFiXColors.AccentBlue
                    )
                ) {
                    Text(text = "Grant Permissions")
                }
            }
        }

        // Live Throughput & Sparkline Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Bandwidth",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Session: ${NetworkMath.formatByteSize(state.traffic.totalSessionRxBytes)} down",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThroughputColumn(
                    label = "DOWNLOAD",
                    mbps = state.traffic.downloadMbps,
                    mBps = state.traffic.downloadMBps,
                    accent = WiFiXColors.AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                ThroughputColumn(
                    label = "UPLOAD",
                    mbps = state.traffic.uploadMbps,
                    mBps = state.traffic.uploadMBps,
                    accent = WiFiXColors.AccentViolet,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            LiveSparklineGraph(
                downloadHistoryMbps = state.downloadHistoryMbps,
                uploadHistoryMbps = state.uploadHistoryMbps
            )
        }

        // Latency, Jitter & Stability Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = null,
                        tint = WiFiXColors.AccentBlueSoft,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Latency & Stability (${state.latency.targetHost})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (state.latency.isMeasuring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = WiFiXColors.AccentBlue
                    )
                } else {
                    IconButton(
                        onClick = onRunLatencyProbe,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Run TCP Latency Probe",
                            tint = WiFiXColors.TextPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricTile(
                    label = "TCP PING",
                    value = state.latency.averageRttMs?.let { String.format("%.1f ms", it) } ?: "--"
                )
                MetricTile(
                    label = "JITTER",
                    value = state.latency.jitterMs?.let { String.format("%.1f ms", it) } ?: "--"
                )
                MetricTile(
                    label = "PROBE LOSS",
                    value = String.format("%.0f%%", state.latency.lossPercent)
                )
                MetricTile(
                    label = "STABILITY",
                    value = "${state.stabilityScore}/100"
                )
            }
            if (state.latency.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Probe note: ${state.latency.errorMessage}",
                    color = WiFiXColors.StatusWarn,
                    fontSize = 12.sp
                )
            }
        }

        // Dual-Interface Breakdown (Wi-Fi + Cellular)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (state.snapshot.wifi.isConnected) WiFiXColors.AccentBlue else WiFiXColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Wi-Fi", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.snapshot.wifi.isConnected) {
                    DetailRow("SSID", state.snapshot.wifi.ssid ?: "Hidden / No Loc")
                    DetailRow("Band", state.snapshot.wifi.band.label)
                    DetailRow("Signal", state.snapshot.wifi.rssiDbm?.let { "$it dBm" } ?: "--")
                    DetailRow("Link", state.snapshot.wifi.linkSpeedMbps?.let { "$it Mbps" } ?: "--")
                    DetailRow("Standard", state.snapshot.wifi.wifiStandard)
                } else {
                    Text(
                        text = "Disconnected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            GlassCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CellTower,
                        contentDescription = null,
                        tint = if (state.snapshot.cellular.isConnected) WiFiXColors.AccentViolet else WiFiXColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Mobile Data", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.snapshot.cellular.isConnected) {
                    DetailRow("Radio", state.snapshot.cellular.generation)
                    DetailRow(
                        "Signal",
                        state.snapshot.cellular.signalStrengthDbm?.let { "$it dBm" } ?: "OS Managed"
                    )
                    DetailRow(
                        "Metered",
                        if (state.snapshot.cellular.isMetered) "Yes" else "No"
                    )
                    DetailRow(
                        "Cap Est",
                        "${state.snapshot.cellular.downstreamEstimateKbps / 1000} Mbps"
                    )
                } else {
                    Text(
                        text = "Inactive / Standby",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // System Status Strip
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = WiFiXColors.AccentBlueSoft,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Active Downloads: ${state.activeDownloadsCount}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Profile: ${state.optimisationProfile}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                StatusPill(
                    label = if (state.snapshot.dualPathAvailable) "Dual-Path Ready" else "Single-Path",
                    dotColor = if (state.snapshot.dualPathAvailable) WiFiXColors.StatusGood else WiFiXColors.AccentBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThroughputColumn(
    label: String,
    mbps: Double,
    mBps: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = String.format("%.2f Mbps", mbps),
            color = WiFiXColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = String.format("%.2f MB/s", mBps),
            color = WiFiXColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = WiFiXColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = WiFiXColors.TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = WiFiXColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
