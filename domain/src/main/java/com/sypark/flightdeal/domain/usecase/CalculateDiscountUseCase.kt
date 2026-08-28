package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Won
import javax.inject.Inject

/**
 * 중앙값 대비 할인율을 계산한다. 배지로 보여줄 가치가 없으면 null.
 */
class CalculateDiscountUseCase @Inject constructor() {

    operator fun invoke(price: Won, stats: PriceStats): Int? {
        if (stats.sampleCount < MIN_SAMPLE_COUNT) return null
        if (price >= stats.median) return null

        val discount = 100 - price.percentOf(stats.median)
        return if (discount >= MIN_DISCOUNT_PERCENT) discount else null
    }

    private companion object {
        /** 표본이 이보다 적으면 중앙값을 신뢰하지 않는다. */
        const val MIN_SAMPLE_COUNT = 3
        /** 이보다 작은 할인은 배지를 달지 않는다. */
        const val MIN_DISCOUNT_PERCENT = 5
    }
}
