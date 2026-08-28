package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.PriceSnapshot
import kotlinx.coroutines.flow.Flow

interface PriceHistoryRepository {

    suspend fun append(snapshot: PriceSnapshot)

    /** 직전 관측값. 변동 판정의 비교 대상이다. */
    suspend fun latest(trackedRouteId: Long): PriceSnapshot?

    fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>>

    /** 이력은 계속 쌓인다. 워커가 돌 때 함께 치운다. */
    suspend fun pruneOlderThan(days: Int)
}
