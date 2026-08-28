package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TrackRouteUseCaseTest {

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private val quote = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(304_619),
        airline = "제주항공",
        foundAt = Instant.parse("2026-08-28T00:00:00Z"),
        deepLink = null,
    )

    private class FakeTrackedRoutes : TrackedRouteRepository {
        var lastRoute: Route? = null
        var lastDepartDate: LocalDate? = null
        var lastReturnDate: LocalDate? = null
        var lastTripType: TripType? = null
        var lastTargetPrice: Won? = null
        var removed: Long? = null
        private val added = mutableListOf<TrackedRoute>()
        private var nextId = 42L

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(added)
        override suspend fun getAll(): List<TrackedRoute> = added
        override suspend fun add(
            route: Route,
            departDate: LocalDate,
            returnDate: LocalDate?,
            tripType: TripType,
            targetPrice: Won?,
            notifiedPrice: Won?,
        ): TrackRegistration {
            lastRoute = route
            lastDepartDate = departDate
            lastReturnDate = returnDate
            lastTripType = tripType
            lastTargetPrice = targetPrice

            // 실제 저장소처럼 같은 노선/날짜/여정 종류로 다시 부르면 새로 만들지 않고
            // 기존 항목의 id를 돌려준다.
            val existing = added.firstOrNull {
                it.route == route && it.departDate == departDate &&
                    it.returnDate == returnDate && it.tripType == tripType
            }
            if (existing != null) return TrackRegistration(existing.id, isNew = false)

            val id = nextId++
            added += TrackedRoute(
                id = id,
                route = route,
                departDate = departDate,
                returnDate = returnDate,
                tripType = tripType,
                targetPrice = targetPrice,
                notifiedPrice = notifiedPrice,
                createdAt = Instant.EPOCH,
            )
            return TrackRegistration(id, isNew = true)
        }
        override suspend fun remove(id: Long) { removed = id }
        override suspend fun markNotified(id: Long, price: Won) = Unit
    }

    private class FakeHistory : PriceHistoryRepository {
        val appended = mutableListOf<PriceSnapshot>()

        override suspend fun append(snapshot: PriceSnapshot) { appended += snapshot }
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = appended.lastOrNull()
        override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
            flowOf(appended)
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    @Test
    fun `등록하면 추적 항목의 id를 돌려준다`() = runTest {
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), FakeHistory())

        assertEquals(42L, useCase(quote, TripType.ROUND_TRIP).id)
    }

    @Test
    fun `등록 즉시 첫 스냅샷을 남긴다`() = runTest {
        val history = FakeHistory()
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), history)

        useCase(quote, TripType.ROUND_TRIP)

        // 첫 스냅샷이 없으면 6시간 뒤 워커가 돌 때까지 비교 대상이 없다.
        val snapshot = history.appended.single()
        assertEquals(42L, snapshot.trackedRouteId)
        assertEquals(Won(304_619), snapshot.price)
        assertEquals(quote.foundAt, snapshot.capturedAt)
    }

    @Test
    fun `첫 스냅샷도 요청한 여정 종류로 남는다`() = runTest {
        val history = FakeHistory()
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), history)

        useCase(quote, TripType.ONE_WAY)

        // 종류가 어긋나면 다음 조회에서 가짜 하락으로 읽힌다.
        assertEquals(TripType.ONE_WAY, history.appended.single().tripType)
    }

    @Test
    fun `요청한 여정 종류로 등록한다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = TrackRouteUseCase(routes, FakeHistory())

        useCase(quote, TripType.ONE_WAY)

        assertEquals(TripType.ONE_WAY, routes.lastTripType)
    }

    @Test
    fun `시세의 노선과 날짜, 목표가를 그대로 넘긴다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = TrackRouteUseCase(routes, FakeHistory())

        useCase(quote, TripType.ROUND_TRIP, targetPrice = Won(280_000))

        assertEquals(quote.route, routes.lastRoute)
        assertEquals(quote.departDate, routes.lastDepartDate)
        assertEquals(quote.returnDate, routes.lastReturnDate)
        // 목표가는 목표 도달 알림의 근거다. 흘리면 알림이 영영 안 온다.
        assertEquals(Won(280_000), routes.lastTargetPrice)
    }

    @Test
    fun `해제하면 저장소에서 지운다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = UntrackRouteUseCase(routes)

        useCase(7L)

        // 이력은 외래키 CASCADE가 함께 지운다.
        assertEquals(7L, routes.removed)
    }

    @Test
    fun `이미 추적 중이면 스냅샷을 덧쓰지 않는다`() = runTest {
        val history = FakeHistory()
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), history)

        useCase(quote, TripType.ROUND_TRIP)
        useCase(quote, TripType.ROUND_TRIP)

        // 덧쓰면 그게 최신이 되어 다음 비교의 기준선이 리셋된다.
        assertEquals(1, history.appended.size)
    }

    @Test
    fun `등록하면 기준선도 함께 심는다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = TrackRouteUseCase(routes, FakeHistory())

        useCase(quote, TripType.ROUND_TRIP)

        assertEquals(quote.price, routes.getAll().first().notifiedPrice)
    }

    @Test
    fun `이미 추적 중이면 새것이 아니라고 알린다`() = runTest {
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), FakeHistory())

        val first = useCase(quote, TripType.ROUND_TRIP)
        val second = useCase(quote, TripType.ROUND_TRIP)

        assertTrue(first.isNew)
        assertFalse(second.isNew)
        assertEquals(first.id, second.id)
    }
}
