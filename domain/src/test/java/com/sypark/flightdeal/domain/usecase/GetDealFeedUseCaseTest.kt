package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class GetDealFeedUseCaseTest {

    private val incheon = Airport("ICN", "서울", "대한민국")
    private val tokyo = Airport("TYO", "도쿄", "일본")
    private val route = Route(incheon, tokyo)

    private fun quote(price: Int) = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
    )

    /** 테스트마다 응답을 지정할 수 있는 최소 스텁. */
    private class StubRepository(
        val deals: AppResult<List<PriceQuote>>,
        val stats: AppResult<PriceStats>,
    ) : FlightPriceRepository {
        override suspend fun cheapestDeals(origin: Airport, limit: Int) = deals
        override suspend fun calendarPrices(route: Route, month: YearMonth) =
            AppResult.Success(emptyList<PriceQuote>())
        override suspend fun priceStats(route: Route, month: YearMonth) = stats
    }

    @Test
    fun `할인율을 계산해 붙인다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.Success(listOf(quote(189_000))),
            stats = AppResult.Success(PriceStats(Won(305_000), Won(180_000), Won(400_000), 20)),
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        val result = useCase(incheon) as AppResult.Success

        assertEquals(1, result.data.size)
        assertEquals(38, result.data.first().discountPercent)
        assertEquals(Won(305_000), result.data.first().originalPrice)
    }

    @Test
    fun `분포를 못 구하면 할인율 없이 가격만 보여준다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.Success(listOf(quote(189_000))),
            stats = AppResult.Empty,
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        val result = useCase(incheon) as AppResult.Success

        assertEquals(1, result.data.size)
        assertNull(result.data.first().discountPercent)
        assertNull(result.data.first().originalPrice)
    }

    @Test
    fun `빈 응답은 Empty로 전달한다`() = runTest {
        val repo = StubRepository(deals = AppResult.Empty, stats = AppResult.Empty)
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        assertEquals(AppResult.Empty, useCase(incheon))
    }

    @Test
    fun `네트워크 오류는 그대로 전달한다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.NetworkError(IOException("boom")),
            stats = AppResult.Empty,
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        assertTrue(useCase(incheon) is AppResult.NetworkError)
    }
}
