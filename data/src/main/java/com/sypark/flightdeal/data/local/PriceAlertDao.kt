package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sypark.flightdeal.data.local.entity.PriceAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {

    @Insert
    suspend fun insert(entity: PriceAlertEntity)

    @Query(
        // id 타이브레이크를 둔다. 같은 초에 여러 알림이 들어가면 notifiedAt만으로는
        // 순서가 정해지지 않는다.
        "SELECT * FROM price_alert WHERE notifiedAt >= :sinceEpochSecond " +
            "ORDER BY notifiedAt DESC, id DESC"
    )
    fun observeRecent(sinceEpochSecond: Long): Flow<List<PriceAlertEntity>>

    /** 보관 기간이 지난 기록을 정리한다. price_snapshot과 같은 기간을 쓴다. */
    @Query("DELETE FROM price_alert WHERE notifiedAt < :epochSecond")
    suspend fun deleteOlderThan(epochSecond: Long)
}
