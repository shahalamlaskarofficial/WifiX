package com.wifix.app.ui.speedtest

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifix.app.core.network.ActiveTransportType
import com.wifix.app.core.network.NetworkSnapshot
import com.wifix.app.core.speedtest.BuiltInSpeedServers
import com.wifix.app.core.speedtest.IspVerificationSummary
import com.wifix.app.core.speedtest.SpeedAnalytics
import com.wifix.app.core.speedtest.SpeedTestEngine
import com.wifix.app.core.speedtest.SpeedTestPhase
import com.wifix.app.core.speedtest.SpeedTestProgress
import com.wifix.app.core.speedtest.SpeedTestServer
import com.wifix.app.data.db.SpeedTestRecord
import com.wifix.app.data.db.WiFiXDatabase
import com.wifix.app.data.prefs.IspPlanConfig
import com.wifix.app.data.prefs.IspPlanStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeedTestUiState(
    val selectedServer: SpeedTestServer = BuiltInSpeedServers.defaults.first(),
    val useCustomServer: Boolean = false,
    val customUrl: String = "",
    val progress: SpeedTestProgress = SpeedTestProgress(),
    val isRunning: Boolean = false,
    val ispPlan: IspPlanConfig = IspPlanConfig(),
    val history: List<SpeedTestRecord> = emptyList(),
    val ispSummary: IspVerificationSummary = SpeedAnalytics.buildIspVerification(emptyList(), IspPlanConfig())
)

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = WiFiXDatabase.getInstance(application).speedTestDao()
    private val planStore = IspPlanStore(application)
    private val engine = SpeedTestEngine()

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var activeTestJob: Job? = null

    init {
        viewModelScope.launch {
            combine(dao.observeRecent(), planStore.planFlow) { records, plan ->
                Pair(records, plan)
            }.collect { (records, plan) ->
                val summary = SpeedAnalytics.buildIspVerification(records, plan)
                _uiState.update { state ->
                    state.copy(
                        ispPlan = plan,
                        history = records,
                        ispSummary = summary
                    )
                }
            }
        }
    }

    fun selectBuiltInServer(server: SpeedTestServer) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(selectedServer = server, useCustomServer = false) }
    }

    fun enableCustomServer() {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(useCustomServer = true) }
    }

    fun updateCustomUrl(url: String) {
        _uiState.update { it.copy(customUrl = url) }
    }

    fun saveIspPlan(ispName: String, downMbps: Double, upMbps: Double) {
        viewModelScope.launch {
            planStore.savePlan(
                IspPlanConfig(
                    ispName = ispName,
                    subscribedDownloadMbps = downMbps,
                    subscribedUploadMbps = upMbps
                )
            )
        }
    }

    fun startTest(snapshot: NetworkSnapshot) {
        if (_uiState.value.isRunning) return

        val server = resolveTargetServer()
        if (server == null) {
            _uiState.update {
                it.copy(
                    progress = SpeedTestProgress(
                        phase = SpeedTestPhase.FAILED,
                        errorMessage = "Enter a valid https:// speed test file URL."
                    )
                )
            }
            return
        }

        if (!snapshot.isInternetValidated && snapshot.activeTransport == ActiveTransportType.NONE) {
            _uiState.update {
                it.copy(
                    progress = SpeedTestProgress(
                        phase = SpeedTestPhase.FAILED,
                        errorMessage = "No active internet connection detected."
                    )
                )
            }
            return
        }

        activeTestJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    progress = SpeedTestProgress(phase = SpeedTestPhase.DNS, progressFraction = 0.04f)
                )
            }

            val finalState = engine.runFullTest(
                server = server,
                network = snapshot.defaultNetworkHandle,
                transport = snapshot.activeTransport,
                wifiRssiDbm = if (snapshot.wifi.isConnected) snapshot.wifi.rssiDbm else null,
                onProgress = { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }
            )

            if (finalState.phase == SpeedTestPhase.COMPLETED && finalState.downloadMbps != null) {
                val nowMs = System.currentTimeMillis()
                val label = when (snapshot.activeTransport) {
                    ActiveTransportType.WIFI -> snapshot.wifi.ssid ?: snapshot.wifi.band.label
                    ActiveTransportType.CELLULAR -> snapshot.cellular.generation
                    else -> snapshot.activeTransport.displayLabel
                }
                dao.insert(
                    SpeedTestRecord(
                        timestampMs = nowMs,
                        serverName = server.name,
                        transportType = snapshot.activeTransport.displayLabel,
                        networkLabel = label,
                        downloadMbps = finalState.downloadMbps,
                        uploadMbps = finalState.uploadMbps,
                        idlePingMs = finalState.idlePingMs ?: 0.0,
                        jitterMs = finalState.jitterMs ?: 0.0,
                        loadedDownloadPingMs = finalState.loadedDownloadPingMs,
                        loadedUploadPingMs = finalState.loadedUploadPingMs,
                        bufferbloatGrade = finalState.bufferbloatGrade,
                        packetLossPercent = finalState.packetLossPercent,
                        dnsLookupMs = finalState.dnsLookupMs,
                        consistencyScore = finalState.consistencyScore,
                        stabilityScore = finalState.stabilityScore,
                        isPeakHour = SpeedAnalytics.isPeakHour(nowMs)
                    )
                )
            }

            _uiState.update { it.copy(isRunning = false) }
        }
    }

    fun stopTest() {
        engine.cancelActiveCalls()
        activeTestJob?.cancel()
        activeTestJob = null
        _uiState.update {
            it.copy(
                isRunning = false,
                progress = SpeedTestProgress(
                    phase = SpeedTestPhase.IDLE,
                    note = "Speed test stopped by user."
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clearAll() }
    }

    fun buildComplaintReportText(): String {
        val state = _uiState.value
        return SpeedAnalytics.formatIspComplaintReport(state.history, state.ispPlan, state.ispSummary)
    }

    fun buildCsvText(): String {
        return SpeedAnalytics.formatCsvExport(_uiState.value.history)
    }

    private fun resolveTargetServer(): SpeedTestServer? {
        val state = _uiState.value
        if (!state.useCustomServer) return state.selectedServer
        val raw = state.customUrl.trim()
        if (!raw.startsWith("https://")) return null
        val host = Uri.parse(raw).host ?: return null
        return SpeedTestServer(
            id = "custom",
            name = "Custom ($host)",
            region = "User Endpoint (Download Only)",
            host = host,
            downloadUrl = raw,
            uploadUrl = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancelActiveCalls()
    }
}
