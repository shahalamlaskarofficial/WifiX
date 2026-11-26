package com.wifix.app.core.speedtest

enum class SpeedTestPhase(val label: String) {
    IDLE("Ready"),
    DNS("DNS Benchmark"),
    LATENCY("Idle Ping & Jitter"),
    DOWNLOAD("Multi-Stream Download"),
    UPLOAD("Multi-Stream Upload"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

data class SpeedTestServer(
    val id: String,
    val name: String,
    val region: String,
    val host: String,
    val downloadUrl: String,
    val uploadUrl: String?
)

object BuiltInSpeedServers {
    val defaults = listOf(
        SpeedTestServer(
            id = "cloudflare",
            name = "Cloudflare Edge",
            region = "Global Anycast (DL + UL)",
            host = "speed.cloudflare.com",
            downloadUrl = "https://speed.cloudflare.com/__down?bytes=25000000",
            uploadUrl = "https://speed.cloudflare.com/__up"
        ),
        SpeedTestServer(
            id = "hetzner_fsn",
            name = "Hetzner EU (FSN1)",
            region = "Germany (Download Only)",
            host = "fsn1-speed.hetzner.com",
            downloadUrl = "https://fsn1-speed.hetzner.com/100MB.bin",
            uploadUrl = null
        ),
        SpeedTestServer(
            id = "hetzner_hel",
            name = "Hetzner EU (HEL1)",
            region = "Finland (Download Only)",
            host = "hel1-speed.hetzner.com",
            downloadUrl = "https://hel1-speed.hetzner.com/100MB.bin",
            uploadUrl = null
        )
    )
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val progressFraction: Float = 0f,
    val currentSpeedMbps: Double = 0.0,
    val liveCurveMbps: List<Double> = emptyList(),
    val dnsLookupMs: Double? = null,
    val idlePingMs: Double? = null,
    val jitterMs: Double? = null,
    val packetLossPercent: Float = 0f,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val loadedDownloadPingMs: Double? = null,
    val loadedUploadPingMs: Double? = null,
    val bufferbloatGrade: String = "--",
    val consistencyScore: Int = 0,
    val stabilityScore: Int = 0,
    val note: String? = null,
    val errorMessage: String? = null
)

data class IspVerificationSummary(
    val totalTests: Int,
    val averageDownloadMbps: Double,
    val averageUploadMbps: Double?,
    val downloadDeficitMbps: Double,
    val deliveredPercent: Double,
    val peakAverageDownloadMbps: Double?,
    val offPeakAverageDownloadMbps: Double?,
    val wifiAverageDownloadMbps: Double?,
    val cellularAverageDownloadMbps: Double?,
    val repeatedDropsDetected: Boolean,
    val verdictTitle: String,
    val verdictDetail: String
)
