package com.wifix.app.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wifix.app.core.network.NetworkMath
import com.wifix.app.data.db.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("WiFiX Download Engine", "Preparing download queue...", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val repo = DownloadRepository.getInstance(this)
        serviceScope.launch {
            combine(repo.tasksFlow, repo.liveMetrics) { tasks, metrics ->
                Pair(tasks, metrics)
            }.collect { (tasks, metrics) ->
                val active = tasks.filter {
                    it.status == DownloadStatus.DOWNLOADING.name ||
                        it.status == DownloadStatus.PROBING.name ||
                        it.status == DownloadStatus.FINALIZING.name
                }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                val primary = active.first()
                val live = metrics[primary.id]
                val speedMbps = live?.let { NetworkMath.bytesPerSecToMbps(it.speedBytesPerSec) } ?: 0.0
                val progress = if (primary.totalBytes > 0L) {
                    ((primary.downloadedBytes * 100L) / primary.totalBytes).toInt().coerceIn(0, 100)
                } else 0

                val title = if (active.size == 1) {
                    primary.fileName
                } else {
                    "${active.size} active downloads"
                }
                val content = String.format(
                    Locale.US,
                    "%.1f Mbps | %s / %s | ETA %s",
                    speedMbps,
                    NetworkMath.formatByteSize(primary.downloadedBytes),
                    if (primary.totalBytes > 0L) NetworkMath.formatByteSize(primary.totalBytes) else "Unknown",
                    DownloadMath.formatEta(live?.etaSeconds)
                )

                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(title, content, progress, primary.totalBytes <= 0L)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WiFiX Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active parallel download progress"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wifix_downloads_channel"
        private const val NOTIFICATION_ID = 3001
    }
}
