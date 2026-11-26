package com.wifix.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifix.app.core.download.DownloadRepository
import com.wifix.app.core.network.DualNetworkMonitor
import com.wifix.app.core.network.LatencyReport
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.core.network.NetworkSnapshot
import com.wifix.app.core.network.RealtimeTrafficMeter
import com.wifix.app.core.network.TcpLatencyProbe
import com.wifix.app.core.network.ThroughputSample
import com.wifix.app.core.permissions.PermissionHelper
import com.wifix.app.core.permissions.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: NetworkSnapshot = NetworkSnapshot(),
    val traffic: ThroughputSample = ThroughputSample(),
    val downloadHistoryMbps: List<Double> = List(30) { 0.0 },
    val uploadHistoryMbps: List<Double> = List(30) { 0.0 },
    val latency: LatencyReport = LatencyReport(),
    val stabilityScore: Int = 0,
    val activeDownloadsCount: Int = 0,
    val optimisationProfile: String = "Balanced Monitoring",
    val permissions: PermissionStatus = PermissionStatus(
        locationGranted = false,
        phoneStateGranted = false,
        notificationsGranted = false
    )
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val networkMonitor = DualNetworkMonitor(application)
    private val trafficMeter = RealtimeTrafficMeter(intervalMs = 1_000L)
    private val latencyProbe = TcpLatencyProbe()
    private val downloadRepository = DownloadRepository.getInstance(application)

    private val _uiState = MutableStateFlow(
        DashboardUiState(permissions = PermissionHelper.checkStatus(application))
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        networkMonitor.start()

        viewModelScope.launch {
            networkMonitor.snapshot.collect { snap ->
                _uiState.update { state ->
                    state.copy(
                        snapshot = snap,
                        stabilityScore = computeStability(snap, state.latency)
                    )
                }
            }
        }

        viewModelScope.launch {
            trafficMeter.observeTraffic().collect { sample ->
                _uiState.update { state ->
                    val nextDown = (state.downloadHistoryMbps + sample.downloadMbps).takeLast(30)
                    val nextUp = (state.uploadHistoryMbps + sample.uploadMbps).takeLast(30)
                    state.copy(
                        traffic = sample,
                        downloadHistoryMbps = nextDown,
                        uploadHistoryMbps = nextUp
                    )
                }
            }
        }

        viewModelScope.launch {
            downloadRepository.activeDownloadCount.collect { activeCount ->
                _uiState.update { it.copy(activeDownloadsCount = activeCount) }
            }
        }

        runLatencyProbe()
    }

    fun refreshPermissions() {
        val status = PermissionHelper.checkStatus(getApplication())
        _uiState.update { it.copy(permissions = status) }
        networkMonitor.refreshActiveSnapshot()
    }

    fun runLatencyProbe(host: String = "1.1.1.1", port: Int = 443) {
        val currentSnap = _uiState.value.snapshot
        if (_uiState.value.latency.isMeasuring) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(latency = state.latency.copy(isMeasuring = true, errorMessage = null))
            }

            val report = latencyProbe.measure(
                host = host,
                port = port,
                samples = 5,
                network = currentSnap.defaultNetworkHandle,
                transport = currentSnap.activeTransport
            )

            _uiState.update { state ->
                state.copy(
                    latency = report,
                    stabilityScore = computeStability(state.snapshot, report)
                )
            }
        }
    }

    private fun computeStability(snap: NetworkSnapshot, latency: LatencyReport): Int {
        val wifiRssi = if (snap.wifi.isConnected) snap.wifi.rssiDbm else null
        return NetworkMath.calculateStabilityScore(
            isValidated = snap.isInternetValidated,
            lossPercent = latency.lossPercent,
            jitterMs = latency.jitterMs,
            averageRttMs = latency.averageRttMs,
            wifiRssiDbm = wifiRssi
        )
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.stop()
    }
}
