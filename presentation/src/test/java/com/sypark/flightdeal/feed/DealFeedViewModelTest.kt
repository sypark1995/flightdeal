package com.sypark.flightdeal.feed

import app.cash.turbine.test
import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.CalendarDeals
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
import com.sypark.flightdeal.domain.repository.SettingsRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.CalculateDiscountUseCase
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import com.sypark.flightdeal.domain.usecase.TrackRouteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            settings = FakeSettingsRepository(),
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

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals> =
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

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals> =
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

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals> =
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
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()), FakeSettingsRepository())

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
            FakeSettingsRepository(),
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
            FakeSettingsRepository(),
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
            AppResult<CalendarDeals> = AppResult.Empty

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
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()), FakeSettingsRepository())
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
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()), TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()), FakeSettingsRepository())
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
            settings = FakeSettingsRepository(),
        )
        advanceUntilIdle()

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()
        // 전환이 끝난 뒤 화면이 실제로 보여주는 딜을 잡는다 — 전환 전에 잡아두면
        // 여전히 왕복 견적을 들고 있어, 이 테스트가 검증하려는 "화면과 등록이
        // 일치하는지"가 아니라 다른 것(추적 중 화면이 바뀌는 경우, 아래 별도
        // 테스트가 다룬다)을 검증하게 된다.
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()
        viewModel.track(deal)
        advanceUntilIdle()

        // 화면이 편도를 보여주는데 왕복으로 등록되면 이후 비교가 전부 어긋난다.
        assertEquals(TripType.ONE_WAY, routes.lastTripType)
    }

    /**
     * 첫 호출은 즉시(라고 봐도 될 만큼 짧게) 끝나고, 이후 호출은 [pendingDelayMs]만큼
     * 걸린다. 조회 중인 상태를 만들어두고 그 사이 [track]을 호출하는 테스트 전용이다.
     */
    private inner class SwitchableTripTypeRepository(
        var pendingDelayMs: Long = 1_000L,
    ) : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> {
            val isFirstCall = ++calls == 1
            if (!isFirstCall) delay(pendingDelayMs)
            val roundTrip = quote(189_000).copy(returnDate = LocalDate.of(2026, 10, 19))
            val q = if (tripType == TripType.ONE_WAY) roundTrip.copy(returnDate = null) else roundTrip
            return AppResult.Success(listOf(q))
        }

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals> =
            AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats> =
            AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    @Test
    fun `조회 중에 추적하면 화면의 견적이 실제로 어떤 종류인지로 등록한다`() = runTest {
        val repo = SwitchableTripTypeRepository()
        val routes = RecordingTrackedRoutes()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repo, CalculateDiscountUseCase()),
            TrackRouteUseCase(routes, NoopHistory()),
            FakeSettingsRepository(),
        )
        // 첫 조회(왕복)를 끝까지 진행시켜 화면에 왕복 카드가 뜨게 한다.
        advanceUntilIdle()
        val displayedDeal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()
        // 화면에 남아 있는 카드는 실제로 왕복 견적이어야 한다 — 이 테스트의 전제다.
        assertTrue(displayedDeal.quote.returnDate != null)

        // 편도로 토글한다. 토글은 즉시 바뀌지만, 조회가 오래 걸려 화면에는
        // 여전히 위에서 잡아둔 왕복 카드가 그대로 남아 있다.
        viewModel.setTripType(TripType.ONE_WAY)
        advanceTimeBy(100)
        assertEquals(TripType.ONE_WAY, viewModel.tripType.value)
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)

        // 조회가 끝나기 전에, 아직 화면에 떠 있는 왕복 카드를 추적한다.
        viewModel.track(displayedDeal)
        advanceUntilIdle()

        // 토글은 이미 편도지만, 실제로 추적한 카드는 왕복 견적이었다.
        assertEquals(TripType.ROUND_TRIP, routes.lastTripType)
    }

    @Test
    fun `연속으로 토글한 뒤 실패하면 화면의 데이터에 맞는 종류로 되돌린다`() = runTest {
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        // 왕복으로 성공한 목록이 화면에 떠 있다.
        advanceUntilIdle()
        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)

        // 편도로 눌렀다가, 그 요청이 끝나기 전에 다시 왕복으로 누른다. 이후 요청은
        // 계속 실패한다 — 화면에 남은 목록은 처음의 왕복 그대로다.
        repository.nextResult = AppResult.NetworkError(IOException())
        viewModel.setTripType(TripType.ONE_WAY)
        viewModel.setTripType(TripType.ROUND_TRIP)
        advanceUntilIdle()

        // "마지막으로 탭한 값 이전"(편도)이 아니라, 화면에 실제로 남아 있는
        // 데이터의 종류(왕복)로 되돌아가야 한다.
        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)
    }

    @Test
    fun `여정 종류를 바꾸지 않으면 왕복으로 등록한다`() = runTest {
        val routes = RecordingTrackedRoutes()
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(
                FakeFlightPriceRepository(), CalculateDiscountUseCase()
            ),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
            settings = FakeSettingsRepository(),
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
        override suspend fun setTargetPrice(id: Long, target: Won?) = Unit
    }

    private class NoopHistory : PriceHistoryRepository {
        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = null
        override fun observeHistory(trackedRouteId: Long, days: Int) =
            flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) = Unit
        override fun observeCount(): Flow<Int> = flowOf(0)
        override suspend fun clearAll() = Unit
    }

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
        override suspend fun setTargetPrice(id: Long, target: Won?) = Unit
    }

    @Test
    fun `추적에 성공하고 새로 등록됐으면 시작했다고 알린다`() = runTest {
        val routes = RecordingTrackedRoutes().apply { isNew = true }
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
            settings = FakeSettingsRepository(),
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
            settings = FakeSettingsRepository(),
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
            settings = FakeSettingsRepository(),
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
            settings = FakeSettingsRepository(),
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

    /**
     * 호출마다 원하는 결과로 바꿔가며 검증할 때 쓰는 스텁. 두 번째 조회부터
     * 첫 조회와 다른 결과(성공→실패 등)를 돌려줘야 하는 테스트 전용이다.
     *
     * 지연 없이 즉시 값을 돌려주면 Loading이 StateFlow의 conflation으로
     * 관찰되기 전에 다음 값에 덮여, 구현이 Loading을 실제로 건너뛰었는지와
     * 무관하게 테스트가 항상 통과해버린다. SlowFirstRepository와 같은 이유로
     * delay를 둔다.
     */
    private inner class SwitchableRepository(
        var nextResult: AppResult<List<PriceQuote>> = AppResult.Success(listOf(quote(189_000))),
    ) : FlightPriceRepository {

        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> {
            delay(10L)
            return nextResult
        }

        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>> =
            AppResult.Empty

        override suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals> =
            AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats> =
            AppResult.Empty

        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> = AppResult.Empty
    }

    @Test
    fun `새로고침이 실패해도 보던 목록을 지우지 않는다`() = runTest {
        // 첫 조회는 성공해서 목록이 떠 있다.
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)

        // 두 번째 조회가 네트워크 오류로 실패한다.
        repository.nextResult = AppResult.NetworkError(IOException())
        viewModel.refresh()
        advanceUntilIdle()

        // 목록은 그대로 남아야 한다. 오류는 스낵바로만 알린다.
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)
    }

    @Test
    fun `첫 조회가 실패하면 오류 화면을 보여준다`() = runTest {
        // 보여줄 것이 없으면 오류 화면이 맞다. 빈 화면보다 낫다.
        val repository = SwitchableRepository(nextResult = AppResult.NetworkError(IOException()))
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is DealFeedUiState.Error)
    }

    @Test
    fun `목록이 떠 있으면 새로고침 중에 Loading으로 되돌리지 않는다`() = runTest {
        // Loading으로 바꾸면 스켈레톤이 떴다가 목록이 돌아온다 — 깜빡인다.
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )

        viewModel.uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())
            assertTrue(awaitItem() is DealFeedUiState.Success)

            repository.nextResult = AppResult.Success(listOf(quote(200_000)))
            viewModel.refresh()

            // 다음으로 받는 값이 Loading이면 깜빡임이 있다는 뜻이다.
            assertTrue(awaitItem() is DealFeedUiState.Success)
        }
    }

    @Test
    fun `실패를 스낵바로 알린다`() = runTest {
        // 목록을 남겨두기만 하고 아무 말도 안 하면 사용자는 갱신된 줄 안다.
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)

        viewModel.messages.test {
            repository.nextResult = AppResult.NetworkError(IOException())
            viewModel.refresh()

            assertEquals("가격을 새로 받아오지 못했어요", awaitItem())
        }
    }

    @Test
    fun `여정 종류 변경이 실패하면 토글도 되돌린다`() = runTest {
        // 토글만 바뀌고 목록은 이전 종류 그대로 남으면, 화면이 "편도"라고 말하면서
        // 왕복 가격을 보여준다. 그 상태에서 추적을 누르면 왕복 견적이 편도로 저장돼
        // 이후 변동 판정이 전부 어긋난다.
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        advanceUntilIdle()
        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)

        repository.nextResult = AppResult.NetworkError(IOException())
        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()

        // 목록이 왕복 그대로 남았으니 토글도 왕복이어야 한다.
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)
        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)
    }

    @Test
    fun `여정 종류 변경이 성공하면 토글은 새 값을 유지한다`() = runTest {
        val repository = SwitchableRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repository, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(),
        )
        advanceUntilIdle()

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()

        assertEquals(TripType.ONE_WAY, viewModel.tripType.value)
    }

    @Test
    fun `아무도 화면을 보고 있지 않을 때 보낸 메시지는 나중에 구독해도 다시 나타나지 않는다`() = runTest {
        // 리뷰가 지적한 실제 결함: Channel(BUFFERED)는 구독자가 없어도 메시지를
        // 버퍼에 쌓아뒀다가, 나중에 새로 구독하는 순간(탭을 떠났다 돌아오는 것과
        // 같다) 그 오래된 메시지를 그대로 내보낸다. 위 "회전 뒤" 테스트와 다르게,
        // 여기서는 메시지를 보내는 시점에 구독자가 아예 없다는 게 핵심이다.
        val routes = RecordingTrackedRoutes().apply { isNew = true }
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(FakeFlightPriceRepository(), CalculateDiscountUseCase()),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
            settings = FakeSettingsRepository(),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        // 아무도 messages를 구독하지 않은 채로 메시지를 보낸다.
        viewModel.track(deal)
        advanceUntilIdle()

        // 이제서야 구독한다 — 탭을 갔다가 돌아온 상황을 흉내낸다. 아무것도 안 보여야 한다.
        viewModel.messages.test {
            expectNoEvents()
        }
    }

    @Test
    fun `예약 페이지를 열 수 없으면 안내 메시지를 보낸다`() = runTest {
        val viewModel = viewModel(FakeFlightPriceRepository.Behavior.Normal)

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
        val repo = CountingRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repo, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            FakeSettingsRepository(initial = busan),
        )
        advanceUntilIdle()

        assertEquals(busan, viewModel.origin.value)
        assertEquals(1, repo.calls)
    }

    @Test
    fun `출발지가 바뀌면 다시 조회한다`() = runTest {
        // 달력에서 출발지를 바꿔도 SettingsRepository를 통해 이 ViewModel에 흘러들어와야
        // 한다. 이걸 무시하면 같은 노선인데 화면마다 다른 출발지의 가격을 보여준다.
        val repo = CountingRepository()
        val settings = FakeSettingsRepository()
        val viewModel = DealFeedViewModel(
            GetDealFeedUseCase(repo, CalculateDiscountUseCase()),
            TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory()),
            settings,
        )
        advanceUntilIdle()
        val before = repo.calls

        val busan = Airport("PUS", "부산", "대한민국")
        settings.setOrigin(busan)
        advanceUntilIdle()

        assertEquals(busan, viewModel.origin.value)
        assertEquals(before + 1, repo.calls)
    }
}
