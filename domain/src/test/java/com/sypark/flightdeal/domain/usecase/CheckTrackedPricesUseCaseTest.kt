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

    private fun tracked(id: Long = 1L, target: Won? = null) = TrackedRoute(
        id = id,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = target,
        createdAt = Instant.EPOCH,
    )

    private class StubRoutes(private val routes: List<TrackedRoute>) : TrackedRouteRepository {
        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(routes)
        override suspend fun getAll(): List<TrackedRoute> = routes
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?,
        ): Long = 1L
        override suspend fun remove(id: Long) = Unit
    }

    private class StubHistory(private val last: PriceSnapshot?) : PriceHistoryRepository {
        val appended = mutableListOf<PriceSnapshot>()
        var pruned = false

        override suspend fun append(snapshot: PriceSnapshot) { appended += snapshot }
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = last
        override fun observeHistory(trackedRouteId: Long, days: Int) = flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) { pruned = true }
    }

    private class StubPrices(
        private val result: AppResult<List<PriceQuote>>,
    ) : FlightPriceRepository {
        var seenTripType: TripType? = null

        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) =
            AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType):
            AppResult<List<PriceQuote>> {
            seenTripType = tripType
            return result
        }
        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType):
            AppResult<PriceStats> = AppResult.Empty
    }

    private fun quote(price: Int) = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
    )

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
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
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
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        // 다음 실행의 비교 대상이 된다. 남기지 않으면 매번 같은 변동을 다시 알린다.
        assertEquals(Won(280_000), history.appended.single().price)
    }

    @Test
    fun `추적 항목의 여정 종류로 조회한다`() = runTest {
        val prices = StubPrices(AppResult.Success(listOf(quote(280_000))))

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
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        assertEquals(TripType.ROUND_TRIP, history.appended.single().tripType)
    }

    @Test
    fun `가격이 그대로면 변동이 없다`() = runTest {
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            StubHistory(snapshot(300_000)),
            StubPrices(AppResult.Success(listOf(quote(300_000)))),
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
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        assertTrue(history.pruned)
    }
}
