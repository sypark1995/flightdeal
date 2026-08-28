package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TrackedRouteRepository {

    fun observeAll(): Flow<List<TrackedRoute>>

    /** 워커용. 한 번만 읽는다. */
    suspend fun getAll(): List<TrackedRoute>

    /**
     * @return 새로 만들어진 추적 항목의 id
     *
     * `notifiedPrice`에 기본값을 두지 않는다. 잊고 호출하면 통보 기준선이 NULL인
     * 채로 저장되고, 그 행은 어떤 가격 변동도 영영 판정하지 못하는 죽은 행이 된다.
     */
    suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
        notifiedPrice: Won?,
    ): Long

    suspend fun remove(id: Long)

    /** 알림이 실제로 전달된 뒤에만 부른다. */
    suspend fun markNotified(id: Long, price: Won)
}
