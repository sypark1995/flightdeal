package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceSnapshotDao {

    @Insert
    suspend fun insert(entity: PriceSnapshotEntity)

    @Query(
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "AND capturedAt >= :sinceEpochSecond ORDER BY capturedAt ASC"
    )
    fun observeFor(trackedRouteId: Long, sinceEpochSecond: Long): Flow<List<PriceSnapshotEntity>>

    @Query(
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "ORDER BY capturedAt DESC LIMIT 1"
    )
    suspend fun latestFor(trackedRouteId: Long): PriceSnapshotEntity?

    /** 이력은 계속 쌓인다. 워커가 돌 때 함께 치운다. */
    @Query("DELETE FROM price_snapshot WHERE capturedAt < :epochSecond")
    suspend fun deleteOlderThan(epochSecond: Long)
}
