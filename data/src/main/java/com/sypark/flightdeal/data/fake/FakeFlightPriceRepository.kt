package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
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

    override suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = respond {
        FakeDealFixtures.deals()
            .take(limit)
            .map { if (tripType == TripType.ONE_WAY) it.asOneWay() else it }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * 픽스처는 왕복 기준이다. 귀국일만 지우고 가격을 그대로 두면 같은 항공편이
     * "10만원 편도"이자 "10만원 왕복"이 되어버린다. 실측에서 인천→도쿄 편도는
     * 왕복의 3분의 1 수준이었으므로 어림잡아 60%로 낮춘다.
     */
    private fun PriceQuote.asOneWay(): PriceQuote =
        copy(returnDate = null, price = Won(price.amount * ONE_WAY_RATIO_PERCENT / 100))

    override suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>> =
        respond { FakeDealFixtures.monthlyPrices(route, month).takeIf { it.isNotEmpty() } }

    override suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats> =
        respond { PriceStats.from(FakeDealFixtures.monthlyPrices(route, month).map { it.price }) }

    /**
     * 세 조회가 모두 같은 지연과 같은 [Behavior] 규칙을 거치도록 한 곳에 모은다.
     * 한 메서드만 지연을 건너뛰면 로딩 상태를 다루는 테스트가 조용히 어긋난다.
     *
     * @param produce 결과가 없으면 null을 돌려준다. 그러면 [AppResult.Empty]가 된다.
     */
    private suspend fun <T : Any> respond(produce: () -> T?): AppResult<T> {
        delay(NETWORK_DELAY_MS)
        return when (behavior) {
            Behavior.Failing -> AppResult.NetworkError(IOException("fake network failure"))
            Behavior.EmptyData -> AppResult.Empty
            Behavior.Normal -> produce()?.let { AppResult.Success(it) } ?: AppResult.Empty
        }
    }

    private companion object {
        /**
         * 로딩 상태가 실제로 보이도록 약간의 지연을 준다. runTest에서는 즉시 건너뛴다.
         * 피드 한 번에 조회가 1 + 딜 개수만큼 일어나므로 값을 키우면 체감이 급격히 나빠진다.
         */
        const val NETWORK_DELAY_MS = 150L

        /** 편도는 왕복의 대략 60%. Fake가 편도와 왕복을 같은 값으로 말하지 않게 한다. */
        const val ONE_WAY_RATIO_PERCENT = 60
    }
}
