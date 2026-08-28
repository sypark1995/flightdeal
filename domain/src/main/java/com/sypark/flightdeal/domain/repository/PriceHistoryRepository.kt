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

    /** 기기에 쌓인 가격 관측 건수. 내정보 화면이 "무엇을 저장하고 있는지" 보여주는 데 쓴다. */
    fun observeCount(): Flow<Int>

    /**
     * 관측 이력만 지운다. 추적 항목과 통보 기준선([TrackedRoute.notifiedPrice])은 남는다 —
     * 추적을 계속하겠다는 사용자의 결정까지 취소하지 않는다. 그래프만 비고 다시 쌓인다.
     */
    suspend fun clearAll()
}
