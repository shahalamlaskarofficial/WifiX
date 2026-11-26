package com.wifix.app.ui.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifix.app.core.download.DownloadMath
import com.wifix.app.core.download.LiveDownloadMetrics
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.core.network.NetworkSnapshot
import com.wifix.app.data.db.DownloadCategory
import com.wifix.app.data.db.DownloadNetworkPolicy
import com.wifix.app.data.db.DownloadSegmentEntity
import com.wifix.app.data.db.DownloadStatus
import com.wifix.app.data.db.DownloadTaskEntity
import com.wifix.app.ui.components.GlassCard
import com.wifix.app.ui.components.LiveSparklineGraph
import com.wifix.app.ui.components.StatusPill
import com.wifix.app.ui.theme.WiFiXColors
import java.util.Locale

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    networkSnapshot: NetworkSnapshot,
    onSelectCategory: (DownloadCategory) -> Unit,
    onSetMaxConcurrent: (Int) -> Unit,
    onSetCustomFolder: (String?, String) -> Unit,
    onAddDownload: (String, Int, DownloadNetworkPolicy, String?, Long) -> Unit,
    onConfirmMobileDownload: () -> Unit,
    onDismissMobileDownload: () -> Unit,
    onPause: (Long) -> Unit,
    onResumeOrRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAddDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = uri.lastPathSegment?.substringAfterLast(':') ?: "Custom Folder"
            onSetCustomFolder(uri.toString(), label)
        }
    }

    val filteredTasks = remember(state.tasks, state.selectedCategory) {
        if (state.selectedCategory == DownloadCategory.ALL) {
            state.tasks
        } else {
            state.tasks.filter { it.category == state.selectedCategory.name }
        }
    }

    val activeTask = state.tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING.name }
    val activeMetrics = activeTask?.let { state.liveMetrics[it.id] }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Control & Storage Bar
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Parallel Download Engine", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Save to: ${state.customFolderLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Choose Download Location",
                        tint = WiFiXColors.AccentBlueSoft
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WiFiXColors.AccentBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isProbingUrl) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Download")
                }

                OutlinedButton(
                    onClick = {
                        val next = if (state.maxConcurrent >= 4) 1 else state.maxConcurrent + 1
                        onSetMaxConcurrent(next)
                    }
                ) {
                    Text("Queue: ${state.maxConcurrent}x", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mobile Data Used: ${NetworkMath.formatByteSize(state.mobileBytesSession)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Active Link: ${networkSnapshot.activeTransport.displayLabel}",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            state.feedbackMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = msg, color = WiFiXColors.AccentBlueSoft, fontSize = 12.sp)
            }
        }

        // Live Speed Graph when an active download is running
        if (activeTask != null && activeMetrics != null && activeMetrics.speedHistoryMbps.size >= 2) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Transfer Graph",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.1f Mbps | %.2f MB/s",
                            NetworkMath.bytesPerSecToMbps(activeMetrics.speedBytesPerSec),
                            NetworkMath.bytesPerSecToMBps(activeMetrics.speedBytesPerSec)
                        ),
                        color = WiFiXColors.AccentBlueSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LiveSparklineGraph(
                    downloadHistoryMbps = activeMetrics.speedHistoryMbps,
                    uploadHistoryMbps = emptyList()
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DownloadCategory.entries.forEach { category ->
                val selected = state.selectedCategory == category
                val shape = RoundedCornerShape(50)
                Text(
                    text = category.label,
                    color = if (selected) Color.White else WiFiXColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            if (selected) WiFiXColors.AccentBlue.copy(alpha = 0.30f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .border(
                            1.dp,
                            if (selected) WiFiXColors.AccentBlue else Color.White.copy(alpha = 0.14f),
                            shape
                        )
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Task List
        if (filteredTasks.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "No Downloads in This Category", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap New Download to start a multi-threaded HTTP Range transfer with pause/resume, ETag resume verification, and SHA-256 integrity checking.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            filteredTasks.forEach { task ->
                DownloadTaskCard(
                    task = task,
                    segments = state.segmentsByTask[task.id].orEmpty(),
                    metrics = state.liveMetrics[task.id],
                    onPause = { onPause(task.id) },
                    onResumeOrRetry = { onResumeOrRetry(task.id) },
                    onDelete = { onDelete(task.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(104.dp))
    }

    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onStart = { url, threads, policy, sha, delayMin ->
                showAddDialog = false
                onAddDownload(url, threads, policy, sha, delayMin)
            }
        )
    }

    state.pendingMobileConfirmation?.let { pending ->
        val sizeText = pending.probe?.totalBytes?.takeIf { it > 0L }
            ?.let { NetworkMath.formatByteSize(it) } ?: "Unknown size"
        AlertDialog(
            onDismissRequest = onDismissMobileDownload,
            containerColor = WiFiXColors.SurfaceDeep,
            title = { Text("Mobile Data Confirmation") },
            text = {
                Text(
                    text = "You are currently on metered Mobile Data. Download ${pending.probe?.fileName ?: "this file"} ($sizeText) using cellular data?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmMobileDownload) { Text("Allow Mobile Data") }
            },
            dismissButton = {
                TextButton(onClick = onDismissMobileDownload) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    segments: List<DownloadSegmentEntity>,
    metrics: LiveDownloadMetrics?,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val status = runCatching { DownloadStatus.valueOf(task.status) }.getOrDefault(DownloadStatus.QUEUED)
    val policy = runCatching { DownloadNetworkPolicy.valueOf(task.networkPolicy) }
        .getOrDefault(DownloadNetworkPolicy.AUTO)

    val statusColor = when (status) {
        DownloadStatus.COMPLETED -> WiFiXColors.StatusGood
        DownloadStatus.DOWNLOADING, DownloadStatus.PROBING, DownloadStatus.FINALIZING -> WiFiXColors.AccentBlue
        DownloadStatus.PAUSED, DownloadStatus.QUEUED, DownloadStatus.SCHEDULED -> WiFiXColors.StatusWarn
        DownloadStatus.FAILED -> WiFiXColors.StatusError
    }

    val progressFraction = if (task.totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else if (status == DownloadStatus.COMPLETED) {
        1f
    } else {
        0f
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (task.supportsRange) "${task.threadCount}x Range" else "Single-Stream (No Range)")
                        append(" | ")
                        append(policy.label)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(label = status.label, dotColor = statusColor)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Per-segment segmented progress bar when multiple segments exist
        if (segments.size > 1 && task.totalBytes > 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                segments.forEach { seg ->
                    val segTotal = (seg.endByte - seg.startByte + 1L).coerceAtLeast(1L)
                    val segDone = (seg.currentOffset - seg.startByte).coerceIn(0L, segTotal)
                    val segFrac = if (seg.completed) 1f else (segDone.toFloat() / segTotal.toFloat())
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segFrac)
                                .fillMaxSize()
                                .background(if (seg.completed) WiFiXColors.StatusGood else WiFiXColors.AccentBlue)
                        )
                    }
                }
            }
        } else {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = statusColor,
                trackColor = Color.White.copy(alpha = 0.10f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transfer stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sizeLabel = if (task.totalBytes > 0L) {
                "${NetworkMath.formatByteSize(task.downloadedBytes)} / ${NetworkMath.formatByteSize(task.totalBytes)}"
            } else {
                NetworkMath.formatByteSize(task.downloadedBytes)
            }
            Text(text = sizeLabel, color = WiFiXColors.TextSecondary, fontSize = 12.sp)

            if (status == DownloadStatus.DOWNLOADING && metrics != null) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f Mbps | ETA %s",
                        NetworkMath.bytesPerSecToMbps(metrics.speedBytesPerSec),
                        DownloadMath.formatEta(metrics.etaSeconds)
                    ),
                    color = WiFiXColors.AccentBlueSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (status == DownloadStatus.COMPLETED) {
                Text(
                    text = task.destinationDisplay,
                    color = WiFiXColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // SHA-256 verification status for completed files
        if (task.computedSha256 != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = when (task.integrityVerified) {
                        true -> WiFiXColors.StatusGood
                        false -> WiFiXColors.StatusError
                        null -> WiFiXColors.AccentBlueSoft
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SHA-256: ${task.computedSha256.take(18)}...${
                        when (task.integrityVerified) {
                            true -> " (Matched)"
                            false -> " (MISMATCH)"
                            null -> ""
                        }
                    }",
                    color = WiFiXColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        task.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = err, color = WiFiXColors.StatusWarn, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROBING) {
                TextButton(onClick = onPause) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
            } else if (status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED || status == DownloadStatus.QUEUED) {
                TextButton(onClick = onResumeOrRetry) {
                    Icon(
                        imageVector = if (status == DownloadStatus.FAILED) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (status == DownloadStatus.FAILED) "Retry" else "Resume")
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove Download",
                    tint = WiFiXColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onStart: (String, Int, DownloadNetworkPolicy, String?, Long) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var threads by remember { mutableIntStateOf(4) }
    var policy by remember { mutableStateOf(DownloadNetworkPolicy.AUTO) }
    var delayMinutes by remember { mutableLongStateOf(0L) }
    var expectedSha by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WiFiXColors.SurfaceDeep,
        title = { Text("New Parallel Download") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HTTPS File URL") },
                    placeholder = { Text("https://example.com/archive.zip") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { url = "https://fsn1-speed.hetzner.com/100MB.bin" }
                ) {
                    Text("Use 100 MB Range-Test File (Hetzner)")
                }

                Text("Parallel Segments: $threads threads", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 4, 8).forEach { count ->
                        SelectableMiniChip(
                            label = "${count}x",
                            selected = threads == count,
                            onClick = { threads = count }
                        )
                    }
                }

                Text("Connection Policy", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadNetworkPolicy.entries.forEach { p ->
                        SelectableMiniChip(
                            label = p.label,
                            selected = policy == p,
                            onClick = { policy = p }
                        )
                    }
                }

                Text("Schedule Start", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Now", 15L to "+15m", 60L to "+1h").forEach { (mins, lbl) ->
                        SelectableMiniChip(
                            label = lbl,
                            selected = delayMinutes == mins,
                            onClick = { delayMinutes = mins }
                        )
                    }
                }

                OutlinedTextField(
                    value = expectedSha,
                    onValueChange = { expectedSha = it },
                    label = { Text("Expected SHA-256 (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(url, threads, policy, expectedSha.ifBlank { null }, delayMinutes)
                }
            ) {
                Text(if (delayMinutes > 0L) "Schedule" else "Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SelectableMiniChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        color = if (selected) Color.White else WiFiXColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) WiFiXColors.AccentBlue.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) WiFiXColors.AccentBlue else Color.White.copy(alpha = 0.14f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
