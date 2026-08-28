package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import java.time.YearMonth

/**
 * 가격 조회. 구현체가 어디서 데이터를 가져오는지 도메인은 알지 않는다.
 */
interface FlightPriceRepository {

    /** 출발지 기준 특가 목록. 홈 피드용. */
    suspend fun cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>>

    /** 한 노선·한 달의 날짜별 가격. */
    suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>>

    /** 한 노선·한 달의 가격 분포. 할인율 배지의 기준. */
    suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats>
}
