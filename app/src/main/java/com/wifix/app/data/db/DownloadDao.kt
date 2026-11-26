package com.wifix.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAtMs DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_segments")
    fun observeAllSegments(): Flow<List<DownloadSegmentEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN ('DOWNLOADING', 'PROBING', 'FINALIZING')")
    suspend fun getInProgressTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY createdAtMs ASC")
    suspend fun getQueuedTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_segments WHERE taskId = :taskId ORDER BY segmentIndex ASC")
    suspend fun getSegmentsForTask(taskId: Long): List<DownloadSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity): Long

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<DownloadSegmentEntity>)

    @Query("UPDATE download_segments SET currentOffset = :offset, completed = :completed WHERE taskId = :taskId AND segmentIndex = :segmentIndex")
    suspend fun updateSegmentProgress(taskId: Long, segmentIndex: Int, offset: Long, completed: Boolean)

    @Query("UPDATE download_tasks SET downloadedBytes = :downloadedBytes WHERE id = :taskId")
    suspend fun updateTaskDownloadedBytes(taskId: Long, downloadedBytes: Long)

    @Query("DELETE FROM download_segments WHERE taskId = :taskId")
    suspend fun deleteSegmentsForTask(taskId: Long)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)
}
