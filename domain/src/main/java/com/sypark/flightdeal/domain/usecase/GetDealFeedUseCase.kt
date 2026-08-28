package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.YearMonth
import javax.inject.Inject

class GetDealFeedUseCase @Inject constructor(
    private val repository: FlightPriceRepository,
    private val calculateDiscount: CalculateDiscountUseCase,
) {

    suspend operator fun invoke(
        origin: Airport,
        tripType: TripType,
        limit: Int = DEFAULT_LIMIT,
    ): AppResult<List<DealItem>> {
        return when (val deals = repository.cheapestDeals(origin, limit, tripType)) {
            is AppResult.Success -> AppResult.Success(attachDiscounts(deals.data, tripType))
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> deals
            is AppResult.Unknown -> deals
        }
    }

    /**
     * 딜마다 분포를 따로 조회하면 왕복이 딜 개수만큼 순차로 쌓인다. limit 기본값 20에
     * 실제 네트워크 지연을 곱하면 첫 화면이 수 초씩 걸린다.
     *
     * 그래서 두 가지를 한다. 같은 (노선, 달)은 한 번만 조회하고, 그 조회들을 병렬로 돌린다.
     */
    private suspend fun attachDiscounts(
        quotes: List<PriceQuote>,
        tripType: TripType,
    ): List<DealItem> = coroutineScope {
        val keys = quotes.map { it.route to YearMonth.from(it.departDate) }.distinct()

        val statsByKey: Map<Pair<Route, YearMonth>, PriceStats> = keys
            .map { key ->
                async {
                    // 분포 조회가 실패해도 딜 자체는 보여준다. 배지만 빠진다.
                    key to (repository.priceStats(key.first, key.second, tripType) as? AppResult.Success)?.data
                }
            }
            .awaitAll()
            .mapNotNull { (key, stats) -> stats?.let { key to it } }
            .toMap()

        quotes.map { quote ->
            val stats = statsByKey[quote.route to YearMonth.from(quote.departDate)]
            // 배지와 취소선 기준가는 항상 함께 붙거나 함께 빠진다.
            val badge = stats?.let { s ->
                calculateDiscount(quote.price, s)?.let { percent -> percent to s.median }
            }

            DealItem(
                quote = quote,
                discountPercent = badge?.first,
                originalPrice = badge?.second,
            )
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
