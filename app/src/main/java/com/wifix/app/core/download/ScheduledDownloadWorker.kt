package com.wifix.app.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ScheduledDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()
        DownloadRepository.getInstance(applicationContext).triggerScheduledDownload(taskId)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "download_task_id"
    }
}
