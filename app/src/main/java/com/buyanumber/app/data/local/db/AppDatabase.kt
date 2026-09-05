package com.buyanumber.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackedOrder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackedOrderDao(): TrackedOrderDao

    companion object {
        const val NAME = "buyanumber.db"
    }
}
