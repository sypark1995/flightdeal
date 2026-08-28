package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.MonthCalendar
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import java.time.YearMonth
import javax.inject.Inject

/**
 * 한 노선·한 달을 캘린더 화면 한 장으로 묶는다.
 *
 * 예약 가능한 값만 쓰는 [FlightPriceRepository.calendarDeals]를 조회한다 — 화면이
 * 눌러서 결제하는 곳이라 통계용인 calendarPrices와 다른 규칙을 쓸 수 없다.
 */
class GetMonthCalendarUseCase @Inject constructor(
    private val repository: FlightPriceRepository,
) {

    suspend operator fun invoke(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<MonthCalendar> {
        return when (val deals = repository.calendarDeals(route, month, tripType)) {
            is AppResult.Success -> AppResult.Success(build(month, deals.data))
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> deals
            is AppResult.Unknown -> deals
        }
    }

    /**
     * 날짜당 하나만 남긴다. calendarDeals가 이미 그렇게 주지만, 그 보장이 깨져도
     * 화면이 같은 칸에 두 값을 그리지 않도록 여기서도 스스로 지킨다.
     */
    private fun build(month: YearMonth, quotes: List<PriceQuote>): MonthCalendar {
        val byDate = quotes
            .groupBy { it.departDate }
            .mapValues { (_, dayQuotes) -> dayQuotes.minBy { it.price.amount } }

        return MonthCalendar(
            month = month,
            byDate = byDate,
            cheapestDate = byDate.values.minByOrNull { it.price.amount }?.departDate,
            // 중앙값 계산을 새로 쓰지 않는다 — 배지 기준선과 같은 규칙을 쓴다.
            median = PriceStats.from(byDate.values.map { it.price })?.median,
        )
    }
}
