package com.wifix.app.core.network

import kotlin.math.abs
import kotlin.math.roundToInt

object NetworkMath {

    /**
     * Converts Bytes per second (B/s) into Megabits per second (Mbps).
     * 1 Byte = 8 bits; 1 Mbps = 1,000,000 bits/sec (networking standard).
     */
    fun bytesPerSecToMbps(bytesPerSec: Long): Double {
        if (bytesPerSec <= 0L) return 0.0
        return (bytesPerSec * 8.0) / 1_000_000.0
    }

    /**
     * Converts Bytes per second (B/s) into Megabytes per second (MB/s).
     * 1 MB = 1,048,576 bytes (MiB display convention used by OS download managers).
     */
    fun bytesPerSecToMBps(bytesPerSec: Long): Double {
        if (bytesPerSec <= 0L) return 0.0
        return bytesPerSec / 1_048_576.0
    }

    fun classifyWifiBand(frequencyMhz: Int?): WifiBand {
        if (frequencyMhz == null) return WifiBand.UNKNOWN
        return when (frequencyMhz) {
            in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            in 4900..5899 -> WifiBand.BAND_5_GHZ
            in 5925..7125 -> WifiBand.BAND_6_GHZ
            else -> WifiBand.UNKNOWN
        }
    }

    /**
     * Mean absolute deviation between consecutive RTT samples (ms).
     */
    fun calculateJitterMs(rttsMs: List<Double>): Double {
        if (rttsMs.size < 2) return 0.0
        var sumDiff = 0.0
        for (i in 1 until rttsMs.size) {
            sumDiff += abs(rttsMs[i] - rttsMs[i - 1])
        }
        return sumDiff / (rttsMs.size - 1)
    }

    /**
     * Transparent, deterministic network stability score (0..100).
     * Derived solely from real loss %, jitter, RTT, and Wi-Fi RSSI (when on Wi-Fi).
     */
    fun calculateStabilityScore(
        isValidated: Boolean,
        lossPercent: Float,
        jitterMs: Double?,
        averageRttMs: Double?,
        wifiRssiDbm: Int?
    ): Int {
        if (!isValidated) return 0
        var score = 100.0

        // Penalize probe loss heavily
        score -= (lossPercent * 1.5)

        // Penalize jitter above 5ms
        if (jitterMs != null && jitterMs > 5.0) {
            score -= ((jitterMs - 5.0) * 1.2).coerceAtMost(30.0)
        }

        // Penalize high latency above 50ms
        if (averageRttMs != null && averageRttMs > 50.0) {
            score -= ((averageRttMs - 50.0) * 0.15).coerceAtMost(25.0)
        }

        // Penalize weak Wi-Fi signal below -67 dBm
        if (wifiRssiDbm != null && wifiRssiDbm < -67) {
            val weakness = (-67 - wifiRssiDbm)
            score -= (weakness * 1.1).coerceAtMost(25.0)
        }

        return score.roundToInt().coerceIn(0, 100)
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
