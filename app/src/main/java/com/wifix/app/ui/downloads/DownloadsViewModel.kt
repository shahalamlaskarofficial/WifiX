package com.wifix.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifix.app.core.download.DownloadRepository
import com.wifix.app.core.download.LiveDownloadMetrics
import com.wifix.app.core.download.ProbeResult
import com.wifix.app.core.network.ActiveTransportType
import com.wifix.app.core.network.NetworkSnapshot
import com.wifix.app.data.db.DownloadCategory
import com.wifix.app.data.db.DownloadNetworkPolicy
import com.wifix.app.data.db.DownloadSegmentEntity
import com.wifix.app.data.db.DownloadTaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingMobileConfirmation(
    val url: String,
    val threadCount: Int,
    val networkPolicy: DownloadNetworkPolicy,
    val expectedSha256: String?,
    val delayMinutes: Long,
    val probe: ProbeResult?
)

data class DownloadsUiState(
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val segmentsByTask: Map<Long, List<DownloadSegmentEntity>> = emptyMap(),
    val liveMetrics: Map<Long, LiveDownloadMetrics> = emptyMap(),
    val selectedCategory: DownloadCategory = DownloadCategory.ALL,
    val maxConcurrent: Int = 2,
    val mobileBytesSession: Long = 0L,
    val customFolderLabel: String = "Downloads/WiFiX (Default)",
    val isProbingUrl: Boolean = false,
    val pendingMobileConfirmation: PendingMobileConfirmation? = null,
    val feedbackMessage: String? = null
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.tasksFlow,
                repository.segmentsFlow,
                repository.liveMetrics,
                repository.mobileBytesConsumed,
                repository.maxConcurrent
            ) { tasks, segments, metrics, mobileBytes, maxConc ->
                val grouped = segments.groupBy { it.taskId }
                _uiState.value.copy(
                    tasks = tasks,
                    segmentsByTask = grouped,
                    liveMetrics = metrics,
                    mobileBytesSession = mobileBytes,
                    maxConcurrent = maxConc
                )
            }.collect { updated ->
                _uiState.value = updated
            }
        }
    }

    fun selectCategory(category: DownloadCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setMaxConcurrent(count: Int) {
        repository.setMaxConcurrent(count)
    }

    fun setCustomDownloadFolder(uriString: String?, displayLabel: String) {
        repository.setCustomFolderUri(uriString)
        _uiState.update { it.copy(customFolderLabel = displayLabel) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun submitNewDownload(
        rawUrl: String,
        threadCount: Int,
        networkPolicy: DownloadNetworkPolicy,
        expectedSha256: String?,
        delayMinutes: Long,
        networkSnapshot: NetworkSnapshot
    ) {
        val url = rawUrl.trim()
        if (!url.startsWith("https://")) {
            _uiState.update { it.copy(feedbackMessage = "Enter a valid https:// download URL.") }
            return
        }

        val onMeteredCellular = networkSnapshot.activeTransport == ActiveTransportType.CELLULAR ||
            (networkSnapshot.isMetered && !networkSnapshot.wifi.isConnected)

        viewModelScope.launch {
            _uiState.update { it.copy(isProbingUrl = true, feedbackMessage = null) }
            val probe = runCatching { repository.inspectUrl(url) }.getOrNull()
            _uiState.update { it.copy(isProbingUrl = false) }

            val isLargeOrUnknown = probe == null || probe.totalBytes <= 0L || probe.totalBytes >= 20L * 1024L * 1024L

            if (onMeteredCellular && networkPolicy != DownloadNetworkPolicy.WIFI_ONLY && delayMinutes == 0L && isLargeOrUnknown) {
                _uiState.update {
                    it.copy(
                        pendingMobileConfirmation = PendingMobileConfirmation(
                            url = url,
                            threadCount = threadCount,
                            networkPolicy = networkPolicy,
                            expectedSha256 = expectedSha256,
                            delayMinutes = delayMinutes,
                            probe = probe
                        )
                    )
                }
                return@launch
            }

            repository.enqueueDownload(
                url = url,
                threadCount = threadCount,
                networkPolicy = networkPolicy,
                expectedSha256 = expectedSha256,
                delayMinutes = delayMinutes,
                preProbed = probe
            )
            _uiState.update {
                it.copy(
                    feedbackMessage = if (delayMinutes > 0L) {
                        "Scheduled download to start in $delayMinutes min."
                    } else {
                        "Added to parallel download queue."
                    }
                )
            }
        }
    }

    fun confirmMobileDownload() {
        val pending = _uiState.value.pendingMobileConfirmation ?: return
        _uiState.update { it.copy(pendingMobileConfirmation = null) }
        viewModelScope.launch {
            repository.enqueueDownload(
                url = pending.url,
                threadCount = pending.threadCount,
                networkPolicy = DownloadNetworkPolicy.MOBILE_ALLOWED,
                expectedSha256 = pending.expectedSha256,
                delayMinutes = pending.delayMinutes,
                preProbed = pending.probe
            )
        }
    }

    fun dismissMobileDownload() {
        _uiState.update { it.copy(pendingMobileConfirmation = null) }
    }

    fun pauseDownload(taskId: Long) = repository.pauseDownload(taskId)
    fun resumeOrRetry(taskId: Long) = repository.resumeOrRetryDownload(taskId)
    fun deleteDownload(taskId: Long) = repository.deleteDownload(taskId)
}
