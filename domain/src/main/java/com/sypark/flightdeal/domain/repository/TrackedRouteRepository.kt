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

    /** @return 새로 만들어진 추적 항목의 id */
    suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
    ): Long

    suspend fun remove(id: Long)
}
