package com.wifix.app.core.download

import com.wifix.app.data.db.DownloadCategory
import com.wifix.app.data.db.DownloadSegmentEntity
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

data class ProbeResult(
    val finalUrl: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val supportsRange: Boolean,
    val etag: String?,
    val lastModified: String?
)

data class LiveDownloadMetrics(
    val speedBytesPerSec: Long = 0L,
    val speedHistoryMbps: List<Double> = emptyList(),
    val etaSeconds: Long? = null,
    val activeThreads: Int = 0
)

object DownloadMath {

    private const val MIN_SEGMENT_BYTES = 512L * 1024L // 512 KB minimum per segment

    /**
     * Splits a file into exact inclusive byte ranges [startByte..endByte].
     * Dynamically lowers thread count for small files or Range-unsupported servers.
     */
    fun buildSegments(
        taskId: Long,
        totalBytes: Long,
        requestedThreads: Int,
        supportsRange: Boolean
    ): List<DownloadSegmentEntity> {
        if (!supportsRange || totalBytes <= 0L) {
            return listOf(
                DownloadSegmentEntity(
                    taskId = taskId,
                    segmentIndex = 0,
                    startByte = 0L,
                    endByte = (totalBytes - 1L).coerceAtLeast(-1L),
                    currentOffset = 0L,
                    completed = false
                )
            )
        }

        val maxUsefulThreads = (totalBytes / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
        val actualThreads = requestedThreads.coerceIn(1, 8).coerceAtMost(maxUsefulThreads)
        val baseSize = totalBytes / actualThreads
        val segments = mutableListOf<DownloadSegmentEntity>()

        for (index in 0 until actualThreads) {
            val start = index * baseSize
            val end = if (index == actualThreads - 1) totalBytes - 1L else ((index + 1) * baseSize) - 1L
            segments.add(
                DownloadSegmentEntity(
                    taskId = taskId,
                    segmentIndex = index,
                    startByte = start,
                    endByte = end,
                    currentOffset = start,
                    completed = false
                )
            )
        }
        return segments
    }

    /**
     * Validates that the remote file has not changed since pause.
     */
    fun isResumeMetadataValid(
        storedEtag: String?,
        storedLastModified: String?,
        storedTotalBytes: Long,
        probe: ProbeResult
    ): Boolean {
        if (storedTotalBytes > 0L && probe.totalBytes > 0L && storedTotalBytes != probe.totalBytes) {
            return false
        }
        if (!storedEtag.isNullOrBlank() && !probe.etag.isNullOrBlank()) {
            return storedEtag == probe.etag
        }
        if (!storedLastModified.isNullOrBlank() && !probe.lastModified.isNullOrBlank()) {
            return storedLastModified == probe.lastModified
        }
        return true
    }

    fun classifyCategory(fileName: String, mimeType: String): DownloadCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = mimeType.lowercase(Locale.US)
        return when {
            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v") ->
                DownloadCategory.VIDEO
            mime.startsWith("audio/") || ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") ->
                DownloadCategory.AUDIO
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") ->
                DownloadCategory.IMAGES
            ext in setOf("apk", "apks", "xapk") || mime == "application/vnd.android.package-archive" ->
                DownloadCategory.APPS
            ext in setOf("zip", "rar", "7z", "tar", "gz", "xz", "iso", "bin") ->
                DownloadCategory.ARCHIVES
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub") ||
                mime.startsWith("text/") ->
                DownloadCategory.DOCUMENTS
            else -> DownloadCategory.OTHER
        }
    }

    fun extractFileName(url: String, contentDisposition: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("filename\\*?=(?:UTF-8''|\"?)([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank()) {
                return sanitizeFileName(match)
            }
        }
        val pathPart = url.substringBefore('?').substringAfterLast('/')
        return if (pathPart.isNotBlank() && pathPart.contains('.')) {
            sanitizeFileName(pathPart)
        } else {
            "wifix_file_${System.currentTimeMillis()}.bin"
        }
    }

    fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120).ifBlank { "download.bin" }
    }

    fun formatEta(etaSeconds: Long?): String {
        if (etaSeconds == null || etaSeconds < 0L) return "--"
        if (etaSeconds < 60L) return "${etaSeconds}s"
        val mins = etaSeconds / 60L
        val secs = etaSeconds % 60L
        if (mins < 60L) return "${mins}m ${secs}s"
        val hrs = mins / 60L
        val remMins = mins % 60L
        return "${hrs}h ${remMins}m"
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return bytesToHex(digest.digest())
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        bytes.forEach { b -> sb.append(String.format(Locale.US, "%02x", b)) }
        return sb.toString()
    }
}
