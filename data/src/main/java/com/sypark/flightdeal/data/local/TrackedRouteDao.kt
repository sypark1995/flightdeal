package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedRouteDao {

    @Query("SELECT * FROM tracked_route ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackedRouteEntity>>

    /** 워커용. Flow가 아니라 한 번만 읽는다. */
    @Query("SELECT * FROM tracked_route")
    suspend fun getAll(): List<TrackedRouteEntity>

    /** 이미 있으면 -1을 돌려준다. 중복 등록은 오류가 아니라 "이미 추적 중"이다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TrackedRouteEntity): Long

    @Query(
        "SELECT id FROM tracked_route WHERE originIata = :origin " +
            "AND destinationIata = :destination AND departDate = :departDate " +
            "AND returnDate = :returnDate AND tripType = :tripType LIMIT 1"
    )
    suspend fun findId(
        origin: String,
        destination: String,
        departDate: String,
        returnDate: String,
        tripType: String,
    ): Long?

    /** 알림이 실제로 전달된 뒤에만 부른다. */
    @Query("UPDATE tracked_route SET notifiedPrice = :price WHERE id = :id")
    suspend fun updateNotifiedPrice(id: Long, price: Int)

    /** notifiedPrice(통보 기준선)는 건드리지 않는다. */
    @Query("UPDATE tracked_route SET targetPrice = :target WHERE id = :id")
    suspend fun updateTargetPrice(id: Long, target: Int?)

    @Query("DELETE FROM tracked_route WHERE id = :id")
    suspend fun deleteById(id: Long)
}
