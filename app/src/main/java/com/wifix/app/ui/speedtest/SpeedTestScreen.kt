package com.wifix.app.ui.speedtest

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.core.network.NetworkSnapshot
import com.wifix.app.core.speedtest.BuiltInSpeedServers
import com.wifix.app.core.speedtest.SpeedAnalytics
import com.wifix.app.core.speedtest.SpeedTestPhase
import com.wifix.app.core.speedtest.SpeedTestServer
import com.wifix.app.data.db.SpeedTestRecord
import com.wifix.app.ui.components.GlassCard
import com.wifix.app.ui.components.LiveSparklineGraph
import com.wifix.app.ui.components.SpeedGaugeMeter
import com.wifix.app.ui.components.StatusPill
import com.wifix.app.ui.theme.WiFiXColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeedTestScreen(
    state: SpeedTestUiState,
    networkSnapshot: NetworkSnapshot,
    onSelectServer: (SpeedTestServer) -> Unit,
    onSelectCustomServer: () -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onSaveIspPlan: (String, Double, Double) -> Unit,
    onClearHistory: () -> Unit,
    onBuildReportText: () -> String,
    onBuildCsvText: () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showPlanDialog by remember { mutableStateOf(false) }

    fun sharePlainText(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Server Selector Strip
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Test Server", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuiltInSpeedServers.defaults.forEach { server ->
                    val selected = !state.useCustomServer && state.selectedServer.id == server.id
                    ServerChip(
                        label = server.name,
                        selected = selected,
                        onClick = { onSelectServer(server) }
                    )
                }
                ServerChip(
                    label = "Custom HTTPS",
                    selected = state.useCustomServer,
                    onClick = onSelectCustomServer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.useCustomServer) {
                    "Provide a direct HTTPS file URL (download-only benchmark)."
                } else {
                    "${state.selectedServer.region} - ${state.selectedServer.host}"
                },
                style = MaterialTheme.typography.labelMedium
            )
            if (state.useCustomServer) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.customUrl,
                    onValueChange = onUpdateCustomUrl,
                    singleLine = true,
                    placeholder = { Text("https://example.com/100MB.bin") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Main Gauge & Live Curve Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SpeedGaugeMeter(
                    speedMbps = state.progress.currentSpeedMbps,
                    phaseLabel = state.progress.phase.label,
                    progressFraction = state.progress.progressFraction
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.isRunning) {
                    LinearProgressIndicator(
                        progress = { state.progress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50)),
                        color = WiFiXColors.AccentBlue,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = if (state.isRunning) onStopTest else onStartTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) WiFiXColors.StatusError else WiFiXColors.AccentBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isRunning) {
                            "Stop Benchmark"
                        } else {
                            "Start Speed Test (${networkSnapshot.activeTransport.displayLabel})"
                        }
                    )
                }

                if (state.progress.liveCurveMbps.size >= 2) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LiveSparklineGraph(
                        downloadHistoryMbps = if (state.progress.phase == SpeedTestPhase.UPLOAD) emptyList() else state.progress.liveCurveMbps,
                        uploadHistoryMbps = if (state.progress.phase == SpeedTestPhase.UPLOAD) state.progress.liveCurveMbps else emptyList()
                    )
                }

                state.progress.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = WiFiXColors.StatusError, fontSize = 12.sp)
                }
                state.progress.note?.let { note ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = note, color = WiFiXColors.StatusWarn, fontSize = 12.sp)
                }
            }
        }

        // Complete Metrics Grid (DL / UL / Ping / Jitter / DNS / Loss / Bufferbloat / Consistency)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Laboratory Telemetry", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LabMetric(
                    label = "DOWNLOAD",
                    value = state.progress.downloadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "--",
                    sub = state.progress.downloadMbps?.let { String.format(Locale.US, "%.2f MB/s", SpeedAnalytics.mbpsToMBps(it)) } ?: "",
                    modifier = Modifier.weight(1f)
                )
                LabMetric(
                    label = "UPLOAD",
                    value = state.progress.uploadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "--",
                    sub = state.progress.uploadMbps?.let { String.format(Locale.US, "%.2f MB/s", SpeedAnalytics.mbpsToMBps(it)) } ?: "",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LabMetric(
                    label = "IDLE PING",
                    value = state.progress.idlePingMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--",
                    sub = "Jitter: ${state.progress.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--"}",
                    modifier = Modifier.weight(1f)
                )
                LabMetric(
                    label = "BUFFERBLOAT",
                    value = "Grade ${state.progress.bufferbloatGrade}",
                    sub = state.progress.loadedDownloadPingMs?.let { String.format(Locale.US, "Loaded %.0f ms", it) } ?: "Under load",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LabMetric(
                    label = "DNS LOOKUP",
                    value = state.progress.dnsLookupMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--",
                    sub = "Loss: ${String.format(Locale.US, "%.0f%%", state.progress.packetLossPercent)}",
                    modifier = Modifier.weight(1f)
                )
                LabMetric(
                    label = "CONSISTENCY",
                    value = if (state.progress.consistencyScore > 0) "${state.progress.consistencyScore}/100" else "--",
                    sub = "Stability: ${if (state.progress.stabilityScore > 0) "${state.progress.stabilityScore}/100" else "--"}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ISP Speed Verification & Peak-Hour Analysis Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "ISP Speed Verification", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${state.ispPlan.ispName} - Plan: ${String.format(Locale.US, "%.0f", state.ispPlan.subscribedDownloadMbps)} Mbps DL / ${String.format(Locale.US, "%.0f", state.ispPlan.subscribedUploadMbps)} Mbps UL",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showPlanDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Configure ISP Plan",
                        tint = WiFiXColors.AccentBlueSoft
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val verdictColor = when {
                state.ispSummary.repeatedDropsDetected -> WiFiXColors.StatusError
                state.ispSummary.totalTests < 3 -> WiFiXColors.StatusWarn
                state.ispSummary.deliveredPercent >= 85.0 -> WiFiXColors.StatusGood
                else -> WiFiXColors.StatusWarn
            }
            StatusPill(label = state.ispSummary.verdictTitle, dotColor = verdictColor)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = state.ispSummary.verdictDetail, style = MaterialTheme.typography.bodyMedium)

            if (state.ispSummary.totalTests > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    LabMetric(
                        label = "AVG MEASURED",
                        value = String.format(Locale.US, "%.1f Mbps", state.ispSummary.averageDownloadMbps),
                        sub = String.format(Locale.US, "Diff: -%.1f Mbps", state.ispSummary.downloadDeficitMbps),
                        modifier = Modifier.weight(1f)
                    )
                    LabMetric(
                        label = "PEAK vs OFF-PEAK",
                        value = state.ispSummary.peakAverageDownloadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        sub = "Off-Peak: ${state.ispSummary.offPeakAverageDownloadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--"}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    LabMetric(
                        label = "WI-FI AVG",
                        value = state.ispSummary.wifiAverageDownloadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--",
                        sub = "",
                        modifier = Modifier.weight(1f)
                    )
                    LabMetric(
                        label = "MOBILE AVG",
                        value = state.ispSummary.cellularAverageDownloadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--",
                        sub = "",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { sharePlainText("WiFiX ISP Speed Report", onBuildReportText()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ISP Report", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { sharePlainText("WiFiX Speed History CSV", onBuildCsvText()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV", maxLines = 1)
                    }
                }
            }
        }

        // Persistent Test History Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (${state.history.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.history.isNotEmpty()) {
                    IconButton(onClick = onClearHistory) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = WiFiXColors.TextMuted
                        )
                    }
                }
            }
            if (state.history.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed speed tests are stored locally in Room for long-term ISP tracking.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                state.history.take(15).forEach { record ->
                    HistoryRecordRow(record)
                }
            }
        }

        Spacer(modifier = Modifier.height(104.dp))
    }

    if (showPlanDialog) {
        IspPlanConfigDialog(
            initialName = state.ispPlan.ispName,
            initialDown = state.ispPlan.subscribedDownloadMbps,
            initialUp = state.ispPlan.subscribedUploadMbps,
            onDismiss = { showPlanDialog = false },
            onConfirm = { name, down, up ->
                onSaveIspPlan(name, down, up)
                showPlanDialog = false
            }
        )
    }
}

