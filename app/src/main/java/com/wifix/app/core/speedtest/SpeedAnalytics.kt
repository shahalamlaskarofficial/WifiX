package com.wifix.app.core.speedtest

import com.wifix.app.data.db.SpeedTestRecord
import com.wifix.app.data.prefs.IspPlanConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SpeedAnalytics {

    fun isPeakHour(timestampMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 18..22
    }

    /**
     * Grades bufferbloat from latency increase under load (ms) using standard Waveform thresholds.
     */
    fun gradeBufferbloat(idlePingMs: Double, loadedDownloadMs: Double?, loadedUploadMs: Double?): String {
        val dlDelta = loadedDownloadMs?.let { (it - idlePingMs).coerceAtLeast(0.0) } ?: 0.0
        val ulDelta = loadedUploadMs?.let { (it - idlePingMs).coerceAtLeast(0.0) } ?: 0.0
        val worstDelta = maxOf(dlDelta, ulDelta)
        if (loadedDownloadMs == null && loadedUploadMs == null) return "N/A"
        return when {
            worstDelta < 5.0 -> "A+"
            worstDelta < 30.0 -> "A"
            worstDelta < 60.0 -> "B"
            worstDelta < 200.0 -> "C"
            worstDelta < 400.0 -> "D"
            else -> "F"
        }
    }

    /**
     * Measures throughput stability (0..100) via inverse coefficient of variation across intervals.
     */
    fun calculateConsistencyScore(intervalSamplesMbps: List<Double>): Int {
        val valid = intervalSamplesMbps.filter { it > 0.05 }
        if (valid.size < 3) return if (valid.isEmpty()) 0 else 80
        val mean = valid.average()
        if (mean <= 0.0) return 0
        val variance = valid.sumOf { (it - mean).pow(2) } / valid.size
        val stdDev = sqrt(variance)
        val cv = stdDev / mean
        return ((1.0 - (cv * 1.15)) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun mbpsToMBps(mbps: Double): Double = mbps / 8.0

    fun buildIspVerification(
        records: List<SpeedTestRecord>,
        plan: IspPlanConfig
    ): IspVerificationSummary {
        if (records.isEmpty()) {
            return IspVerificationSummary(
                totalTests = 0,
                averageDownloadMbps = 0.0,
                averageUploadMbps = null,
                downloadDeficitMbps = 0.0,
                deliveredPercent = 0.0,
                peakAverageDownloadMbps = null,
                offPeakAverageDownloadMbps = null,
                wifiAverageDownloadMbps = null,
                cellularAverageDownloadMbps = null,
                repeatedDropsDetected = false,
                verdictTitle = "No Measurements Yet",
                verdictDetail = "Run at least 3 tests across different times before evaluating ISP compliance."
            )
        }

        val avgDl = records.map { it.downloadMbps }.average()
        val ulValues = records.mapNotNull { it.uploadMbps }
        val avgUl = if (ulValues.isNotEmpty()) ulValues.average() else null

        val deficit = (plan.subscribedDownloadMbps - avgDl).coerceAtLeast(0.0)
        val deliveredPct = ((avgDl / plan.subscribedDownloadMbps.coerceAtLeast(1.0)) * 100.0)
            .coerceIn(0.0, 200.0)

        val peak = records.filter { it.isPeakHour }.map { it.downloadMbps }
        val offPeak = records.filter { !it.isPeakHour }.map { it.downloadMbps }
        val wifi = records.filter { it.transportType.equals("Wi-Fi", ignoreCase = true) }
            .map { it.downloadMbps }
        val cellular = records.filter { it.transportType.contains("Mobile", ignoreCase = true) }
            .map { it.downloadMbps }

        val recent = records.take(10)
        val lowRuns = recent.count { it.downloadMbps < plan.subscribedDownloadMbps * 0.80 }
        val repeatedDrops = recent.size >= 3 && lowRuns >= 3

        val (title, detail) = when {
            records.size < 3 -> {
                "Insufficient Evidence (${records.size}/3 Tests)" to
                    "One or two tests cannot prove an ISP fault. Run ${3 - records.size} more test(s) across peak and off-peak hours."
            }
            repeatedDrops -> {
                "Repeated Under-Delivery Detected" to
                    "$lowRuns of your last ${recent.size} runs delivered below 80% of your ${plan.subscribedDownloadMbps.roundToInt()} Mbps plan (avg ${String.format(Locale.US, "%.1f", avgDl)} Mbps)."
            }
            deliveredPct >= 85.0 -> {
                "Plan Performance Healthy" to
                    "Average delivery is ${String.format(Locale.US, "%.1f%%", deliveredPct)} of your ${plan.subscribedDownloadMbps.roundToInt()} Mbps plan."
            }
            else -> {
                "Moderate Variance Observed" to
                    "Average speed is ${String.format(Locale.US, "%.1f", deficit)} Mbps below plan. Verify wired/5 GHz proximity before filing a complaint."
            }
        }

        return IspVerificationSummary(
            totalTests = records.size,
            averageDownloadMbps = avgDl,
            averageUploadMbps = avgUl,
            downloadDeficitMbps = deficit,
            deliveredPercent = deliveredPct,
            peakAverageDownloadMbps = peak.takeIf { it.isNotEmpty() }?.average(),
            offPeakAverageDownloadMbps = offPeak.takeIf { it.isNotEmpty() }?.average(),
            wifiAverageDownloadMbps = wifi.takeIf { it.isNotEmpty() }?.average(),
            cellularAverageDownloadMbps = cellular.takeIf { it.isNotEmpty() }?.average(),
            repeatedDropsDetected = repeatedDrops,
            verdictTitle = title,
            verdictDetail = detail
        )
    }

    fun formatIspComplaintReport(
        records: List<SpeedTestRecord>,
        plan: IspPlanConfig,
        summary: IspVerificationSummary
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("WiFiX ISP Speed Verification Report")
        sb.appendLine("====================================")
        sb.appendLine("ISP Provider: ${plan.ispName}")
        sb.appendLine(
            "Subscribed Plan: ${String.format(Locale.US, "%.1f", plan.subscribedDownloadMbps)} Mbps DL / " +
                "${String.format(Locale.US, "%.1f", plan.subscribedUploadMbps)} Mbps UL"
        )
        sb.appendLine(
            "Measured Average (${summary.totalTests} tests): " +
                "${String.format(Locale.US, "%.2f", summary.averageDownloadMbps)} Mbps " +
                "(${String.format(Locale.US, "%.2f", mbpsToMBps(summary.averageDownloadMbps))} MB/s) DL"
        )
        sb.appendLine(
            "Difference from Plan: -${String.format(Locale.US, "%.2f", summary.downloadDeficitMbps)} Mbps " +
                "(${String.format(Locale.US, "%.1f%%", summary.deliveredPercent)} delivered)"
        )
        summary.peakAverageDownloadMbps?.let {
            sb.appendLine("Peak Hours (18:00-23:00) Avg: ${String.format(Locale.US, "%.2f", it)} Mbps")
        }
        summary.offPeakAverageDownloadMbps?.let {
            sb.appendLine("Off-Peak Hours Avg: ${String.format(Locale.US, "%.2f", it)} Mbps")
        }
        sb.appendLine("Assessment: ${summary.verdictTitle} - ${summary.verdictDetail}")
        sb.appendLine()
        sb.appendLine("Recorded Tests (Most Recent First):")
        records.take(25).forEach { r ->
            val ulText = r.uploadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "N/A"
            val period = if (r.isPeakHour) "Peak" else "Off-Peak"
            sb.appendLine(
                "- ${df.format(Date(r.timestampMs))} [$period] | ${r.transportType} (${r.networkLabel}) | " +
                    "Server: ${r.serverName} | DL: ${String.format(Locale.US, "%.2f", r.downloadMbps)} Mbps | " +
                    "UL: $ulText | Ping: ${String.format(Locale.US, "%.1f", r.idlePingMs)} ms | " +
                    "Jitter: ${String.format(Locale.US, "%.1f", r.jitterMs)} ms | " +
                    "Loss: ${String.format(Locale.US, "%.0f%%", r.packetLossPercent)} | Bloat: ${r.bufferbloatGrade}"
            )
        }
        return sb.toString()
    }

    fun formatCsvExport(records: List<SpeedTestRecord>): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine(
            "timestamp,server,transport,network_label,download_mbps,download_MBps,upload_mbps," +
                "idle_ping_ms,jitter_ms,loaded_dl_ping_ms,loaded_ul_ping_ms,bufferbloat_grade," +
                "packet_loss_pct,dns_ms,consistency_score,stability_score,is_peak_hour"
        )
        records.forEach { r ->
            sb.appendLine(
                listOf(
                    df.format(Date(r.timestampMs)),
                    r.serverName.replace(",", " "),
                    r.transportType,
                    r.networkLabel.replace(",", " "),
                    String.format(Locale.US, "%.2f", r.downloadMbps),
                    String.format(Locale.US, "%.2f", mbpsToMBps(r.downloadMbps)),
                    r.uploadMbps?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                    String.format(Locale.US, "%.1f", r.idlePingMs),
                    String.format(Locale.US, "%.1f", r.jitterMs),
                    r.loadedDownloadPingMs?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    r.loadedUploadPingMs?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    r.bufferbloatGrade,
                    String.format(Locale.US, "%.1f", r.packetLossPercent),
                    r.dnsLookupMs?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    r.consistencyScore.toString(),
                    r.stabilityScore.toString(),
                    r.isPeakHour.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }
}
