package com.wifix.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus(val label: String) {
    QUEUED("Queued"),
    SCHEDULED("Scheduled"),
    PROBING("Inspecting"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    FINALIZING("Verifying"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

enum class DownloadCategory(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENTS("Docs"),
    ARCHIVES("Archives"),
    APPS("APKs"),
    IMAGES("Images"),
    OTHER("Other")
}

enum class DownloadNetworkPolicy(val label: String) {
    AUTO("Auto"),
    WIFI_ONLY("Wi-Fi Only"),
    MOBILE_ALLOWED("Mobile OK")
}

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val status: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val supportsRange: Boolean,
    val threadCount: Int,
    val networkPolicy: String,
    val etag: String?,
    val lastModified: String?,
    val partFilePath: String,
    val destinationUri: String?,
    val destinationDisplay: String,
    val expectedSha256: String?,
    val computedSha256: String?,
    val integrityVerified: Boolean?,
    val scheduledAtMs: Long?,
    val errorMessage: String?,
    val createdAtMs: Long,
    val completedAtMs: Long?
)

@Entity(
    tableName = "download_segments",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class DownloadSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val segmentIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val currentOffset: Long,
    val completed: Boolean
)
