package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceAlert
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceAlertRepository
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
import java.time.ZoneOffset

class ConfirmNotifiedUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private class StubRoutes : TrackedRouteRepository {
        val notified = mutableListOf<Pair<Long, Won>>()
        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(emptyList())
        override suspend fun getAll(): List<TrackedRoute> = emptyList()
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
        ): TrackRegistration = TrackRegistration(1L, isNew = true)
        override suspend fun remove(id: Long) = Unit
        override suspend fun markNotified(id: Long, price: Won) { notified += id to price }
        override suspend fun setTargetPrice(id: Long, target: Won?) = Unit
    }

    private class StubAlerts : PriceAlertRepository {
        val recorded = mutableListOf<PriceChange>()
        var lastAt: Instant? = null
        override suspend fun record(changes: List<PriceChange>, at: Instant) {
            recorded += changes
            lastAt = at
        }
        override fun observeRecent(days: Int): Flow<List<PriceAlert>> = flowOf(emptyList())
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    private fun change(id: Long = 1L, previous: Int = 300_000, current: Int = 280_000) = PriceChange(
        trackedRouteId = id,
        previous = Won(previous),
        current = Won(current),
        direction = Direction.DOWN,
        reachedTarget = false,
    )

    @Test
    fun `알림을 확인하면 기록이 남는다`() = runTest {
        val routes = StubRoutes()
        val alerts = StubAlerts()
        val useCase = ConfirmNotifiedUseCase(routes, alerts, clock)
        val shown = listOf(change())

        useCase(shown)

        assertEquals(shown, alerts.recorded)
        assertEquals(clock.instant(), alerts.lastAt)
        assertEquals(listOf(1L to Won(280_000)), routes.notified)
    }

    /**
     * 워커는 notifier.notify(...)가 돌려준 shown만 이 유스케이스에 넘긴다 — 채널이
     * 꺼져 있거나 표시에 실패해 아무 것도 렌더링되지 않으면 shown은 빈 리스트다.
     * 이 유스케이스가 자신이 받은 인자가 아니라 다른 경로(예: 판정 직후의 전체
     * changes)에서 기록할 것을 다시 끌어오면, 이 테스트처럼 빈 리스트를 넘겨도
     * 무언가 기록되는 회귀가 생긴다 — 사용자는 오지도 않은 알림을 기록에서 보게 된다.
     */
    @Test
    fun `보여주지 않은 변동은 기록하지 않는다`() = runTest {
        val routes = StubRoutes()
        val alerts = StubAlerts()
        val useCase = ConfirmNotifiedUseCase(routes, alerts, clock)

        useCase(emptyList())

        assertTrue(alerts.recorded.isEmpty())
        assertTrue(routes.notified.isEmpty())
    }
}
