package com.wifix.app.core.network

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures real TCP handshake RTT, jitter, and probe failure rate against an endpoint.
 * Accepts an explicit Android Network handle so Wi-Fi and Cellular can be benchmarked independently.
 */
class TcpLatencyProbe {

    suspend fun measure(
        host: String = "1.1.1.1",
        port: Int = 443,
        samples: Int = 5,
        timeoutMs: Int = 2_000,
        network: Network? = null,
        transport: ActiveTransportType = ActiveTransportType.NONE
    ): LatencyReport = withContext(Dispatchers.IO) {
        val successfulRtts = mutableListOf<Double>()
        var lastError: String? = null

        for (index in 0 until samples) {
            val socket = try {
                network?.socketFactory?.createSocket() ?: Socket()
            } catch (e: Exception) {
                lastError = e.message ?: "Socket creation failed"
                null
            }

            if (socket != null) {
                try {
                    val startNs = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                    successfulRtts.add(elapsedMs)
                } catch (e: Exception) {
                    lastError = e.message ?: "Connection timed out"
                } finally {
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                }
            }

            if (index < samples - 1) {
                delay(120L)
            }
        }

        val failedCount = samples - successfulRtts.size
        val lossPercent = (failedCount.toFloat() / samples.coerceAtLeast(1)) * 100f

        if (successfulRtts.isEmpty()) {
            LatencyReport(
                targetHost = host,
                targetPort = port,
                lossPercent = 100f,
                sampleCount = samples,
                measuredOver = transport,
                isMeasuring = false,
                errorMessage = lastError ?: "All probes timed out"
            )
        } else {
            LatencyReport(
                targetHost = host,
                targetPort = port,
                averageRttMs = successfulRtts.average(),
                minRttMs = successfulRtts.minOrNull(),
                maxRttMs = successfulRtts.maxOrNull(),
                jitterMs = NetworkMath.calculateJitterMs(successfulRtts),
                lossPercent = lossPercent,
                sampleCount = samples,
                measuredOver = transport,
                isMeasuring = false,
                errorMessage = null
            )
        }
    }
}