@Composable
private fun ServerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) Color.White else WiFiXColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) WiFiXColors.AccentBlue.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) WiFiXColors.AccentBlue else Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun LabMetric(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(end = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = WiFiXColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                color = WiFiXColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryRecordRow(record: SpeedTestRecord) {
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
    val ulText = record.uploadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${record.transportType} - ${record.networkLabel}",
                color = WiFiXColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatter.format(Date(record.timestampMs))} | ${record.serverName} | Bloat ${record.bufferbloatGrade}",
                color = WiFiXColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "DL ${String.format(Locale.US, "%.1f", record.downloadMbps)} Mbps",
                color = WiFiXColors.AccentBlueSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "UL $ulText | ${String.format(Locale.US, "%.0f", record.idlePingMs)} ms",
                color = WiFiXColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun IspPlanConfigDialog(
    initialName: String,
    initialDown: Double,
    initialUp: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit
) {
    var ispName by remember { mutableStateOf(initialName) }
    var downText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initialDown)) }
    var upText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initialUp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WiFiXColors.SurfaceDeep,
        title = { Text("Configure Subscribed ISP Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ispName,
                    onValueChange = { ispName = it },
                    label = { Text("ISP Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = downText,
                    onValueChange = { downText = it },
                    label = { Text("Subscribed Download (Mbps)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = upText,
                    onValueChange = { upText = it },
                    label = { Text("Subscribed Upload (Mbps)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dl = downText.toDoubleOrNull() ?: initialDown
                    val ul = upText.toDoubleOrNull() ?: initialUp
                    onConfirm(ispName, dl, ul)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
