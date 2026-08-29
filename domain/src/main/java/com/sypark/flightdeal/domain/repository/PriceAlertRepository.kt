package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.PriceAlert
import com.sypark.flightdeal.domain.model.PriceChange
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface PriceAlertRepository {

    /** 알림을 띄운 직후에만 부른다. */
    suspend fun record(changes: List<PriceChange>, at: Instant)

    fun observeRecent(days: Int): Flow<List<PriceAlert>>

    suspend fun pruneOlderThan(days: Int)
}
