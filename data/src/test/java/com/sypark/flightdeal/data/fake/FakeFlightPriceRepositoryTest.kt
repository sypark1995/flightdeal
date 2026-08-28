package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import java.time.LocalDate
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
    fun `calendarDeals는 calendarPrices와 같은 값을 돌려준다`() = runTest {
        // FakeDealFixtures에는 예약처 개념이 없다. 실데이터처럼 걸러낼 게 없으니
        // 둘이 갈라질 이유가 없다 — 갈리면 딜 피드와 캘린더 화면의 개발용 데이터가 달라진다.
        val repo = FakeFlightPriceRepository()
        val month = YearMonth.of(2026, 10)

        val prices = (repo.calendarPrices(tokyoRoute, month, TripType.ROUND_TRIP) as AppResult.Success).data
        val deals = (repo.calendarDeals(tokyoRoute, month, TripType.ROUND_TRIP) as AppResult.Success).data

        assertEquals(prices, deals)
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
    fun `trackedPrice는 귀국일이 다르면 값이 없다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val departDate = LocalDate.of(2026, 10, 12)

        // 픽스처의 실제 귀국일은 출발일+4일이다. 다른 날을 요구하면 같은 여정이 아니다.
        val mismatched = repo.trackedPrice(
            route = tokyoRoute,
            departDate = departDate,
            returnDate = departDate.plusDays(10),
            tripType = TripType.ROUND_TRIP,
        )

        // 출발일만 보고 맞다고 하면, 실제 구현에서 고쳐진 "귀국일 다른 여정을 같은 걸로
        // 치는" 버그를 이 fake가 다시 감추게 된다.
        assertEquals(AppResult.Empty, mismatched)
    }

    @Test
    fun `trackedPrice는 귀국일이 맞으면 값을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val departDate = LocalDate.of(2026, 10, 12)

        val matched = repo.trackedPrice(
            route = tokyoRoute,
            departDate = departDate,
            returnDate = departDate.plusDays(4),
            tripType = TripType.ROUND_TRIP,
        )

        assertTrue(matched is AppResult.Success)
    }

    @Test
    fun `trackedPrice는 편도 조회에서 귀국일을 null로 요구한다`() = runTest {
        val repo = FakeFlightPriceRepository()
        val departDate = LocalDate.of(2026, 10, 12)

        val oneWay = repo.trackedPrice(
            route = tokyoRoute,
            departDate = departDate,
            returnDate = null,
            tripType = TripType.ONE_WAY,
        )

        assertTrue(oneWay is AppResult.Success)
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
