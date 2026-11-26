package com.wifix.app.core.download

import android.content.ContentValues
import android.content.Context
import android.net.Network
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.wifix.app.data.db.DownloadSegmentEntity
import kotlinx.coroutines.CancellationException
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Genuine HTTP Range segmented download engine.
 * Supports multi-threaded RandomAccessFile writes, ETag/Last-Modified validation,
 * automatic single-stream fallback when Range is unsupported, and MediaStore/SAF export.
 */
class ParallelDownloadEngine(private val context: Context) {

    private val activeCallsByTask = ConcurrentHashMap<Long, CopyOnWriteArrayList<Call>>()

    fun cancelTaskCalls(taskId: Long) {
        activeCallsByTask.remove(taskId)?.forEach { call ->
            runCatching { call.cancel() }
        }
    }

    suspend fun probeUrl(url: String, network: Network?): ProbeResult = withContext(Dispatchers.IO) {
        val client = buildClient(network)
        var headLength = -1L
        var headRange = false
        var etag: String? = null
        var lastModified: String? = null
        var mimeType = "application/octet-stream"
        var disposition: String? = null
        var finalUrl = url

        // 1. Try lightweight HEAD request
        try {
            val headReq = Request.Builder().url(url).head().build()
            client.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    finalUrl = resp.request.url.toString()
                    headLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    headRange = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    etag = resp.header("ETag")
                    lastModified = resp.header("Last-Modified")
                    mimeType = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?: "application/octet-stream"
                    disposition = resp.header("Content-Disposition")
                }
            }
        } catch (_: IOException) {
            // Fall through to 1-byte Range probe
        }

        // 2. Verify true 206 Partial Content support with Range: bytes=0-0 if uncertain
        if (!headRange || headLength <= 0L) {
            val probeReq = Request.Builder()
                .url(finalUrl)
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(probeReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    throw IOException("Server returned HTTP ${resp.code}")
                }
                finalUrl = resp.request.url.toString()
                if (etag == null) etag = resp.header("ETag")
                if (lastModified == null) lastModified = resp.header("Last-Modified")
                if (disposition == null) disposition = resp.header("Content-Disposition")
                resp.header("Content-Type")?.substringBefore(';')?.trim()?.let { mimeType = it }

                if (resp.code == 206) {
                    headRange = true
                    val contentRange = resp.header("Content-Range")
                    val parsedTotal = contentRange?.substringAfterLast('/')?.toLongOrNull()
                    if (parsedTotal != null && parsedTotal > 0L) {
                        headLength = parsedTotal
                    }
                } else {
                    headRange = false
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    if (len > 0L) headLength = len
                }
            }
        }

        val fileName = DownloadMath.extractFileName(finalUrl, disposition)
        ProbeResult(
            finalUrl = finalUrl,
            fileName = fileName,
            mimeType = mimeType,
            totalBytes = headLength,
            supportsRange = headRange && headLength > 0L,
            etag = etag,
            lastModified = lastModified
        )
    }

    suspend fun downloadSegments(
        taskId: Long,
        url: String,
        partFile: File,
        totalBytes: Long,
        supportsRange: Boolean,
        segments: List<DownloadSegmentEntity>,
        network: Network?,
        onSegmentPersist: suspend (segmentIndex: Int, offset: Long, completed: Boolean) -> Unit,
        onProgressTick: suspend (downloadedBytes: Long, speedBytesPerSec: Long, activeThreads: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        partFile.parentFile?.mkdirs()
        if (supportsRange && totalBytes > 0L && (!partFile.exists() || partFile.length() != totalBytes)) {
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }
        }

        val client = buildClient(network)
        val taskCalls = CopyOnWriteArrayList<Call>()
        activeCallsByTask[taskId] = taskCalls

        val initialDownloaded = segments.sumOf { seg ->
            if (supportsRange && seg.endByte >= seg.startByte) {
                (seg.currentOffset - seg.startByte).coerceAtLeast(0L)
            } else {
                seg.currentOffset.coerceAtLeast(0L)
            }
        }
        val totalDownloaded = AtomicLong(initialDownloaded)
        val activeWorkers = AtomicInteger(0)

        try {
            coroutineScope {
                val tickerJob = launch {
                    var prevBytes = totalDownloaded.get()
                    var prevNs = System.nanoTime()
                    while (isActive) {
                        delay(500L)
                        val nowNs = System.nanoTime()
                        val curBytes = totalDownloaded.get()
                        val deltaBytes = (curBytes - prevBytes).coerceAtLeast(0L)
                        val deltaSec = ((nowNs - prevNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                        val bytesPerSec = (deltaBytes / deltaSec).toLong()
                        onProgressTick(curBytes, bytesPerSec, activeWorkers.get())
                        prevBytes = curBytes
                        prevNs = nowNs
                    }
                }

                try {
                    val workerJobs = segments.filter { !it.completed }.map { segment ->
                        async(Dispatchers.IO) {
                            activeWorkers.incrementAndGet()
                            try {
                                if (supportsRange && segment.endByte >= segment.startByte) {
                                    runRangedSegment(
                                        client = client,
                                        taskCalls = taskCalls,
                                        url = url,
                                        partFile = partFile,
                                        segment = segment,
                                        totalDownloaded = totalDownloaded,
                                        onSegmentPersist = onSegmentPersist
                                    )
                                } else {
                                    runSingleStream(
                                        client = client,
                                        taskCalls = taskCalls,
                                        url = url,
                                        partFile = partFile,
                                        totalDownloaded = totalDownloaded,
                                        onSegmentPersist = onSegmentPersist
                                    )
                                }
                            } finally {
                                activeWorkers.decrementAndGet()
                            }
                        }
                    }
                    workerJobs.awaitAll()
                    onProgressTick(totalDownloaded.get(), 0L, 0)
                } finally {
                    tickerJob.cancel()
                }
            }
        } finally {
            activeCallsByTask.remove(taskId)
        }
    }

    private suspend fun runRangedSegment(
        client: OkHttpClient,
        taskCalls: CopyOnWriteArrayList<Call>,
        url: String,
        partFile: File,
        segment: DownloadSegmentEntity,
        totalDownloaded: AtomicLong,
        onSegmentPersist: suspend (Int, Long, Boolean) -> Unit
    ) {
        var currentOffset = segment.currentOffset
        val endByte = segment.endByte
        var attempt = 0
        val maxAttempts = 4
        val buffer = ByteArray(32 * 1024)

        while (currentOffset <= endByte) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                throw CancellationException("Download paused")
            }

            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$currentOffset-$endByte")
                .build()

            val call = client.newCall(request)
            taskCalls.add(call)
            var lastPersistNs = System.nanoTime()

            try {
                call.execute().use { response ->
                    if (response.code != 206) {
                        throw IOException("Expected HTTP 206 for segment ${segment.segmentIndex}, got ${response.code}")
                    }
                    val input = response.body?.byteStream()
                        ?: throw IOException("Empty body on segment ${segment.segmentIndex}")

                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(currentOffset)
                        while (currentOffset <= endByte) {
                            val remaining = (endByte - currentOffset + 1L)
                            val maxRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, maxRead)
                            if (read == -1) break

                            raf.write(buffer, 0, read)
                            currentOffset += read
                            totalDownloaded.addAndGet(read.toLong())

                            val nowNs = System.nanoTime()
                            if (nowNs - lastPersistNs > 750_000_000L) {
                                onSegmentPersist(segment.segmentIndex, currentOffset, false)
                                lastPersistNs = nowNs
                            }
                        }
                    }
                }
                attempt = 0
                if (currentOffset > endByte) {
                    onSegmentPersist(segment.segmentIndex, currentOffset, true)
                    return
                }
            } catch (ce: CancellationException) {
                onSegmentPersist(segment.segmentIndex, currentOffset, false)
                throw ce
            } catch (ioe: IOException) {
                onSegmentPersist(segment.segmentIndex, currentOffset, false)
                attempt++
                if (attempt >= maxAttempts || call.isCanceled()) {
                    throw ioe
                }
                delay(1_000L * attempt)
            } finally {
                taskCalls.remove(call)
            }
        }
    }

    private suspend fun runSingleStream(
        client: OkHttpClient,
        taskCalls: CopyOnWriteArrayList<Call>,
        url: String,
        partFile: File,
        totalDownloaded: AtomicLong,
        onSegmentPersist: suspend (Int, Long, Boolean) -> Unit
    ) {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        taskCalls.add(call)
        val buffer = ByteArray(32 * 1024)
        var written = 0L
        var lastPersistNs = System.nanoTime()

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading file")
                }
                val input = response.body?.byteStream() ?: throw IOException("Empty response body")
                partFile.outputStream().use { out ->
                    while (true) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            throw CancellationException("Single-stream download paused")
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        totalDownloaded.set(written)

                        val nowNs = System.nanoTime()
                        if (nowNs - lastPersistNs > 750_000_000L) {
                            onSegmentPersist(0, written, false)
                            lastPersistNs = nowNs
                        }
                    }
                }
            }
            onSegmentPersist(0, written, true)
        } finally {
            taskCalls.remove(call)
        }
    }

    /**
     * Exports verified .part file into either a user-selected SAF tree directory
     * or public Android Downloads/WiFiX via MediaStore.
     */
    suspend fun exportCompletedFile(
        partFile: File,
        fileName: String,
        mimeType: String,
        customTreeUriString: String?
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        if (!customTreeUriString.isNullOrBlank()) {
            val treeUri = Uri.parse(customTreeUriString)
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val createdUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                mimeType.ifBlank { "application/octet-stream" },
                fileName
            ) ?: throw IOException("Unable to create file in selected download folder")

            resolver.openOutputStream(createdUri)?.use { out ->
                FileInputStream(partFile).use { input -> input.copyTo(out, 64 * 1024) }
            } ?: throw IOException("Unable to open output stream for selected folder")

            partFile.delete()
            return@withContext Pair(createdUri.toString(), "Custom Folder / $fileName")
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WiFiX")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to allocate entry in Downloads/WiFiX")

        try {
            resolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(partFile).use { input -> input.copyTo(out, 64 * 1024) }
            } ?: throw IOException("Unable to write to Downloads/WiFiX")

            val doneValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(targetUri, doneValues, null, null)
            partFile.delete()
            Pair(targetUri.toString(), "Downloads/WiFiX/$fileName")
        } catch (e: Exception) {
            resolver.delete(targetUri, null, null)
            throw e
        }
    }

    private fun buildClient(network: Network?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (network != null) {
            builder.socketFactory(network.socketFactory)
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return network.getAllByName(hostname).toList()
                }
            })
        }
        return builder.build()
    }
}
