package com.wifix.app.core.download

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.data.db.DownloadNetworkPolicy
import com.wifix.app.data.db.DownloadSegmentEntity
import com.wifix.app.data.db.DownloadStatus
import com.wifix.app.data.db.DownloadTaskEntity
import com.wifix.app.data.db.WiFiXDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadRepository private constructor(private val appContext: Context) {

    private val dao = WiFiXDatabase.getInstance(appContext).downloadDao()
    private val engine = ParallelDownloadEngine(appContext)
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runningJobs = ConcurrentHashMap<Long, Job>()

    private val _liveMetrics = MutableStateFlow>(emptyMap())
    val liveMetrics: StateFlow> = _liveMetrics.asStateFlow()

    private val _mobileBytesConsumed = MutableStateFlow(0L)
    val mobileBytesConsumed: StateFlow<Long> = _mobileBytesConsumed.asStateFlow()

    private val _customFolderUri = MutableStateFlow<String?>(null)
    val customFolderUri: StateFlow<String?> = _customFolderUri.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(2)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    val tasksFlow: Flow<List<DownloadTaskEntity>> = dao.observeAllTasks()
    val segmentsFlow: Flow<List<DownloadSegmentEntity>> = dao.observeAllSegments()

    val activeDownloadCount: Flow<Int> = tasksFlow.map { list ->
        list.count {
            it.status == DownloadStatus.DOWNLOADING.name ||
                it.status == DownloadStatus.PROBING.name ||
                it.status == DownloadStatus.FINALIZING.name
        }
    }

    init {
        // Recover interrupted downloads cleanly after an unexpected process kill/restart
        scope.launch {
            val interrupted = dao.getInProgressTasks()
            interrupted.forEach { task ->
                dao.updateTask(
                    task.copy(
                        status = DownloadStatus.PAUSED.name,
                        errorMessage = "Paused after app restart - tap Resume to continue."
                    )
                )
            }
        }
    }

    fun setCustomFolderUri(uriString: String?) {
        _customFolderUri.value = uriString
    }

    fun setMaxConcurrent(count: Int) {
        _maxConcurrent.value = count.coerceIn(1, 4)
        scheduleNextFromQueue()
    }

    suspend fun inspectUrl(url: String): ProbeResult {
        val activeNet = connectivityManager.activeNetwork
        return engine.probeUrl(url.trim(), activeNet)
    }

    suspend fun enqueueDownload(
        url: String,
        threadCount: Int,
        networkPolicy: DownloadNetworkPolicy,
        expectedSha256: String?,
        delayMinutes: Long = 0L,
        preProbed: ProbeResult? = null
    ): Long {
        val trimmedUrl = url.trim()
        val fileName = preProbed?.fileName ?: DownloadMath.extractFileName(trimmedUrl, null)
        val mime = preProbed?.mimeType ?: "application/octet-stream"
        val category = DownloadMath.classifyCategory(fileName, mime).name
        val nowMs = System.currentTimeMillis()
        val scheduledAt = if (delayMinutes > 0L) nowMs + (delayMinutes * 60_000L) else null
        val initialStatus = if (scheduledAt != null) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED

        val stagingDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        stagingDir.mkdirs()
        val partFile = File(stagingDir, "${nowMs}_${fileName}.part")

        val taskId = dao.insertTask(
            DownloadTaskEntity(
                url = preProbed?.finalUrl ?: trimmedUrl,
                fileName = fileName,
                mimeType = mime,
                category = category,
                status = initialStatus.name,
                totalBytes = preProbed?.totalBytes ?: -1L,
                downloadedBytes = 0L,
                supportsRange = preProbed?.supportsRange ?: false,
                threadCount = threadCount.coerceIn(1, 8),
                networkPolicy = networkPolicy.name,
                etag = preProbed?.etag,
                lastModified = preProbed?.lastModified,
                partFilePath = partFile.absolutePath,
                destinationUri = null,
                destinationDisplay = if (_customFolderUri.value != null) "Custom Folder" else "Downloads/WiFiX",
                expectedSha256 = expectedSha256?.trim()?.lowercase()?.ifBlank { null },
                computedSha256 = null,
                integrityVerified = null,
                scheduledAtMs = scheduledAt,
                errorMessage = null,
                createdAtMs = nowMs,
                completedAtMs = null
            )
        )

        if (scheduledAt != null) {
            scheduleWorkManagerTrigger(taskId, delayMinutes, networkPolicy)
        } else {
            scheduleNextFromQueue()
        }
        return taskId
    }

    fun triggerScheduledDownload(taskId: Long) {
        scope.launch {
            val task = dao.getTaskById(taskId) ?: return@launch
            if (task.status == DownloadStatus.SCHEDULED.name) {
                dao.updateTask(task.copy(status = DownloadStatus.QUEUED.name, scheduledAtMs = null))
                scheduleNextFromQueue()
            }
        }
    }

    fun pauseDownload(taskId: Long) {
        engine.cancelTaskCalls(taskId)
        runningJobs.remove(taskId)?.cancel()
        scope.launch {
            val task = dao.getTaskById(taskId) ?: return@launch
            if (task.status != DownloadStatus.COMPLETED.name) {
                dao.updateTask(task.copy(status = DownloadStatus.PAUSED.name, errorMessage = null))
            }
            clearLiveMetrics(taskId)
            scheduleNextFromQueue()
        }
    }

    fun resumeOrRetryDownload(taskId: Long) {
        scope.launch {
            val task = dao.getTaskById(taskId) ?: return@launch
            if (task.status == DownloadStatus.COMPLETED.name || runningJobs.containsKey(taskId)) return@launch
            dao.updateTask(
                task.copy(
                    status = DownloadStatus.QUEUED.name,
                    errorMessage = null
                )
            )
            scheduleNextFromQueue()
        }
    }

    fun deleteDownload(taskId: Long, deleteLocalPart: Boolean = true) {
        engine.cancelTaskCalls(taskId)
        runningJobs.remove(taskId)?.cancel()
        WorkManager.getInstance(appContext).cancelUniqueWork("wifix_sched_$taskId")
        scope.launch {
            val task = dao.getTaskById(taskId)
            if (deleteLocalPart && task != null) {
                runCatching { File(task.partFilePath).delete() }
            }
            dao.deleteTaskById(taskId)
            clearLiveMetrics(taskId)
            scheduleNextFromQueue()
        }
    }

    private fun scheduleNextFromQueue() {
        scope.launch {
            val availableSlots = _maxConcurrent.value - runningJobs.size
            if (availableSlots <= 0) return@launch

            val queued = dao.getQueuedTasks().take(availableSlots)
            queued.forEach { task ->
                if (!runningJobs.containsKey(task.id)) {
                    startTaskExecution(task.id)
                }
            }
        }
    }

    private fun startTaskExecution(taskId: Long) {
        ensureForegroundServiceRunning()

        val job = scope.launch {
            var task = dao.getTaskById(taskId) ?: return@launch
            val policy = runCatching { DownloadNetworkPolicy.valueOf(task.networkPolicy) }
                .getOrDefault(DownloadNetworkPolicy.AUTO)

            val selectedNetwork = selectNetworkForPolicy(policy)
            if (selectedNetwork == null) {
                dao.updateTask(
                    task.copy(
                        status = DownloadStatus.PAUSED.name,
                        errorMessage = if (policy == DownloadNetworkPolicy.WIFI_ONLY) {
                            "Waiting for Wi-Fi connection (Wi-Fi-only policy)."
                        } else {
                            "No validated internet connection available."
                        }
                    )
                )
                return@launch
            }

            val isUsingCellular = isCellularNetwork(selectedNetwork)

            try {
                // Step 1: Probe URL & validate ETag/Last-Modified on resume
                dao.updateTask(task.copy(status = DownloadStatus.PROBING.name, errorMessage = null))
                val probe = engine.probeUrl(task.url, selectedNetwork)

                val existingSegments = dao.getSegmentsForTask(taskId)
                val partFile = File(task.partFilePath)
                val canResumeExisting = existingSegments.isNotEmpty() &&
                    partFile.exists() &&
                    probe.supportsRange &&
                    DownloadMath.isResumeMetadataValid(
                        storedEtag = task.etag,
                        storedLastModified = task.lastModified,
                        storedTotalBytes = task.totalBytes,
                        probe = probe
                    )

                val activeSegments: List<DownloadSegmentEntity>
                val updatedCategory = DownloadMath.classifyCategory(probe.fileName, probe.mimeType).name

                if (canResumeExisting) {
                    activeSegments = existingSegments
                    task = task.copy(
                        status = DownloadStatus.DOWNLOADING.name,
                        supportsRange = true,
                        totalBytes = probe.totalBytes,
                        etag = probe.etag ?: task.etag,
                        lastModified = probe.lastModified ?: task.lastModified,
                        errorMessage = null
                    )
                    dao.updateTask(task)
                } else {
                    dao.deleteSegmentsForTask(taskId)
                    runCatching { partFile.delete() }
                    activeSegments = DownloadMath.buildSegments(
                        taskId = taskId,
                        totalBytes = probe.totalBytes,
                        requestedThreads = task.threadCount,
                        supportsRange = probe.supportsRange
                    )
                    dao.insertSegments(activeSegments)
                    task = task.copy(
                        url = probe.finalUrl,
                        fileName = probe.fileName,
                        mimeType = probe.mimeType,
                        category = updatedCategory,
                        status = DownloadStatus.DOWNLOADING.name,
                        totalBytes = probe.totalBytes,
                        downloadedBytes = 0L,
                        supportsRange = probe.supportsRange,
                        etag = probe.etag,
                        lastModified = probe.lastModified,
                        errorMessage = if (existingSegments.isNotEmpty()) {
                            "Remote file changed (ETag/Last-Modified mismatch); restarted cleanly."
                        } else null
                    )
                    dao.updateTask(task)
                }

                // Step 2: Execute segmented download
                var lastReportedBytes = task.downloadedBytes
                engine.downloadSegments(
                    taskId = taskId,
                    url = task.url,
                    partFile = partFile,
                    totalBytes = task.totalBytes,
                    supportsRange = task.supportsRange,
                    segments = activeSegments,
                    network = selectedNetwork,
                    onSegmentPersist = { segIndex, offset, completed ->
                        dao.updateSegmentProgress(taskId, segIndex, offset, completed)
                    },
                    onProgressTick = { downloadedBytes, speedBytesPerSec, activeThreads ->
                        val delta = (downloadedBytes - lastReportedBytes).coerceAtLeast(0L)
                        lastReportedBytes = downloadedBytes
                        if (isUsingCellular && delta > 0L) {
                            _mobileBytesConsumed.update { it + delta }
                        }

                        dao.updateTaskDownloadedBytes(taskId, downloadedBytes)

                        val remaining = if (task.totalBytes > 0L) {
                            (task.totalBytes - downloadedBytes).coerceAtLeast(0L)
                        } else -1L
                        val eta = if (remaining >= 0L && speedBytesPerSec > 0L) {
                            remaining / speedBytesPerSec
                        } else null

                        val instMbps = NetworkMath.bytesPerSecToMbps(speedBytesPerSec)
                        _liveMetrics.update { currentMap ->
                            val prev = currentMap[taskId] ?: LiveDownloadMetrics()
                            val nextHistory = (prev.speedHistoryMbps + instMbps).takeLast(28)
                            currentMap + (
                                taskId to LiveDownloadMetrics(
                                    speedBytesPerSec = speedBytesPerSec,
                                    speedHistoryMbps = nextHistory,
                                    etaSeconds = eta,
                                    activeThreads = activeThreads
                                )
                            )
                        }
                    }
                )

                // Step 3: Verify SHA-256 & Export to final storage location
                dao.updateTask(task.copy(status = DownloadStatus.FINALIZING.name, downloadedBytes = partFile.length()))
                val sha256 = DownloadMath.computeSha256(partFile)
                val verified = task.expectedSha256?.let { expected ->
                    expected.equals(sha256, ignoreCase = true)
                }

                val (destUri, destDisplay) = engine.exportCompletedFile(
                    partFile = partFile,
                    fileName = task.fileName,
                    mimeType = task.mimeType,
                    customTreeUriString = _customFolderUri.value
                )

                val latestTask = dao.getTaskById(taskId) ?: task
                dao.updateTask(
                    latestTask.copy(
                        status = DownloadStatus.COMPLETED.name,
                        totalBytes = if (latestTask.totalBytes > 0L) latestTask.totalBytes else latestTask.downloadedBytes,
                        downloadedBytes = if (latestTask.totalBytes > 0L) latestTask.totalBytes else latestTask.downloadedBytes,
                        destinationUri = destUri,
                        destinationDisplay = destDisplay,
                        computedSha256 = sha256,
                        integrityVerified = verified,
                        completedAtMs = System.currentTimeMillis(),
                        errorMessage = if (verified == false) {
                            "SHA-256 mismatch against expected checksum."
                        } else null
                    )
                )
            } catch (_: CancellationException) {
                val latest = dao.getTaskById(taskId)
                if (latest != null && latest.status != DownloadStatus.COMPLETED.name) {
                    dao.updateTask(latest.copy(status = DownloadStatus.PAUSED.name))
                }
            } catch (e: Exception) {
                val latest = dao.getTaskById(taskId)
                if (latest != null && latest.status != DownloadStatus.PAUSED.name) {
                    dao.updateTask(
                        latest.copy(
                            status = DownloadStatus.FAILED.name,
                            errorMessage = e.message ?: "Download failed"
                        )
                    )
                }
            } finally {
                runningJobs.remove(taskId)
                clearLiveMetrics(taskId)
                scheduleNextFromQueue()
            }
        }

        runningJobs[taskId] = job
    }

    private fun selectNetworkForPolicy(policy: DownloadNetworkPolicy): Network? {
        val active = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return null
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        return when (policy) {
            DownloadNetworkPolicy.WIFI_ONLY -> if (isWifi) active else null
            DownloadNetworkPolicy.AUTO, DownloadNetworkPolicy.MOBILE_ALLOWED -> active
        }
    }

    private fun isCellularNetwork(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun scheduleWorkManagerTrigger(
        taskId: Long,
        delayMinutes: Long,
        policy: DownloadNetworkPolicy
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (policy == DownloadNetworkPolicy.WIFI_ONLY) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<ScheduledDownloadWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(ScheduledDownloadWorker.KEY_TASK_ID to taskId))
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "wifix_sched_$taskId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun ensureForegroundServiceRunning() {
        runCatching {
            val intent = Intent(appContext, DownloadForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }
    }

    private fun clearLiveMetrics(taskId: Long) {
        _liveMetrics.update { it - taskId }
    }

    companion object {
        @Volatile
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            return instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
