package com.wifix.app

import com.wifix.app.core.network.NetworkMath
import com.wifix.app.core.network.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMathTest {

    @Test
    fun bytesPerSecConversions_distinguishMbpsFromMBps() {
        // 1,000,000 bytes/sec = 8.0 Mbps, ~0.9537 MB/s (MiB/s)
        val mbps = NetworkMath.bytesPerSecToMbps(1_000_000L)
        val mBps = NetworkMath.bytesPerSecToMBps(1_000_000L)

        assertEquals(8.0, mbps, 0.0001)
        assertEquals(0.95367, mBps, 0.001)
    }

    @Test
    fun wifiBandClassification_covers24_5_and_6GHz() {
        assertEquals(WifiBand.BAND_2_4_GHZ, NetworkMath.classifyWifiBand(2437))
        assertEquals(WifiBand.BAND_5_GHZ, NetworkMath.classifyWifiBand(5180))
        assertEquals(WifiBand.BAND_6_GHZ, NetworkMath.classifyWifiBand(6115))
        assertEquals(WifiBand.UNKNOWN, NetworkMath.classifyWifiBand(null))
    }

    @Test
    fun jitterCalculation_computesMeanAdjacentDelta() {
        val samples = listOf(20.0, 25.0, 15.0, 25.0)
        // Deltas: |25-20|=5, |15-25|=10, |25-15|=10 -> avg = 25/3 = 8.3333
        assertEquals(8.3333, NetworkMath.calculateJitterMs(samples), 0.001)
    }

    @Test
    fun stabilityScore_reflectsRealDegradation() {
        val healthy = NetworkMath.calculateStabilityScore(
            isValidated = true,
            lossPercent = 0f,
            jitterMs = 2.0,
            averageRttMs = 18.0,
            wifiRssiDbm = -55
        )
        val degraded = NetworkMath.calculateStabilityScore(
            isValidated = true,
            lossPercent = 20f,
            jitterMs = 25.0,
            averageRttMs = 140.0,
            wifiRssiDbm = -82
        )
        val offline = NetworkMath.calculateStabilityScore(
            isValidated = false,
            lossPercent = 0f,
            jitterMs = 0.0,
            averageRttMs = 0.0,
            wifiRssiDbm = null
        )

        assertEquals(100, healthy)
        assertTrue(degraded < 40)
        assertEquals(0, offline)
    }
}
