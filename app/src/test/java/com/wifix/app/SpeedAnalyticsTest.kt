package com.wifix.app

import com.wifix.app.core.speedtest.SpeedAnalytics
import com.wifix.app.data.db.SpeedTestRecord
import com.wifix.app.data.prefs.IspPlanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedAnalyticsTest {

    @Test
    fun bufferbloatGrading_matchesLatencyIncreaseThresholds() {
        assertEquals("A+", SpeedAnalytics.gradeBufferbloat(20.0, 23.0, 22.0))
        assertEquals("A", SpeedAnalytics.gradeBufferbloat(20.0, 40.0, 35.0))
        assertEquals("B", SpeedAnalytics.gradeBufferbloat(20.0, 70.0, 45.0))
        assertEquals("C", SpeedAnalytics.gradeBufferbloat(20.0, 150.0, 80.0))
        assertEquals("F", SpeedAnalytics.gradeBufferbloat(20.0, 500.0, 120.0))
    }

    @Test
    fun consistencyScore_penalizesWildSpeedFluctuations() {
        val steady = SpeedAnalytics.calculateConsistencyScore(listOf(50.0, 51.0, 49.5, 50.5, 50.0))
        val erratic = SpeedAnalytics.calculateConsistencyScore(listOf(10.0, 80.0, 5.0, 95.0, 12.0))

        assertTrue(steady > 90)
        assertTrue(erratic < 40)
    }

    @Test
    fun ispVerification_neverFlagsViolationFromSingleLowTest() {
        val plan = IspPlanConfig(ispName = "FiberISP", subscribedDownloadMbps = 30.0, subscribedUploadMbps = 30.0)
        val oneBadRun = listOf(makeRecord(downloadMbps = 10.0, isPeak = true))
        val singleSummary = SpeedAnalytics.buildIspVerification(oneBadRun, plan)

        assertFalse(singleSummary.repeatedDropsDetected)
        assertEquals(20.0, singleSummary.downloadDeficitMbps, 0.01)

        val threeBadRuns = listOf(
            makeRecord(downloadMbps = 10.0, isPeak = true),
            makeRecord(downloadMbps = 12.0, isPeak = false),
            makeRecord(downloadMbps = 11.0, isPeak = true)
        )
        val repeatedSummary = SpeedAnalytics.buildIspVerification(threeBadRuns, plan)
        assertTrue(repeatedSummary.repeatedDropsDetected)
    }

    @Test
    fun csvExport_includesHeaderAndBothMbpsAndMBps() {
        val csv = SpeedAnalytics.formatCsvExport(listOf(makeRecord(downloadMbps = 40.0, isPeak = false)))
        assertTrue(csv.contains("download_mbps,download_MBps"))
        assertTrue(csv.contains("40.00,5.00"))
    }

    private fun makeRecord(downloadMbps: Double, isPeak: Boolean) = SpeedTestRecord(
        timestampMs = 1_760_000_000_000L,
        serverName = "Cloudflare Edge",
        transportType = "Wi-Fi",
        networkLabel = "5 GHz",
        downloadMbps = downloadMbps,
        uploadMbps = 25.0,
        idlePingMs = 18.0,
        jitterMs = 2.5,
        loadedDownloadPingMs = 30.0,
        loadedUploadPingMs = 28.0,
        bufferbloatGrade = "A",
        packetLossPercent = 0f,
        dnsLookupMs = 14.0,
        consistencyScore = 92,
        stabilityScore = 96,
        isPeakHour = isPeak
    )
}
