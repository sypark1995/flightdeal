package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CheckTrackedPricesUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(id: Long = 1L, target: Won? = null, notifiedPrice: Won? = null) = TrackedRoute(
        id = id,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = target,
        notifiedPrice = notifiedPrice,
        createdAt = Instant.EPOCH,
    )

    private class StubRoutes(routes: List<TrackedRoute>) : TrackedRouteRepository {
        private val state = routes.toMutableList()

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(state)
        override suspend fun getAll(): List<TrackedRoute> = state
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
        ): Long = 1L
        override suspend fun remove(id: Long) = Unit
        override suspend fun markNotified(id: Long, price: Won) {
            val index = state.indexOfFirst { it.id == id }
            if (index != -1) state[index] = state[index].copy(notifiedPrice = price)
        }
    }

    // latest()는 이미 쓴 스냅샷을 먼저 본다. 초기값 last는 등록 시점의 이력을 흉내낸다.
    private class StubHistory(private val last: PriceSnapshot?) : PriceHistoryRepository {
        val appended = mutableListOf<PriceSnapshot>()
        var pruned = false

        override suspend fun append(snapshot: PriceSnapshot) { appended += snapshot }
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? =
            appended.lastOrNull() ?: last
        override fun observeHistory(trackedRouteId: Long, days: Int) = flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) { pruned = true }
    }

    private class StubPrices(
        private val result: AppResult<Won>,
    ) : FlightPriceRepository {
        var seenDepartDate: LocalDate? = null
        var seenReturnDate: LocalDate? = null
        var seenTripType: TripType? = null

        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) =
            AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType):
            AppResult<List<PriceQuote>> = AppResult.Empty
        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType):
            AppResult<PriceStats> = AppResult.Empty
        override suspend fun trackedPrice(
            route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType,
        ): AppResult<Won> {
            seenDepartDate = departDate
            seenReturnDate = returnDate
            seenTripType = tripType
            return result
        }
    }

    private fun snapshot(price: Int, tripType: TripType = TripType.ROUND_TRIP) =
        PriceSnapshot(1L, Won(price), tripType, Instant.EPOCH)

    private fun useCase(
        routes: TrackedRouteRepository,
        history: PriceHistoryRepository,
        prices: FlightPriceRepository,
    ) = CheckTrackedPricesUseCase(routes, history, prices, DetectPriceChangesUseCase(), clock)

    @Test
    fun `가격이 내리면 변동을 돌려준다`() = runTest {
        val history = StubHistory(snapshot(300_000))
        val changes = useCase(
            // 비교는 이제 기준선(notifiedPrice)과 한다.
            StubRoutes(listOf(tracked(notifiedPrice = Won(300_000)))),
            history,
            StubPrices(AppResult.Success(Won(280_000))),
        ).invoke()

        assertEquals(1, changes.size)
        assertEquals(Direction.DOWN, changes.single().direction)
    }

    @Test
    fun `조회한 가격을 이력에 남긴다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(Won(280_000))),
        ).invoke()

        // 다음 실행의 비교 대상이 된다. 남기지 않으면 매번 같은 변동을 다시 알린다.
        assertEquals(Won(280_000), history.appended.single().price)
    }

    @Test
    fun `추적 항목의 여정 종류로 조회한다`() = runTest {
        val prices = StubPrices(AppResult.Success(Won(280_000)))

        useCase(StubRoutes(listOf(tracked())), StubHistory(snapshot(300_000)), prices).invoke()

        // 왕복 추적을 편도로 조회하면 매번 60% 하락으로 읽힌다.
        assertEquals(TripType.ROUND_TRIP, prices.seenTripType)
    }

    @Test
    fun `이력의 여정 종류도 추적 항목과 같다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(Won(280_000))),
        ).invoke()

        assertEquals(TripType.ROUND_TRIP, history.appended.single().tripType)
    }

    @Test
    fun `가격이 그대로면 변동이 없다`() = runTest {
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            StubHistory(snapshot(300_000)),
            StubPrices(AppResult.Success(Won(300_000))),
        ).invoke()

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `조회에 실패한 항목은 건너뛴다`() = runTest {
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            StubHistory(snapshot(300_000)),
            StubPrices(AppResult.NetworkError(java.io.IOException("boom"))),
        ).invoke()

        // 한 노선이 실패했다고 나머지를 포기하지 않는다.
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `추적 항목이 없으면 아무 일도 하지 않는다`() = runTest {
        val changes = useCase(
            StubRoutes(emptyList()),
            StubHistory(null),
            StubPrices(AppResult.Empty),
        ).invoke()

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `실행할 때마다 오래된 이력을 치운다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(Won(280_000))),
        ).invoke()

        assertTrue(history.pruned)
    }

    @Test
    fun `추적 항목의 여정을 그대로 조회한다`() = runTest {
        val prices = StubPrices(AppResult.Success(Won(280_000)))

        useCase(StubRoutes(listOf(tracked())), StubHistory(snapshot(300_000)), prices).invoke()

        // 등록 때와 같은 여정을 물어야 같은 규칙으로 고른 값이 온다.
        assertEquals(LocalDate.of(2026, 10, 12), prices.seenDepartDate)
        assertEquals(LocalDate.of(2026, 10, 16), prices.seenReturnDate)
        assertEquals(TripType.ROUND_TRIP, prices.seenTripType)
    }

    @Test
    fun `알림을 확인하기 전에는 기준선이 그대로다`() = runTest {
        // 300,000으로 등록된 노선. 통보 기준선(notifiedPrice)이 300,000이다.
        val routes = StubRoutes(listOf(tracked(notifiedPrice = Won(300_000))))
        val history = StubHistory(snapshot(300_000))
        val prices = StubPrices(AppResult.Success(Won(250_000)))
        val useCase = useCase(routes, history, prices)

        val first = useCase.invoke()
        // ConfirmNotifiedUseCase를 부르지 않는다 — 알림이 전달됐는지 확인하지 않은 채로
        // 다시 폴링한다.
        val second = useCase.invoke()

        assertEquals(1, first.size)
        assertEquals(Direction.DOWN, first.single().direction)
        // 기준선이 안 옮겨졌으므로 두 번째도 여전히 같은 변동이 잡혀야 한다.
        assertEquals(1, second.size)
        assertEquals(Direction.DOWN, second.single().direction)
    }

    @Test
    fun `확인한 뒤에는 같은 값에 다시 알리지 않는다`() = runTest {
        val routes = StubRoutes(listOf(tracked(notifiedPrice = Won(300_000))))
        val history = StubHistory(snapshot(300_000))
        val prices = StubPrices(AppResult.Success(Won(250_000)))
        val useCase = useCase(routes, history, prices)

        val first = useCase.invoke()
        ConfirmNotifiedUseCase(routes).invoke(first)
        val second = useCase.invoke()

        // 기준선이 250,000으로 옮겨졌으니 같은 값과는 더 이상 비교에서 변동이 아니다.
        assertTrue(second.isEmpty())
    }

    @Test
    fun `관측 이력은 통보와 무관하게 쌓인다`() = runTest {
        val routes = StubRoutes(listOf(tracked(notifiedPrice = Won(300_000))))
        val history = StubHistory(snapshot(300_000))
        val prices = StubPrices(AppResult.Success(Won(250_000)))
        val useCase = useCase(routes, history, prices)

        // ConfirmNotifiedUseCase를 부르지 않는다.
        useCase.invoke()
        useCase.invoke()

        // 그래프가 알림 성공 여부에 인질로 잡히면 안 된다.
        assertEquals(2, history.appended.size)
    }
}
