package com.wifix.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_records")
data class SpeedTestRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val serverName: String,
    val transportType: String,
    val networkLabel: String,
    val downloadMbps: Double,
    val uploadMbps: Double?,
    val idlePingMs: Double,
    val jitterMs: Double,
    val loadedDownloadPingMs: Double?,
    val loadedUploadPingMs: Double?,
    val bufferbloatGrade: String,
    val packetLossPercent: Float,
    val dnsLookupMs: Double?,
    val consistencyScore: Int,
    val stabilityScore: Int,
    val isPeakHour: Boolean
)
