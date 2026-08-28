package com.sypark.flightdeal.domain.model

/**
 * 원화 금액. raw Int와 섞이지 않도록 감싼다.
 */
@JvmInline
value class Won(val amount: Int) : Comparable<Won> {

    override fun compareTo(other: Won): Int = amount.compareTo(other.amount)

    /**
     * [base] 대비 이 금액이 몇 퍼센트인지. [base]가 0이면 비교 자체가 무의미하므로 100을 돌려준다.
     *
     * 버림이 아니라 반올림한다. 189,000 / 305,000은 61.97%인데 버리면 61%가 되고,
     * 할인율이 39%로 계산돼 실제 38%와 어긋난다.
     */
    fun percentOf(base: Won): Int {
        if (base.amount == 0) return 100
        val scaled = amount.toLong() * 100 + base.amount / 2
        return (scaled / base.amount).toInt()
    }
}
