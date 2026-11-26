package com.wifix.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {

    @Query("SELECT * FROM speed_test_records ORDER BY timestampMs DESC LIMIT 100")
    fun observeRecent(): Flow<List<SpeedTestRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SpeedTestRecord): Long

    @Query("DELETE FROM speed_test_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM speed_test_records")
    suspend fun clearAll()
}
