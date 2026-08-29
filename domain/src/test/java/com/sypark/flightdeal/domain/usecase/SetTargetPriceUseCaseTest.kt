package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SetTargetPriceUseCaseTest {

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(target: Won?, notifiedPrice: Won?) = TrackedRoute(
        id = 1L,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = target,
        notifiedPrice = notifiedPrice,
        createdAt = Instant.EPOCH,
    )

    private class FakeTrackedRoutes(initial: TrackedRoute) : TrackedRouteRepository {
        var state = initial

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(listOf(state))
        override suspend fun getAll(): List<TrackedRoute> = listOf(state)
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
        ): TrackRegistration = TrackRegistration(1L, isNew = true)
        override suspend fun remove(id: Long) = Unit
        override suspend fun markNotified(id: Long, price: Won) {
            state = state.copy(notifiedPrice = price)
        }
        override suspend fun setTargetPrice(id: Long, target: Won?) {
            state = state.copy(targetPrice = target)
        }
    }

    @Test
    fun `목표가를 저장한다`() = runTest {
        val routes = FakeTrackedRoutes(tracked(target = null, notifiedPrice = Won(300_000)))
        val useCase = SetTargetPriceUseCase(routes)

        useCase(1L, Won(280_000))

        assertEquals(Won(280_000), routes.state.targetPrice)
    }

    @Test
    fun `null을 넣으면 목표가가 해제된다`() = runTest {
        val routes = FakeTrackedRoutes(tracked(target = Won(280_000), notifiedPrice = Won(300_000)))
        val useCase = SetTargetPriceUseCase(routes)

        useCase(1L, null)

        assertNull(routes.state.targetPrice)
    }

    @Test
    fun `목표가를 바꿔도 통보 기준선은 그대로다`() = runTest {
        // 기준선(notifiedPrice)이 함께 초기화되면 다음 폴링에서 없던 변동이 잡힌다 —
        // 이 프로젝트가 이미 여러 번 겪은 계열의 결함이다.
        val routes = FakeTrackedRoutes(tracked(target = Won(300_000), notifiedPrice = Won(300_000)))
        val useCase = SetTargetPriceUseCase(routes)

        useCase(1L, Won(250_000))

        assertEquals(Won(250_000), routes.state.targetPrice)
        assertEquals(Won(300_000), routes.state.notifiedPrice)
    }
}
