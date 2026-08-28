package com.sypark.flightdeal.domain.model

/**
 * 화면이 그리는 딜 하나. 가격에 "이게 싼 건지"의 판단이 붙어 있다.
 *
 * @param discountPercent 배지에 표시할 할인율. 배지를 달 가치가 없으면 null.
 * @param originalPrice 취소선으로 보여줄 기준가(중앙값). [discountPercent]가 null이면 함께 null.
 */
data class DealItem(
    val quote: PriceQuote,
    val discountPercent: Int?,
    val originalPrice: Won?,
)
