package com.sypark.flightdeal.tracking

import app.cash.turbine.test
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.UntrackRouteUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

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
        notifiedPrice = null,
        createdAt = Instant.EPOCH,
    )

    private fun snapshot(price: Int, at: Long) =
        PriceSnapshot(1L, Won(price), TripType.ROUND_TRIP, Instant.ofEpochSecond(at))

    private class FakeRoutes(routes: List<TrackedRoute>) : TrackedRouteRepository {
        val state = MutableStateFlow(routes)
        var removed: Long? = null

        override fun observeAll(): Flow<List<TrackedRoute>> = state
        override suspend fun getAll(): List<TrackedRoute> = state.value
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
        ): TrackRegistration = TrackRegistration(1L, isNew = true)
        override suspend fun remove(id: Long) {
            removed = id
            state.value = state.value.filterNot { it.id == id }
        }
        override suspend fun markNotified(id: Long, price: Won) = Unit
        var lastTargetId: Long? = null
        var lastTarget: Won? = null
        override suspend fun setTargetPrice(id: Long, target: Won?) {
            lastTargetId = id
            lastTarget = target
            state.value = state.value.map { if (it.id == id) it.copy(targetPrice = target) else it }
        }
    }

    private class FakeHistory(private val snapshots: List<PriceSnapshot>) : PriceHistoryRepository {
        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = snapshots.lastOrNull()
        override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
            flowOf(snapshots)
        override suspend fun pruneOlderThan(days: Int) = Unit
        override fun observeCount(): Flow<Int> = flowOf(snapshots.size)
        override suspend fun clearAll() = Unit
    }

    // 기존 테스트의 tracked()는 2026-10-12 출발이다. 이보다 이른 고정 시각을 써서
    // hasDeparted가 이 테스트들에서는 항상 false가 되게 한다.
    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(
        routes: TrackedRouteRepository,
        history: PriceHistoryRepository,
    ) = TrackingViewModel(routes, history, UntrackRouteUseCase(routes), clock)

    @Test
    fun `추적 항목이 없으면 빈 상태다`() = runTest {
        viewModel(FakeRoutes(emptyList()), FakeHistory(emptyList())).uiState.test {
            assertEquals(TrackingUiState.Loading, awaitItem())
            assertEquals(TrackingUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `최근 두 관측값을 함께 내려준다`() = runTest {
        val history = FakeHistory(listOf(snapshot(300_000, 100), snapshot(280_000, 200)))

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertEquals(Won(280_000), item.latest!!.price)
            assertEquals(Won(300_000), item.previous!!.price)
        }
    }

    @Test
    fun `관측값이 하나뿐이면 직전 값이 없다`() = runTest {
        val history = FakeHistory(listOf(snapshot(300_000, 100)))

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            // 등록 직후엔 비교할 대상이 없다. 화면은 변동을 표시하지 않아야 한다.
            assertEquals(Won(300_000), item.latest!!.price)
            assertNull(item.previous)
        }
    }

    @Test
    fun `값이 그대로인 폴링이 쌓여도 마지막 변동을 계속 보여준다`() = runTest {
        // 300,000 -> 250,000 하락 이후 세 번의 무변동 폴링. 마지막 두 스냅샷만 보면
        // 둘 다 250,000이라 화살표가 사라진다.
        val history = FakeHistory(
            listOf(
                snapshot(300_000, 100),
                snapshot(250_000, 200),
                snapshot(250_000, 300),
                snapshot(250_000, 400),
            )
        )

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertEquals(Won(250_000), item.latest!!.price)
            // 방금 통보한 하락(300,000 -> 250,000)이 계속 보여야 한다.
            assertEquals(Won(300_000), item.previous!!.price)
        }
    }

    @Test
    fun `카드에 전체 이력이 실린다`() = runTest {
        // 그래프는 마지막 두 점이 아니라 그동안 모은 전부를 그린다.
        val history = FakeHistory(
            listOf(
                snapshot(300_000, 100),
                snapshot(250_000, 200),
                snapshot(250_000, 300),
            )
        )

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertEquals(listOf(Won(300_000), Won(250_000), Won(250_000)), item.history.map { it.price })
        }
    }

    @Test
    fun `새 가격이 저장되면 화면이 바로 갱신된다`() = runTest {
        val snapshots = MutableStateFlow(listOf(snapshot(300_000, 100)))
        val history = object : PriceHistoryRepository {
            override suspend fun append(snapshot: PriceSnapshot) = Unit
            override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = null
            override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
                snapshots
            override suspend fun pruneOlderThan(days: Int) = Unit
            override fun observeCount(): Flow<Int> = flowOf(0)
            override suspend fun clearAll() = Unit
        }

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            assertEquals(
                Won(300_000),
                (awaitItem() as TrackingUiState.Success).items.single().latest!!.price,
            )

            // 워커가 새 가격을 저장한 상황. 탭을 나갔다 오지 않아도 보여야 한다.
            snapshots.value = listOf(snapshot(300_000, 100), snapshot(280_000, 200))

            val updated = (awaitItem() as TrackingUiState.Success).items.single()
            assertEquals(Won(280_000), updated.latest!!.price)
            assertEquals(Won(300_000), updated.previous!!.price)
        }
    }

    @Test
    fun `출발일이 지나면 지난 여정으로 표시한다`() = runTest {
        val pastClock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)
        val vm = TrackingViewModel(
            FakeRoutes(listOf(tracked())), // departDate = 2026-10-12
            FakeHistory(listOf(snapshot(300_000, 100))),
            UntrackRouteUseCase(FakeRoutes(listOf(tracked()))),
            pastClock,
        )

        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertTrue(item.hasDeparted)
        }
    }

    @Test
    fun `출발일이 지나지 않으면 지난 여정이 아니다`() = runTest {
        viewModel(FakeRoutes(listOf(tracked())), FakeHistory(listOf(snapshot(300_000, 100)))).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertTrue(!item.hasDeparted)
        }
    }

    @Test
    fun `해제하면 목록에서 빠진다`() = runTest {
        val routes = FakeRoutes(listOf(tracked()))
        val vm = viewModel(routes, FakeHistory(listOf(snapshot(300_000, 100))))

        vm.uiState.test {
            awaitItem()
            assertTrue(awaitItem() is TrackingUiState.Success)

            vm.untrack(1L)

            assertEquals(TrackingUiState.Empty, awaitItem())
        }
        assertEquals(1L, routes.removed)
    }
}
