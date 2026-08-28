package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import java.time.YearMonth
import javax.inject.Inject

class GetDealFeedUseCase @Inject constructor(
    private val repository: FlightPriceRepository,
    private val calculateDiscount: CalculateDiscountUseCase,
) {

    suspend operator fun invoke(origin: Airport, limit: Int = DEFAULT_LIMIT): AppResult<List<DealItem>> {
        return when (val deals = repository.cheapestDeals(origin, limit)) {
            is AppResult.Success -> AppResult.Success(deals.data.map { quote ->
                // 분포 조회가 실패해도 딜 자체는 보여준다. 배지만 빠진다.
                val stats = (repository.priceStats(quote.route, YearMonth.from(quote.departDate))
                    as? AppResult.Success)?.data
                val discount = stats?.let { calculateDiscount(quote.price, it) }

                DealItem(
                    quote = quote,
                    discountPercent = discount,
                    originalPrice = if (discount != null) stats.median else null,
                )
            })
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> deals
            is AppResult.Unknown -> deals
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
