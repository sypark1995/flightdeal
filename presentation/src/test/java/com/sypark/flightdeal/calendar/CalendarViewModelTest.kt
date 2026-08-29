package com.sypark.flightdeal.calendar

import app.cash.turbine.test
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.CalendarDeals
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.SettingsRepository
import com.sypark.flightdeal.domain.usecase.GetMonthCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // 2026-08-29로 고정한다. YearMonth.now()를 직접 부르면 실행 시각마다 기본 달이
    // 달라져 테스트가 흔들린다.
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val incheon = Airport.INCHEON
    private val tokyo = Airport.DESTINATIONS[0]
    private val bangkok = Airport.DESTINATIONS[1]

    private fun quote(destination: Airport, day: Int, price: Int, month: YearMonth = YearMonth.of(2026, 8)) =
        PriceQuote(
            route = Route(incheon, destination),
            departDate = month.atDay(day),
            returnDate = month.atDay(day).plusDays(4),
            price = Won(price),
            airline = "대한항공",
            foundAt = Instant.EPOCH,
            deepLink = "https://example.com/booking/${destination.iata}/$day",
            transfers = null,
            outboundMinutes = null,
        )

    /** 목적지별로 다른 값을 돌려주고, 호출 횟수를 센다. */
    private inner class CountingRepository : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) = AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty

        override suspend fun calendarDeals(
            route: Route,
            month: YearMonth,
            tripType: TripType,
        ): AppResult<CalendarDeals> {
            calls++
            return AppResult.Success(CalendarDeals(listOf(quote(route.destination, 15, 200_000, month)), emptySet()))
        }

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    /** 첫 호출만 느리게 만들고, 호출할 때마다 다른 목적지 결과를 돌려준다. */
    private inner class SlowFirstRepository : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) = AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty

        override suspend fun calendarDeals(
            route: Route,
            month: YearMonth,
            tripType: TripType,
        ): AppResult<CalendarDeals> {
            val current = ++calls
            delay(if (current == 1) 1_000L else 10L)
            return AppResult.Success(
                CalendarDeals(listOf(quote(route.destination, 15, 100_000 * current, month)), emptySet()),
            )
        }

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    private class EmptyRepository : FlightPriceRepository {
        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) = AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType) = AppResult.Empty
        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    private fun viewModel(repository: FlightPriceRepository, settings: SettingsRepository = FakeSettingsRepository()) =
        CalendarViewModel(GetMonthCalendarUseCase(repository), fixedClock, settings)

    /** [Airport.observeOrigin]을 즉시 흘려보내는 스텁. 기본은 인천이다. */
    private class FakeSettingsRepository(
        initial: Airport = Airport.INCHEON,
    ) : SettingsRepository {
        private val origin = MutableStateFlow(initial)
        override fun observeOrigin(): Flow<Airport> = origin
        override suspend fun setOrigin(origin: Airport) {
            this.origin.value = origin
        }
    }

    @Test
    fun `목적지를 바꾸면 다시 조회한다`() = runTest {
        val repo = CountingRepository()
        val viewModel = viewModel(repo)
        advanceUntilIdle()
        val before = repo.calls

        viewModel.selectDestination(bangkok)
        advanceUntilIdle()

        assertEquals(bangkok, viewModel.destination.value)
        assertEquals(before + 1, repo.calls)
    }

    @Test
    fun `값이 없으면 Empty다`() = runTest {
        val viewModel = viewModel(EmptyRepository())

        viewModel.uiState.test {
            assertEquals(CalendarUiState.Loading, awaitItem())
            assertEquals(CalendarUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `조회 중이던 요청은 취소한다`() = runTest {
        val repo = SlowFirstRepository()
        val viewModel = viewModel(repo)

        // 첫 요청이 실제로 시작되어 응답을 기다리는 상태까지 진행시킨다.
        advanceTimeBy(100)
        assertEquals(1, repo.calls)

        // 진행 중이던 첫 요청은 여기서 취소되고 버려져야 한다.
        viewModel.selectDestination(bangkok)
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        val state = viewModel.uiState.value as CalendarUiState.Success
        // 취소가 없으면 느린 첫 요청(100,000원, 도쿄)이 나중에 끝나 이 값을 덮어쓴다.
        assertEquals(200_000, state.calendar.byDate.values.single().price.amount)
    }

    @Test
    fun `기본 목적지는 도쿄다`() = runTest {
        val viewModel = viewModel(CountingRepository())

        assertEquals(tokyo, viewModel.destination.value)
    }

    @Test
    fun `기본 여정 종류는 왕복이다`() = runTest {
        // 딜 피드와 기본값이 다르면 같은 날짜가 화면마다 다른 값으로 보인다.
        val viewModel = viewModel(CountingRepository())

        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)
    }

    @Test
    fun `처음 열면 딜 피드와 같은 달을 본다`() = runTest {
        // 이번 달로 열면 월말에 격자가 거의 비어 "가격 정보가 없는 앱"으로 보인다.
        // 딜 피드가 보는 두 달 뒤와 같은 달에서 시작해야 처음 연 화면이 차 있다.
        val viewModel = viewModel(CountingRepository())

        assertEquals(YearMonth.of(2026, 10), viewModel.month.value)
    }

    @Test
    fun `이번 달까지는 되돌아갈 수 있다`() = runTest {
        // 기본값은 두 달 뒤(10월)지만, 더 가까운 날짜를 보고 싶을 수 있다.
        // 실제 이번 달(8월)까지는 내려갈 수 있어야 하고, 그보다 과거로는 못 간다.
        val repo = CountingRepository()
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        viewModel.previousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), viewModel.month.value)

        viewModel.previousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 8), viewModel.month.value)

        val before = repo.calls
        viewModel.previousMonth()
        advanceUntilIdle()

        // 이번 달(8월)보다 과거로는 못 간다.
        assertEquals(YearMonth.of(2026, 8), viewModel.month.value)
        assertEquals(before, repo.calls)
    }

    @Test
    fun `다음 달로는 이동한다`() = runTest {
        val repo = CountingRepository()
        val viewModel = viewModel(repo)
        advanceUntilIdle()
        val before = repo.calls

        viewModel.nextMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 11), viewModel.month.value)
        assertEquals(before + 1, repo.calls)
    }

    @Test
    fun `오늘은 주입된 시계를 따른다`() = runTest {
        // LocalDate.now()를 화면이 직접 부르면 이 ViewModel과 다른 시계를 보게 될 수
        // 있다 — today는 반드시 생성자로 받은 clock에서 나와야 한다.
        val viewModel = viewModel(CountingRepository())

        assertEquals(LocalDate.of(2026, 8, 29), viewModel.today)
    }

    @Test
    fun `예약 페이지를 열 수 없으면 안내 메시지를 보낸다`() = runTest {
        val viewModel = viewModel(CountingRepository())

        viewModel.messages.test {
            viewModel.bookingUnavailable()

            assertEquals("예약 페이지를 열 수 있는 앱이 없어요", awaitItem())
        }
    }

    @Test
    fun `저장된 출발지로 처음 조회한다`() = runTest {
        // 인천이 아니라 저장된 값(부산)으로 첫 조회가 나가야 한다 — 기본값으로 한 번
        // 조회했다가 저장된 값으로 다시 조회하면 화면이 한 번 더 깜빡인다.
        val busan = Airport("PUS", "부산", "대한민국")
        val settings = FakeSettingsRepository(initial = busan)
        val viewModel = viewModel(CountingRepository(), settings)
        advanceUntilIdle()

        assertEquals(busan, viewModel.origin.value)
    }

    @Test
    fun `출발지가 바뀌면 다시 조회한다`() = runTest {
        // 피드에서 출발지를 바꾸면 그 값이 SettingsRepository를 통해 이 ViewModel에도
        // 흘러들어온다. 캘린더가 이걸 무시하면 같은 노선인데 화면마다 다른 출발지의
        // 가격을 보여주게 된다.
        val repo = CountingRepository()
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(repo, settings)
        advanceUntilIdle()
        val before = repo.calls

        val busan = Airport("PUS", "부산", "대한민국")
        settings.setOrigin(busan)
        advanceUntilIdle()

        assertEquals(busan, viewModel.origin.value)
        assertEquals(before + 1, repo.calls)
    }
}
