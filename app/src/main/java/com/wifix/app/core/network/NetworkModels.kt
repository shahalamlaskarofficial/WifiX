package com.wifix.app.core.network

import android.net.Network

enum class ActiveTransportType(val displayLabel: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Mobile Data"),
    ETHERNET("Ethernet"),
    VPN("VPN Tunnel"),
    NONE("Offline")
}

enum class WifiBand(val label: String) {
    BAND_2_4_GHZ("2.4 GHz"),
    BAND_5_GHZ("5 GHz"),
    BAND_6_GHZ("6 GHz"),
    UNKNOWN("Unknown")
}

data class WifiLinkInfo(
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val ssid: String? = null,
    val rssiDbm: Int? = null,
    val frequencyMhz: Int? = null,
    val band: WifiBand = WifiBand.UNKNOWN,
    val linkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val wifiStandard: String = "Unknown",
    val downstreamEstimateKbps: Int = 0,
    val upstreamEstimateKbps: Int = 0,
    val networkHandle: Network? = null
)

data class CellularLinkInfo(
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val generation: String = "Unknown",
    val signalStrengthDbm: Int? = null,
    val isMetered: Boolean = true,
    val downstreamEstimateKbps: Int = 0,
    val upstreamEstimateKbps: Int = 0,
    val networkHandle: Network? = null
)

data class ThroughputSample(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val totalSessionRxBytes: Long = 0L,
    val totalSessionTxBytes: Long = 0L,
    val timestampMs: Long = 0L
) {
    val downloadMbps: Double get() = NetworkMath.bytesPerSecToMbps(downloadBytesPerSec)
    val uploadMbps: Double get() = NetworkMath.bytesPerSecToMbps(uploadBytesPerSec)
    val downloadMBps: Double get() = NetworkMath.bytesPerSecToMBps(downloadBytesPerSec)
    val uploadMBps: Double get() = NetworkMath.bytesPerSecToMBps(uploadBytesPerSec)
}

data class LatencyReport(
    val targetHost: String = "1.1.1.1",
    val targetPort: Int = 443,
    val averageRttMs: Double? = null,
    val minRttMs: Double? = null,
    val maxRttMs: Double? = null,
    val jitterMs: Double? = null,
    val lossPercent: Float = 0f,
    val sampleCount: Int = 0,
    val measuredOver: ActiveTransportType = ActiveTransportType.NONE,
    val isMeasuring: Boolean = false,
    val errorMessage: String? = null
)

data class NetworkSnapshot(
    val activeTransport: ActiveTransportType = ActiveTransportType.NONE,
    val isInternetValidated: Boolean = false,
    val isMetered: Boolean = false,
    val defaultNetworkHandle: Network? = null,
    val wifi: WifiLinkInfo = WifiLinkInfo(),
    val cellular: CellularLinkInfo = CellularLinkInfo()
) {
    val dualPathAvailable: Boolean
        get() = wifi.isConnected && cellular.isConnected
}
