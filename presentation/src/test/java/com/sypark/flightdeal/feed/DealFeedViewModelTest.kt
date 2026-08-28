package com.sypark.flightdeal.feed

import app.cash.turbine.test
import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.usecase.CalculateDiscountUseCase
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DealFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(behavior: FakeFlightPriceRepository.Behavior) =
        DealFeedViewModel(
            GetDealFeedUseCase(FakeFlightPriceRepository(behavior), CalculateDiscountUseCase())
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
}
