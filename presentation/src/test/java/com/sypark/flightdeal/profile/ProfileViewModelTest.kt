package com.sypark.flightdeal.profile

import app.cash.turbine.test
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeHistory(count: Int) : PriceHistoryRepository {
        val countState = MutableStateFlow(count)
        var cleared = false

        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = null
        override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
            throw NotImplementedError("내정보 화면은 건수만 본다")
        override suspend fun pruneOlderThan(days: Int) = Unit
        override fun observeCount(): Flow<Int> = countState
        override suspend fun clearAll() {
            cleared = true
            countState.value = 0
        }
    }

    @Test
    fun `이력 건수를 그대로 흘려보낸다`() = runTest {
        val history = FakeHistory(count = 12)

        ProfileViewModel(history).historyCount.test {
            assertEquals(0, awaitItem()) // stateIn 초기값
            assertEquals(12, awaitItem())
        }
    }

    @Test
    fun `이력을 지우면 저장소의 삭제를 부른다`() = runTest {
        val history = FakeHistory(count = 5)
        val viewModel = ProfileViewModel(history)

        viewModel.historyCount.test {
            awaitItem()
            awaitItem()

            viewModel.clearHistory()

            assertEquals(0, awaitItem())
        }
        assertTrue(history.cleared)
    }
}
