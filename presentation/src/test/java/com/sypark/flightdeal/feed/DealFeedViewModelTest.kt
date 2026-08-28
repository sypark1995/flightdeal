package com.sypark.flightdeal.feed

import app.cash.turbine.test
import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.CalculateDiscountUseCase
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import com.sypark.flightdeal.domain.usecase.TrackRouteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class DealFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(behavior: FakeFlightPriceRepository.Behavior) =
        DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(behavior), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
        )

    @Test
    fun `로딩으로 시작해 성공으로 끝난다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Normal).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())

            val loaded = awaitItem()
            assertTrue(loaded is DealFeedUiState.Success)
            assertTrue((loaded as DealFeedUiState.Success).deals.isNotEmpty())
        }
    }

    @Test
    fun `빈 데이터는 Empty 상태가 된다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.EmptyData).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())
            assertEquals(DealFeedUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `네트워크 오류는 재시도 가능한 Error 상태가 된다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Failing).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())

            val error = awaitItem()
            assertTrue(error is DealFeedUiState.Error)
            assertTrue((error as DealFeedUiState.Error).retryable)
        }
    }

    @Test
    fun `할인 배지가 붙은 딜이 하나 이상 있다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Normal).uiState.test {
            awaitItem() // Loading
            val loaded = awaitItem() as DealFeedUiState.Success

            assertTrue(loaded.deals.any { it.discountPercent != null })
        }
    }

    @Test
    fun `인증 실패는 재시도 불가 오류가 된다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Unauthorized).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())

            val state = awaitItem()
            assertTrue(state is DealFeedUiState.Error)
            // 토큰이 잘못됐으면 몇 번을 눌러도 같다. 재시도 버튼을 주면 안 된다.
            assertFalse((state as DealFeedUiState.Error).retryable)
        }
    }

    private val tokyo = Airport("TYO", "도쿄", "일본")
    private val route = Route(Airport.INCHEON, tokyo)

    private fun quote(price: Int) = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = null,
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
        transfers = null,
        outboundMinutes = null,
    )

    /** 첫 호출만 느리게 만들고, 호출할 때마다 다른 가격을 돌려준다. */
    private inner class SlowFirstRepository : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> {
            val current = ++calls
            delay(if (current == 1) 1_000L else 10L)
            return AppResult.Success(listOf(quote(100_000 * current)))
        }

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats> =
            AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    private class ThrowingRepository : FlightPriceRepository {
        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> = throw IOException("boom")

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats> =
            AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    private class UnknownErrorRepository : FlightPriceRepository {
        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> = AppResult.Unknown(IllegalStateException("boom"))

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats> =
            AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    @Test
    fun `재시도를 연타하면 이전 요청은 취소되고 마지막 결과만 반영된다`() = runTest {
        val repo = SlowFirstRepository()
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()))

        // 첫 요청이 실제로 시작되어 응답을 기다리는 상태까지 진행시킨다.
        // 이 단계를 빼면 첫 작업이 디스패치 전에 취소되어 조회가 한 번만 일어난다.
        advanceTimeBy(100)
        assertEquals(1, repo.calls)

        // 진행 중이던 첫 요청은 여기서 버려진다.
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        val state = viewModel.uiState.value as DealFeedUiState.Success
        // 취소가 없으면 느린 첫 요청(100,000원)이 나중에 끝나 이 값을 덮어쓴다.
        assertEquals(200_000, state.deals.single().quote.price.amount)
    }

    @Test
    fun `Repository가 예외를 던져도 앱이 죽지 않고 재시도 불가 오류가 된다`() = runTest {
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(ThrowingRepository(), CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DealFeedUiState.Error)
        assertFalse((state as DealFeedUiState.Error).retryable)
    }

    @Test
    fun `알 수 없는 오류는 재시도 불가 오류가 된다`() = runTest {
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(UnknownErrorRepository(), CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DealFeedUiState.Error)
        assertFalse((state as DealFeedUiState.Error).retryable)
    }

    private inner class CountingRepository : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> {
            calls++
            return AppResult.Success(listOf(quote(189_000)))
        }

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType):
            AppResult<List<PriceQuote>> = AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType):
            AppResult<List<PriceQuote>> = AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType):
            AppResult<PriceStats> = AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    @Test
    fun `기본은 왕복이다`() = runTest {
        val viewModel = viewModel(FakeFlightPriceRepository.Behavior.Normal)

        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)
    }

    @Test
    fun `여정 종류를 바꾸면 다시 조회한다`() = runTest {
        val repo = CountingRepository()
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()))
        advanceUntilIdle()
        val before = repo.calls

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()

        assertEquals(TripType.ONE_WAY, viewModel.tripType.value)
        assertEquals(before + 1, repo.calls)
    }

    @Test
    fun `같은 여정 종류를 다시 고르면 조회하지 않는다`() = runTest {
        val repo = CountingRepository()
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()))
        advanceUntilIdle()
        val before = repo.calls

        viewModel.setTripType(TripType.ROUND_TRIP)
        advanceUntilIdle()

        assertEquals(before, repo.calls)
    }

    @Test
    fun `딜을 추적하면 현재 여정 종류로 등록한다`() = runTest {
        val routes = RecordingTrackedRoutes()
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(
                FakeFlightPriceRepository(), CalculateDiscountUseCase()
            ),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()
        viewModel.track(deal)
        advanceUntilIdle()

        // 화면이 편도를 보여주는데 왕복으로 등록되면 이후 비교가 전부 어긋난다.
        assertEquals(TripType.ONE_WAY, routes.lastTripType)
    }

    @Test
    fun `여정 종류를 바꾸지 않으면 왕복으로 등록한다`() = runTest {
        val routes = RecordingTrackedRoutes()
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(
                FakeFlightPriceRepository(), CalculateDiscountUseCase()
            ),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.track(deal)
        advanceUntilIdle()

        // 이 테스트가 없으면 항상 ONE_WAY를 넘기는 구현도 통과한다.
        assertEquals(TripType.ROUND_TRIP, routes.lastTripType)
    }

    private class RecordingTrackedRoutes : TrackedRouteRepository {
        var lastTripType: TripType? = null
        var isNew = true

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(emptyList())
        override suspend fun getAll(): List<TrackedRoute> = emptyList()
        override suspend fun add(
            route: Route,
            departDate: LocalDate,
            returnDate: LocalDate?,
            tripType: TripType,
            targetPrice: Won?,
            notifiedPrice: Won?,
        ): TrackRegistration {
            lastTripType = tripType
            return TrackRegistration(1L, isNew)
        }
        override suspend fun remove(id: Long) = Unit
        override suspend fun markNotified(id: Long, price: Won) = Unit
    }

    private class NoopHistory : PriceHistoryRepository {
        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = null
        override fun observeHistory(trackedRouteId: Long, days: Int) =
            flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    private class ThrowingTrackedRoutes : TrackedRouteRepository {
        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(emptyList())
        override suspend fun getAll(): List<TrackedRoute> = emptyList()
        override suspend fun add(
            route: Route,
            departDate: LocalDate,
            returnDate: LocalDate?,
            tripType: TripType,
            targetPrice: Won?,
            notifiedPrice: Won?,
        ): TrackRegistration = throw IOException("boom")
        override suspend fun remove(id: Long) = Unit
        override suspend fun markNotified(id: Long, price: Won) = Unit
    }

    @Test
    fun `추적에 성공하고 새로 등록됐으면 시작했다고 알린다`() = runTest {
        val routes = RecordingTrackedRoutes().apply { isNew = true }
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.messages.test {
            viewModel.track(deal)

            assertEquals("추적을 시작했어요", awaitItem())
        }
    }

    @Test
    fun `이미 추적 중인 딜을 다시 누르면 이미 추적 중이라고 알린다`() = runTest {
        val routes = RecordingTrackedRoutes().apply { isNew = false }
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.messages.test {
            viewModel.track(deal)

            assertEquals("이미 추적 중이에요", awaitItem())
        }
    }

    @Test
    fun `추적에 실패하면 실패했다고 알린다`() = runTest {
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(ThrowingTrackedRoutes(), NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.messages.test {
            viewModel.track(deal)

            assertEquals("추적을 시작하지 못했어요", awaitItem())
        }
    }

    @Test
    fun `메시지는 회전 뒤 다시 보이지 않는다`() = runTest {
        // StateFlow였다면 마지막 메시지를 다시 replay해 방금 튀어나온 것처럼 보인다.
        // Channel은 한 번 받으면 사라지므로 새 구독자(회전 뒤 재구독을 흉내낸다)는
        // 과거 메시지를 못 본다.
        val routes = RecordingTrackedRoutes().apply { isNew = true }
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        // 첫 구독(회전 전 화면)이 메시지를 받고 끝난다.
        viewModel.messages.test {
            viewModel.track(deal)
            assertEquals("추적을 시작했어요", awaitItem())
        }

        // 회전으로 다시 구독한 새 화면은 지난 메시지를 다시 보면 안 된다.
        viewModel.messages.test {
            expectNoEvents()
        }
    }
}
