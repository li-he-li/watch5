package com.heartrate.phone.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HeartRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HeartRateEntity>): List<Long>

    @Query(
        """
        SELECT * FROM heart_rate_records
        WHERE synced = 0
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int): List<HeartRateEntity>

    @Query("UPDATE heart_rate_records SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM heart_rate_records ORDER BY timestamp ASC")
    suspend fun getAll(): List<HeartRateEntity>

    @Query("SELECT * FROM heart_rate_records ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<HeartRateEntity>>
}
