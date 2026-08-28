package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
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

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10, tripType = TripType.ROUND_TRIP)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isNotEmpty())
    }

    @Test
    fun `limit보다 많이 돌려주지 않는다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 2, tripType = TripType.ROUND_TRIP)

        assertEquals(2, (result as AppResult.Success).data.size)
    }

    @Test
    fun `EmptyData 모드는 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10, tripType = TripType.ROUND_TRIP)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `Failing 모드는 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10, tripType = TripType.ROUND_TRIP)

        assertTrue(result is AppResult.NetworkError)
    }

    @Test
    fun `calendarPrices는 요청한 달에 속한 날짜만 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val month = YearMonth.of(2026, 10)

        val result = repo.calendarPrices(tokyoRoute, month, TripType.ROUND_TRIP)

        assertTrue(result is AppResult.Success)
        val quotes = (result as AppResult.Success).data
        assertTrue(quotes.all { YearMonth.from(it.departDate) == month })
    }

    @Test
    fun `calendarPrices는 요청한 달마다 다른 날짜를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val october = (repo.calendarPrices(tokyoRoute, YearMonth.of(2026, 10), TripType.ROUND_TRIP) as AppResult.Success).data
        val november = (repo.calendarPrices(tokyoRoute, YearMonth.of(2026, 11), TripType.ROUND_TRIP) as AppResult.Success).data

        assertTrue(october.map { it.departDate }.none { it in november.map { q -> q.departDate } })
    }

    @Test
    fun `calendarPrices는 그 달의 일수만큼 가격을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val month = YearMonth.of(2026, 10)

        val result = repo.calendarPrices(tokyoRoute, month, TripType.ROUND_TRIP)

        assertEquals(month.lengthOfMonth(), (result as AppResult.Success).data.size)
    }

    @Test
    fun `priceStats는 Normal 모드에서 Success를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10), TripType.ROUND_TRIP)

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `priceStats는 EmptyData 모드에서 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10), TripType.ROUND_TRIP)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `priceStats는 Failing 모드에서 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.priceStats(tokyoRoute, YearMonth.of(2026, 10), TripType.ROUND_TRIP)

        assertTrue(result is AppResult.NetworkError)
    }

    @Test
    fun `편도를 요청하면 귀국일이 없다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val deals = (repo.cheapestDeals(Airport.INCHEON, 10, TripType.ONE_WAY)
            as AppResult.Success).data

        assertTrue(deals.isNotEmpty())
        assertTrue(deals.all { it.returnDate == null })
    }

    @Test
    fun `왕복을 요청하면 귀국일이 있다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val deals = (repo.cheapestDeals(Airport.INCHEON, 10, TripType.ROUND_TRIP)
            as AppResult.Success).data

        assertTrue(deals.all { it.returnDate != null })
    }

    @Test
    fun `편도가 왕복보다 싸다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val oneWay = (repo.cheapestDeals(Airport.INCHEON, 1, TripType.ONE_WAY)
            as AppResult.Success).data.first()
        val roundTrip = (repo.cheapestDeals(Airport.INCHEON, 1, TripType.ROUND_TRIP)
            as AppResult.Success).data.first()

        // 같은 값이면 토글을 눌러도 화면이 그대로라 사용자가 고장으로 오해한다.
        assertTrue(oneWay.price < roundTrip.price)
    }
}
