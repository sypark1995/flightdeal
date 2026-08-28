package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedRouteDao {

    @Query("SELECT * FROM tracked_route ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackedRouteEntity>>

    /** 워커용. Flow가 아니라 한 번만 읽는다. */
    @Query("SELECT * FROM tracked_route")
    suspend fun getAll(): List<TrackedRouteEntity>

    @Insert
    suspend fun insert(entity: TrackedRouteEntity): Long

    @Update
    suspend fun update(entity: TrackedRouteEntity)

    @Query("DELETE FROM tracked_route WHERE id = :id")
    suspend fun deleteById(id: Long)
}
