package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.CalendarDeals
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
    private class StubRepository(private val deals: AppResult<CalendarDeals>) : FlightPriceRepository {
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
            AppResult.Success(CalendarDeals(listOf(quote(12, 300_000), quote(12, 250_000)), emptySet())),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(1, result.data.byDate.size)
        assertEquals(Won(250_000), result.data.byDate.getValue(month.atDay(12)).price)
    }

    @Test
    fun `가장 싼 날을 찾는다`() = runTest {
        val repo = StubRepository(
            AppResult.Success(
                CalendarDeals(listOf(quote(5, 300_000), quote(12, 189_000), quote(20, 250_000)), emptySet()),
            ),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(month.atDay(12), result.data.cheapestDate)
    }

    @Test
    fun `중앙값은 PriceStats로 계산한다`() = runTest {
        val prices = listOf(quote(5, 300_000), quote(12, 189_000), quote(20, 250_000))
        val repo = StubRepository(AppResult.Success(CalendarDeals(prices, emptySet())))
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        val expected = PriceStats.from(prices.map { it.price })!!.median
        assertEquals(expected, result.data.median)
    }

    @Test
    fun `중앙값은 예약 가능한 가격만으로 계산한다`() = runTest {
        // unbookableDates에는 가격이 없다 — 도메인은 "이 날은 살 수 없다"는 사실만
        // 안다. cheapestDate·median은 deals에서만 나와야 한다. 살 수 없는 날이 섞여
        // 들면 배지와 강조가 사용자가 누를 수 없는 값에 끌려간다.
        val deals = listOf(quote(5, 300_000), quote(12, 189_000), quote(20, 250_000))
        val unbookable = setOf(month.atDay(1), month.atDay(15))
        val repo = StubRepository(AppResult.Success(CalendarDeals(deals, unbookable)))
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        val expectedMedian = PriceStats.from(deals.map { it.price })!!.median
        assertEquals(expectedMedian, result.data.median)
        assertEquals(month.atDay(12), result.data.cheapestDate)
        assertEquals(3, result.data.byDate.size)
    }

    @Test
    fun `요청한 달 밖의 unbookable 날짜는 걸러낸다`() = runTest {
        // byDate를 요청한 달로 거르는 것과 같은 이유다. 걸러내지 않으면 다음 달
        // 예약 불가 날짜가 이번 달 격자에 "—"로 잘못 그려질 수 있다.
        val inMonth = month.atDay(6)
        val outOfMonth = month.plusMonths(1).atDay(3)
        val repo = StubRepository(
            AppResult.Success(
                CalendarDeals(deals = listOf(quote(12, 250_000)), unbookableDates = setOf(inMonth, outOfMonth)),
            ),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(setOf(inMonth), result.data.unbookableDates)
    }

    @Test
    fun `달 밖의 견적은 byDate·cheapestDate·median 어디에도 반영되지 않는다`() = runTest {
        // 지금은 calendarDeals가 항상 요청한 달의 데이터만 주므로 실제로는 일어나지
        // 않는다. 그 보장이 깨졌을 때 UseCase가 스스로 걸러내는지를 확인하는
        // 방어적 테스트다 — 안 걸러지면 다음 달 견적이 이번 달 중앙값에 몰래 투표하고,
        // cheapestDate가 MonthGrid에 그려지지도 않는 날짜를 가리킬 수 있다.
        // 값을 훨씬 싸게 둔다 — 걸러내지 못하면 cheapestDate가 이 날짜를 가리키고
        // median도 이 값에 끌려가야 정상인데, 그렇게 되면 테스트가 실패해 버그를 드러낸다.
        val outOfMonth = quote(1, 10_000).copy(departDate = month.plusMonths(1).atDay(1))
        val repo = StubRepository(
            AppResult.Success(CalendarDeals(listOf(quote(12, 250_000), outOfMonth), emptySet())),
        )
        val useCase = GetMonthCalendarUseCase(repo)

        val result = useCase(route, month, TripType.ROUND_TRIP) as AppResult.Success

        assertEquals(1, result.data.byDate.size)
        assertEquals(month.atDay(12), result.data.cheapestDate)
        assertEquals(Won(250_000), result.data.median)
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
