package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class GetMonthCalendarUseCaseTest {

    private val incheon = Airport("ICN", "서울", "대한민국")
    private val tokyo = Airport("TYO", "도쿄", "일본")
    private val route = Route(incheon, tokyo)
    private val month = YearMonth.of(2026, 10)

    private fun quote(day: Int, price: Int) = PriceQuote(
        route = route,
        departDate = month.atDay(day),
        returnDate = month.atDay(day).plusDays(4),
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
        transfers = null,
        outboundMinutes = null,
    )

    /** calendarDeals 응답만 지정하면 되는 최소 스텁. */
    private class StubRepository(private val deals: AppResult<List<PriceQuote>>) : FlightPriceRepository {
        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) = AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType) = deals
        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    @Test
    fun `날짜마다 가장 싼 값 하나만 남는다`() = runTest {
        // 같은 날짜에 두 값이 온다. calendarDeals가 이미 하루 한 건으로 걸러주는 게
        // 정상이지만, 그 보장이 깨져도 화면이 두 칸을 그리지 않도록 UseCase도 스스로 지킨다.
        val repo = StubRepository(
            AppResult.Success(listOf(quote(12, 300_000), quote(12, 250_000))),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(1, result.data.byDate.size)
        assertEquals(Won(250_000), result.data.byDate.getValue(month.atDay(12)).price)
    }

    @Test
    fun `가장 싼 날을 찾는다`() = runTest {
        val repo = StubRepository(
            AppResult.Success(listOf(quote(5, 300_000), quote(12, 189_000), quote(20, 250_000))),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(month.atDay(12), result.data.cheapestDate)
    }

    @Test
    fun `중앙값은 PriceStats로 계산한다`() = runTest {
        val prices = listOf(quote(5, 300_000), quote(12, 189_000), quote(20, 250_000))
        val repo = StubRepository(AppResult.Success(prices))
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        val expected = PriceStats.from(prices.map { it.price })!!.median
        assertEquals(expected, result.data.median)
    }

    @Test
    fun `값이 하나도 없으면 Empty다`() = runTest {
        // 빈 응답은 오류가 아니다. 한산한 노선은 정상적으로 아무것도 주지 않는다.
        val repo = StubRepository(AppResult.Empty)
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `네트워크 오류는 그대로 전달한다`() = runTest {
        val repo = StubRepository(AppResult.NetworkError(IOException("boom")))
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP)

        assertTrue(result is AppResult.NetworkError)
    }
}
