package com.wifix.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SpeedTestRecord::class],
    version = 1,
    exportSchema = false
)
abstract class WiFiXDatabase : RoomDatabase() {

    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        @Volatile
        private var instance: WiFiXDatabase? = null

        fun getInstance(context: Context): WiFiXDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WiFiXDatabase::class.java,
                    "wifix.db"
                ).build().also { instance = it }
            }
        }
    }
}
