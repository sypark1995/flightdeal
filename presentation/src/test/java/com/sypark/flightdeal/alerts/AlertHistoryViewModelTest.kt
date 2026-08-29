package com.sypark.flightdeal.alerts

import app.cash.turbine.test
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AlertHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(id: Long = 1L) = TrackedRoute(
        id = id,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = Won(280_000),
        notifiedPrice = Won(280_000),
        createdAt = Instant.EPOCH,
    )

    private fun alert(id: Long = 1L, trackedRouteId: Long = 1L, at: Long = 100) = PriceAlert(
        id = id,
        trackedRouteId = trackedRouteId,
        previous = Won(300_000),
        current = Won(280_000),
        reachedTarget = false,
        notifiedAt = Instant.ofEpochSecond(at),
    )

    private class FakeRoutes(routes: List<TrackedRoute>) : TrackedRouteRepository {
        val state = MutableStateFlow(routes)
        override fun observeAll(): Flow<List<TrackedRoute>> = state
        override suspend fun getAll(): List<TrackedRoute> = state.value
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
        ): TrackRegistration = TrackRegistration(1L, isNew = true)
        override suspend fun remove(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
        override suspend fun markNotified(id: Long, price: Won) = Unit
        override suspend fun setTargetPrice(id: Long, target: Won?) = Unit
    }

    private class FakeAlerts(alerts: List<PriceAlert>) : PriceAlertRepository {
        val state = MutableStateFlow(alerts)
        override suspend fun record(changes: List<PriceChange>, at: Instant) = Unit
        override fun observeRecent(days: Int): Flow<List<PriceAlert>> = state
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    // 알림들은 epoch에서 100~200초 뒤. 이 시각을 "지금"으로 고정해서 관측 시각과
    // 가깝게(1분 미만) 만든다 — relativeTime이 "방금"으로 나와야 정상이다.
    private val clock = Clock.fixed(Instant.ofEpochSecond(210), ZoneOffset.UTC)

    private fun viewModel(alerts: PriceAlertRepository, routes: TrackedRouteRepository) =
        AlertHistoryViewModel(alerts, routes, clock)

    @Test
    fun `알림 기록이 없으면 빈 상태다`() = runTest {
        val vm = viewModel(FakeAlerts(emptyList()), FakeRoutes(listOf(tracked())))

        vm.uiState.test {
            assertEquals(AlertHistoryUiState.Loading, awaitItem())
            assertEquals(AlertHistoryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `알림 기록에 노선 정보를 채워 보여준다`() = runTest {
        val vm = viewModel(FakeAlerts(listOf(alert())), FakeRoutes(listOf(tracked())))

        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as AlertHistoryUiState.Success).items.single()

            assertEquals(route, item.route)
            assertEquals(TripType.ROUND_TRIP, item.tripType)
            assertEquals(Won(280_000), item.alert.current)
            assertEquals(Direction.DOWN, item.alert.direction)
            // clock이 알림 시각(100초)에서 110초 뒤(210초)로 고정돼 있다 — 1분 이상 1시간 미만.
            assertEquals("1분 전", item.relativeTime)
        }
    }

    @Test
    fun `노선을 못 찾는 기록은 버린다`() = runTest {
        // CASCADE라 정상적으로는 생기지 않지만, 방어적으로 다룬다 — 그 하나 때문에
        // 화면 전체가 못 열려서는 안 된다.
        val orphan = alert(id = 1L, trackedRouteId = 99L)
        val valid = alert(id = 2L, trackedRouteId = 1L, at = 200)
        val vm = viewModel(FakeAlerts(listOf(orphan, valid)), FakeRoutes(listOf(tracked())))

        vm.uiState.test {
            awaitItem()
            val items = (awaitItem() as AlertHistoryUiState.Success).items

            assertEquals(1, items.size)
            assertEquals(2L, items.single().alert.id)
        }
    }

    @Test
    fun `모든 기록의 노선을 못 찾으면 빈 상태다`() = runTest {
        val orphan = alert(id = 1L, trackedRouteId = 99L)
        val vm = viewModel(FakeAlerts(listOf(orphan)), FakeRoutes(listOf(tracked())))

        vm.uiState.test {
            awaitItem()
            assertEquals(AlertHistoryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `최신 알림이 먼저 온다`() = runTest {
        // 저장소가 이미 최신순으로 정렬해 돌려준다 — ViewModel은 순서를 바꾸지 않는다.
        val older = alert(id = 1L, at = 100)
        val newer = alert(id = 2L, at = 200)
        val vm = viewModel(FakeAlerts(listOf(newer, older)), FakeRoutes(listOf(tracked())))

        vm.uiState.test {
            awaitItem()
            val items = (awaitItem() as AlertHistoryUiState.Success).items

            assertTrue(items[0].alert.notifiedAt.isAfter(items[1].alert.notifiedAt))
        }
    }
}
