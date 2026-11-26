package com.wifix.app.core.network

import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Samples device-wide Rx/Tx byte counters via Android TrafficStats using monotonic clock deltas.
 * Automatically stops polling when the UI lifecycle collector is inactive to conserve battery.
 */
class RealtimeTrafficMeter(
    private val intervalMs: Long = 1_000L
) {
    fun observeTraffic(): Flow<ThroughputSample> = flow {
        val initialRx = TrafficStats.getTotalRxBytes()
        val initialTx = TrafficStats.getTotalTxBytes()
        val supported = initialRx != TrafficStats.UNSUPPORTED.toLong() &&
            initialTx != TrafficStats.UNSUPPORTED.toLong()

        if (!supported) {
            emit(ThroughputSample(timestampMs = SystemClock.elapsedRealtime()))
            return@flow
        }

        var prevRx = initialRx
        var prevTx = initialTx
        var prevTimeMs = SystemClock.elapsedRealtime()
        var sessionRx = 0L
        var sessionTx = 0L

        while (currentCoroutineContext().isActive) {
            delay(intervalMs)
            val nowMs = SystemClock.elapsedRealtime()
            val curRx = TrafficStats.getTotalRxBytes()
            val curTx = TrafficStats.getTotalTxBytes()
            val elapsedMs = (nowMs - prevTimeMs).coerceAtLeast(1L)

            val deltaRx = if (curRx >= prevRx) curRx - prevRx else 0L
            val deltaTx = if (curTx >= prevTx) curTx - prevTx else 0L

            sessionRx += deltaRx
            sessionTx += deltaTx

            val rxPerSec = (deltaRx * 1_000L) / elapsedMs
            val txPerSec = (deltaTx * 1_000L) / elapsedMs

            emit(
                ThroughputSample(
                    downloadBytesPerSec = rxPerSec,
                    uploadBytesPerSec = txPerSec,
                    totalSessionRxBytes = sessionRx,
                    totalSessionTxBytes = sessionTx,
                    timestampMs = nowMs
                )
            )

            prevRx = curRx
            prevTx = curTx
            prevTimeMs = nowMs
        }
    }
}
