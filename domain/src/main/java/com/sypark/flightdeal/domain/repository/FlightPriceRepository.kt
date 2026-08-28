package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import java.time.LocalDate
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

    /**
     * 추적 중인 여정 하나의 현재 가격.
     *
     * 등록 시점의 기준가와 이후 폴링은 **반드시 같은 규칙**으로 골라야 한다.
     * 다르게 고르면 첫 비교에서 있지도 않은 변동이 잡히고, 사용자는 알림을 받고
     * 예약처에 들어가서 가격이 그대로인 것을 본다.
     *
     * 출발일과 귀국일을 모두 맞춘다. 출발일만 맞추면 귀국일이 다른 조합이 잡혀
     * 가격이 그대로여도 매 실행마다 변동으로 읽힌다.
     */
    suspend fun trackedPrice(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
    ): AppResult<Won>
}
