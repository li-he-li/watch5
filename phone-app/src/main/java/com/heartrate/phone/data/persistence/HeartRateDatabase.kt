package com.heartrate.phone.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HeartRateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HeartRateDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
}

