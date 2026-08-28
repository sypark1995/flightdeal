package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.YearMonth

/**
 * 개발·테스트용 구현체. 네트워크 없이 결정론적으로 동작한다.
 *
 * @param behavior 빈 상태와 오류 상태를 재현하기 위한 스위치.
 */
class FakeFlightPriceRepository(
    private val behavior: Behavior = Behavior.Normal,
) : FlightPriceRepository {

    enum class Behavior { Normal, EmptyData, Failing }

    override suspend fun cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>> =
        respond { FakeDealFixtures.deals().take(limit) }

    override suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>> =
        respond { FakeDealFixtures.monthlyPrices(route) }

    override suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats> {
        val prices = FakeDealFixtures.monthlyPrices(route).map { it.price }
        return when (behavior) {
            Behavior.Failing -> AppResult.NetworkError(IOException("fake network failure"))
            Behavior.EmptyData -> AppResult.Empty
            Behavior.Normal -> PriceStats.from(prices)
                ?.let { AppResult.Success(it) }
                ?: AppResult.Empty
        }
    }

    private suspend fun <T> respond(block: () -> List<T>): AppResult<List<T>> {
        delay(NETWORK_DELAY_MS)
        return when (behavior) {
            Behavior.Failing -> AppResult.NetworkError(IOException("fake network failure"))
            Behavior.EmptyData -> AppResult.Empty
            Behavior.Normal -> block().let { if (it.isEmpty()) AppResult.Empty else AppResult.Success(it) }
        }
    }

    private companion object {
        /** 로딩 상태가 실제로 보이도록 약간의 지연을 준다. runTest에서는 즉시 건너뛴다. */
        const val NETWORK_DELAY_MS = 400L
    }
}
