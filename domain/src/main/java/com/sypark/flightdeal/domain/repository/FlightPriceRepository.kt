package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import java.time.YearMonth

/**
 * 가격 조회. 구현체가 어디서 데이터를 가져오는지 도메인은 알지 않는다.
 */
interface FlightPriceRepository {

    /** 출발지 기준 특가 목록. 홈 피드용. */
    suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>>

    /**
     * 한 노선·한 달의 날짜별 가격.
     *
     * @param tripType 왕복 가격을 편도 분포와 비교하면 할인율이 성립하지 않는다.
     *   비교 대상은 같은 종류의 운임이어야 한다.
     */
    suspend fun calendarPrices(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<List<PriceQuote>>

    /** 한 노선·한 달의 가격 분포. 할인율 배지의 기준. */
    suspend fun priceStats(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<PriceStats>
}
