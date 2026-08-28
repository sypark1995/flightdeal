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
        // latestFor와 같은 이유로 id 타이브레이크를 둔다. 같은 초에 들어간 두 행의
        // 순서가 뒤집히면 어느 쪽이 최신인지가 바뀌어 화살표와 색이 반대로 뜬다.
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "AND capturedAt >= :sinceEpochSecond ORDER BY capturedAt ASC, id ASC"
    )
    fun observeFor(trackedRouteId: Long, sinceEpochSecond: Long): Flow<List<PriceSnapshotEntity>>

    @Query(
        // capturedAt은 초 단위라 같은 초에 들어간 두 행의 순서가 정해지지 않는다.
        // id로 타이브레이크해서 항상 나중에 넣은 것이 최근이 되게 한다.
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "ORDER BY capturedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestFor(trackedRouteId: Long): PriceSnapshotEntity?

    /** 이력은 계속 쌓인다. 워커가 돌 때 함께 치운다. */
    @Query("DELETE FROM price_snapshot WHERE capturedAt < :epochSecond")
    suspend fun deleteOlderThan(epochSecond: Long)
}
