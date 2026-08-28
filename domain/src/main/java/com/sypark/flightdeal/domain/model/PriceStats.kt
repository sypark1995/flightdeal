package com.sypark.flightdeal.domain.model

/**
 * 한 노선·한 달치 가격의 분포 요약. 할인율 배지의 기준이 된다.
 */
data class PriceStats(
    val median: Won,
    val min: Won,
    val max: Won,
    val sampleCount: Int,
) {
    companion object {
        /**
         * @return 가격이 하나도 없으면 null. 빈 분포는 만들지 않는다.
         */
        fun from(prices: List<Won>): PriceStats? {
            if (prices.isEmpty()) return null
            val sorted = prices.sortedBy { it.amount }
            val mid = sorted.size / 2
            val median = if (sorted.size % 2 == 1) {
                sorted[mid]
            } else {
                Won((sorted[mid - 1].amount + sorted[mid].amount) / 2)
            }
            return PriceStats(
                median = median,
                min = sorted.first(),
                max = sorted.last(),
                sampleCount = sorted.size,
            )
        }
    }
}
