package com.wifix.app.core.speedtest

import android.net.Network
import com.wifix.app.core.network.ActiveTransportType
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.core.network.TcpLatencyProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Genuine multi-stream HTTP/HTTPS speed test & bufferbloat engine.
 * Binds sockets and DNS lookups explicitly to the active Android Network handle.
 */
class SpeedTestEngine {

    private val latencyProbe = TcpLatencyProbe()
    private val activeCalls = CopyOnWriteArrayList<Call>()

    fun cancelActiveCalls() {
        activeCalls.forEach { runCatching { it.cancel() } }
        activeCalls.clear()
    }

    suspend fun runFullTest(
        server: SpeedTestServer,
        network: Network?,
        transport: ActiveTransportType,
        wifiRssiDbm: Int?,
        onProgress: (SpeedTestProgress) -> Unit
    ): SpeedTestProgress = withContext(Dispatchers.IO) {
        val client = buildClient(network)
        var state = SpeedTestProgress(phase = SpeedTestPhase.DNS, progressFraction = 0.05f)
        onProgress(state)

        // 1. DNS Resolution Benchmark
        val dnsMs = measureDnsLookupMs(server.host, network)
        state = state.copy(
            phase = SpeedTestPhase.LATENCY,
            progressFraction = 0.14f,
            dnsLookupMs = dnsMs
        )
        onProgress(state)

        // 2. Idle TCP Ping, Jitter & Probe Loss
        val idleLatency = latencyProbe.measure(
            host = server.host,
            port = 443,
            samples = 8,
            timeoutMs = 2_000,
            network = network,
            transport = transport
        )

        val idlePing = idleLatency.averageRttMs
        if (idlePing == null) {
            val failed = state.copy(
                phase = SpeedTestPhase.FAILED,
                progressFraction = 0f,
                packetLossPercent = idleLatency.lossPercent,
                errorMessage = idleLatency.errorMessage ?: "Unable to reach ${server.host}"
            )
            onProgress(failed)
            return@withContext failed
        }

        state = state.copy(
            phase = SpeedTestPhase.DOWNLOAD,
            progressFraction = 0.22f,
            idlePingMs = idlePing,
            jitterMs = idleLatency.jitterMs ?: 0.0,
            packetLossPercent = idleLatency.lossPercent,
            liveCurveMbps = emptyList()
        )
        onProgress(state)

        // 3. Multi-Stream Download + Concurrent Loaded Latency (Bufferbloat)
        val dlResult = runDownloadStage(
            client = client,
            server = server,
            network = network,
            transport = transport,
            durationMs = 8_000L,
            streamCount = 4
        ) { liveMbps, fraction, curve ->
            state = state.copy(
                phase = SpeedTestPhase.DOWNLOAD,
                progressFraction = 0.22f + (fraction * 0.43f),
                currentSpeedMbps = liveMbps,
                liveCurveMbps = curve
            )
            onProgress(state)
        }

        if (dlResult.averageMbps <= 0.0 && dlResult.error != null) {
            val failed = state.copy(
                phase = SpeedTestPhase.FAILED,
                errorMessage = dlResult.error
            )
            onProgress(failed)
            return@withContext failed
        }

        val consistency = SpeedAnalytics.calculateConsistencyScore(dlResult.intervalSamplesMbps)
        state = state.copy(
            downloadMbps = dlResult.averageMbps,
            loadedDownloadPingMs = dlResult.loadedPingMs,
            consistencyScore = consistency
        )

        // 4. Multi-Stream Upload + Concurrent Loaded Latency (if server supports upload)
        var ulMbps: Double? = null
        var loadedUlPing: Double? = null
        var note: String? = null

        if (server.uploadUrl != null) {
            state = state.copy(
                phase = SpeedTestPhase.UPLOAD,
                progressFraction = 0.66f,
                currentSpeedMbps = 0.0,
                liveCurveMbps = emptyList()
            )
            onProgress(state)

            val ulResult = runUploadStage(
                client = client,
                server = server,
                network = network,
                transport = transport,
                durationMs = 6_500L,
                streamCount = 3
            ) { liveMbps, fraction, curve ->
                state = state.copy(
                    phase = SpeedTestPhase.UPLOAD,
                    progressFraction = 0.66f + (fraction * 0.32f),
                    currentSpeedMbps = liveMbps,
                    liveCurveMbps = curve
                )
                onProgress(state)
            }

            if (ulResult.averageMbps > 0.0) {
                ulMbps = ulResult.averageMbps
                loadedUlPing = ulResult.loadedPingMs
            } else {
                note = ulResult.error ?: "Upload endpoint did not complete."
            }
        } else {
            note = "Selected server is a download-only mirror. Switch to Cloudflare Edge for full upload benchmarking."
        }

        val bloatGrade = SpeedAnalytics.gradeBufferbloat(
            idlePingMs = idlePing,
            loadedDownloadMs = dlResult.loadedPingMs,
            loadedUploadMs = loadedUlPing
        )
        val stability = NetworkMath.calculateStabilityScore(
            isValidated = true,
            lossPercent = idleLatency.lossPercent,
            jitterMs = idleLatency.jitterMs,
            averageRttMs = idlePing,
            wifiRssiDbm = wifiRssiDbm
        )

        val completed = state.copy(
            phase = SpeedTestPhase.COMPLETED,
            progressFraction = 1f,
            currentSpeedMbps = dlResult.averageMbps,
            uploadMbps = ulMbps,
            loadedUploadPingMs = loadedUlPing,
            bufferbloatGrade = bloatGrade,
            stabilityScore = stability,
            note = note
        )
        onProgress(completed)
        completed
    }

