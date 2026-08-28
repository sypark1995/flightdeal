package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Route
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFlightPriceRepositoryTest {

    // DESTINATIONS에 있는 실제 노선이어야 monthlyPrices()가 빈 목록을 돌려주지 않는다.
    private val tokyoRoute = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    @Test
    fun `기본 동작은 특가 목록을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isNotEmpty())
    }

    @Test
    fun `limit보다 많이 돌려주지 않는다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 2)

        assertEquals(2, (result as AppResult.Success).data.size)
    }

    @Test
    fun `EmptyData 모드는 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `Failing 모드는 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertTrue(result is AppResult.NetworkError)
    }

    @Test
    fun `calendarPrices는 요청한 달에 속한 날짜만 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val month = YearMonth.of(2026, 10)

        val result = repo.calendarPrices(tokyoRoute, month)

        assertTrue(result is AppResult.Success)
        val quotes = (result as AppResult.Success).data
        assertTrue(quotes.all { YearMonth.from(it.departDate) == month })
    }

    @Test
    fun `calendarPrices는 요청한 달마다 다른 날짜를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val october = (repo.calendarPrices(tokyoRoute, YearMonth.of(2026, 10)) as AppResult.Success).data
        val november = (repo.calendarPrices(tokyoRoute, YearMonth.of(2026, 11)) as AppResult.Success).data

        assertTrue(october.map { it.departDate }.none { it in november.map { q -> q.departDate } })
    }

    @Test
    fun `calendarPrices는 그 달의 일수만큼 가격을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val month = YearMonth.of(2026, 10)

        val result = repo.calendarPrices(tokyoRoute, month)

        assertEquals(month.lengthOfMonth(), (result as AppResult.Success).data.size)
    }

    @Test
    fun `priceStats는 Normal 모드에서 Success를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10))

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `priceStats는 EmptyData 모드에서 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10))

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `priceStats는 Failing 모드에서 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10))

        assertTrue(result is AppResult.NetworkError)
    }
}