    private data class TransferStageResult(
        val averageMbps: Double,
        val intervalSamplesMbps: List<Double>,
        val loadedPingMs: Double?,
        val error: String? = null
    )

    private suspend fun runDownloadStage(
        client: OkHttpClient,
        server: SpeedTestServer,
        network: Network?,
        transport: ActiveTransportType,
        durationMs: Long,
        streamCount: Int,
        onTick: (liveMbps: Double, fraction: Float, curve: List<Double>) -> Unit
    ): TransferStageResult = coroutineScope {
        val totalBytes = AtomicLong(0L)
        val postWarmupBytes = AtomicLong(0L)
        val warmupDone = AtomicBoolean(false)
        val running = AtomicBoolean(true)
        val loadedRtts = CopyOnWriteArrayList<Double>()
        val intervalSamples = mutableListOf<Double>()
        var firstError: String? = null

        val startNs = System.nanoTime()
        val warmupMs = 1_000L
        var measurementStartNs = startNs

        // Background loaded latency probe for real bufferbloat measurement
        val bloatJob = launch(Dispatchers.IO) {
            delay(800L)
            while (running.get() && isActive) {
                val sample = latencyProbe.measure(
                    host = server.host,
                    port = 443,
                    samples = 1,
                    timeoutMs = 1_500,
                    network = network,
                    transport = transport
                )
                sample.averageRttMs?.let { loadedRtts.add(it) }
                delay(650L)
            }
        }

        // Sampling ticker for live UI curve
        val tickerJob = launch(Dispatchers.IO) {
            var prevBytes = 0L
            var prevNs = System.nanoTime()
            while (running.get() && isActive) {
                delay(250L)
                val nowNs = System.nanoTime()
                val elapsedMs = (nowNs - startNs) / 1_000_000L
                if (!warmupDone.get() && elapsedMs >= warmupMs) {
                    warmupDone.set(true)
                    measurementStartNs = nowNs
                }

                val curBytes = totalBytes.get()
                val deltaBytes = (curBytes - prevBytes).coerceAtLeast(0L)
                val deltaSec = ((nowNs - prevNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                val instMbps = (deltaBytes * 8.0) / (deltaSec * 1_000_000.0)

                if (warmupDone.get()) {
                    intervalSamples.add(instMbps)
                }
                val curveSnapshot = (intervalSamples.ifEmpty { listOf(instMbps) }).takeLast(28)
                val frac = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                onTick(instMbps, frac, curveSnapshot)

                prevBytes = curBytes
                prevNs = nowNs

                if (elapsedMs >= durationMs) {
                    running.set(false)
                    cancelActiveCalls()
                    break
                }
            }
        }

        // Parallel HTTP download workers
        val workers = (1..streamCount).map { streamIdx ->
            async(Dispatchers.IO) {
                val buffer = ByteArray(32 * 1024)
                while (running.get() && isActive) {
                    val separator = if (server.downloadUrl.contains("?")) "&" else "?"
                    val request = Request.Builder()
                        .url("${server.downloadUrl}${separator}cb=${System.nanoTime()}_$streamIdx")
                        .header("Cache-Control", "no-store")
                        .build()

                    val call = client.newCall(request)
                    activeCalls.add(call)
                    try {
                        call.execute().use { response ->
                            if (!response.isSuccessful) {
                                if (firstError == null) firstError = "HTTP ${response.code} from ${server.name}"
                                break
                            }
                            val stream = response.body?.byteStream() ?: break
                            while (running.get() && isActive) {
                                val read = stream.read(buffer)
                                if (read == -1) break
                                totalBytes.addAndGet(read.toLong())
                                if (warmupDone.get()) {
                                    postWarmupBytes.addAndGet(read.toLong())
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (running.get() && firstError == null) {
                            firstError = e.message ?: "Download stream interrupted"
                        }
                        if (!running.get()) break
                    } finally {
                        activeCalls.remove(call)
                    }
                }
            }
        }

        workers.awaitAll()
        running.set(false)
        tickerJob.cancel()
        bloatJob.cancel()

        val endNs = System.nanoTime()
        val measuredSec = if (warmupDone.get()) {
            ((endNs - measurementStartNs) / 1_000_000_000.0).coerceAtLeast(0.5)
        } else {
            ((endNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.5)
        }
        val measuredBytes = if (warmupDone.get() && postWarmupBytes.get() > 0L) {
            postWarmupBytes.get()
        } else {
            totalBytes.get()
        }
        val avgMbps = (measuredBytes * 8.0) / (measuredSec * 1_000_000.0)

        TransferStageResult(
            averageMbps = avgMbps,
            intervalSamplesMbps = intervalSamples.toList(),
            loadedPingMs = loadedRtts.takeIf { it.isNotEmpty() }?.average(),
            error = firstError
        )
    }

    private suspend fun runUploadStage(
        client: OkHttpClient,
        server: SpeedTestServer,
        network: Network?,
        transport: ActiveTransportType,
        durationMs: Long,
        streamCount: Int,
        onTick: (liveMbps: Double, fraction: Float, curve: List<Double>) -> Unit
    ): TransferStageResult = coroutineScope {
        val uploadUrl = server.uploadUrl ?: return@coroutineScope TransferStageResult(0.0, emptyList(), null)
        val totalBytes = AtomicLong(0L)
        val postWarmupBytes = AtomicLong(0L)
        val warmupDone = AtomicBoolean(false)
        val running = AtomicBoolean(true)
        val loadedRtts = CopyOnWriteArrayList<Double>()
        val intervalSamples = mutableListOf<Double>()
        var firstError: String? = null

        val startNs = System.nanoTime()
        val warmupMs = 800L
        var measurementStartNs = startNs
        val payloadChunk = ByteArray(32 * 1024) { idx -> (idx and 0xFF).toByte() }
        val chunkPostSize = 1_500_000L

        val bloatJob = launch(Dispatchers.IO) {
            delay(700L)
            while (running.get() && isActive) {
                val sample = latencyProbe.measure(
                    host = server.host,
                    port = 443,
                    samples = 1,
                    timeoutMs = 1_500,
                    network = network,
                    transport = transport
                )
                sample.averageRttMs?.let { loadedRtts.add(it) }
                delay(650L)
            }
        }

        val tickerJob = launch(Dispatchers.IO) {
            var prevBytes = 0L
            var prevNs = System.nanoTime()
            while (running.get() && isActive) {
                delay(250L)
                val nowNs = System.nanoTime()
                val elapsedMs = (nowNs - startNs) / 1_000_000L
                if (!warmupDone.get() && elapsedMs >= warmupMs) {
                    warmupDone.set(true)
                    measurementStartNs = nowNs
                }

                val curBytes = totalBytes.get()
                val deltaBytes = (curBytes - prevBytes).coerceAtLeast(0L)
                val deltaSec = ((nowNs - prevNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                val instMbps = (deltaBytes * 8.0) / (deltaSec * 1_000_000.0)

                if (warmupDone.get()) {
                    intervalSamples.add(instMbps)
                }
                val curveSnapshot = (intervalSamples.ifEmpty { listOf(instMbps) }).takeLast(28)
                val frac = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                onTick(instMbps, frac, curveSnapshot)

                prevBytes = curBytes
                prevNs = nowNs

                if (elapsedMs >= durationMs) {
                    running.set(false)
                    cancelActiveCalls()
                    break
                }
            }
        }

        val workers = (1..streamCount).map {
            async(Dispatchers.IO) {
                while (running.get() && isActive) {
                    val body = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength() = chunkPostSize
                        override fun writeTo(sink: BufferedSink) {
                            var sent = 0L
                            while (sent < chunkPostSize && running.get()) {
                                val toWrite = minOf(payloadChunk.size.toLong(), chunkPostSize - sent).toInt()
                                sink.write(payloadChunk, 0, toWrite)
                                sent += toWrite
                                totalBytes.addAndGet(toWrite.toLong())
                                if (warmupDone.get()) {
                                    postWarmupBytes.addAndGet(toWrite.toLong())
                                }
                            }
                            sink.flush()
                        }
                    }

                    val request = Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .header("Cache-Control", "no-store")
                        .build()

                    val call = client.newCall(request)
                    activeCalls.add(call)
                    try {
                        call.execute().use { response ->
                            if (!response.isSuccessful && firstError == null) {
                                firstError = "Upload HTTP ${response.code}"
                            }
                        }
                    } catch (e: IOException) {
                        if (running.get() && firstError == null) {
                            firstError = e.message ?: "Upload stream interrupted"
                        }
                        if (!running.get()) break
                    } finally {
                        activeCalls.remove(call)
                    }
                }
            }
        }

        workers.awaitAll()
        running.set(false)
        tickerJob.cancel()
        bloatJob.cancel()

        val endNs = System.nanoTime()
        val measuredSec = if (warmupDone.get()) {
            ((endNs - measurementStartNs) / 1_000_000_000.0).coerceAtLeast(0.5)
        } else {
            ((endNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.5)
        }
        val measuredBytes = if (warmupDone.get() && postWarmupBytes.get() > 0L) {
            postWarmupBytes.get()
        } else {
            totalBytes.get()
        }
        val avgMbps = (measuredBytes * 8.0) / (measuredSec * 1_000_000.0)

        TransferStageResult(
            averageMbps = avgMbps,
            intervalSamplesMbps = intervalSamples.toList(),
            loadedPingMs = loadedRtts.takeIf { it.isNotEmpty() }?.average(),
            error = firstError
        )
    }

    private fun measureDnsLookupMs(host: String, network: Network?): Double? {
        return try {
            val startNs = System.nanoTime()
            if (network != null) {
                network.getAllByName(host)
            } else {
                InetAddress.getAllByName(host)
            }
            (System.nanoTime() - startNs) / 1_000_000.0
        } catch (_: Exception) {
            null
        }
    }

    private fun buildClient(network: Network?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        if (network != null) {
            builder.socketFactory(network.socketFactory)
            builder.dns(Dns { hostname -> network.getAllByName(hostname).toList() })
        }
        return builder.build()
    }
}
