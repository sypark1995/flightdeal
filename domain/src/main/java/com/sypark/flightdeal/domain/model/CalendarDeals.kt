package com.sypark.flightdeal.domain.model

import java.time.LocalDate

/**
 * 캘린더 조회 한 번의 결과.
 *
 * @param deals 날짜당 하나씩, 한국에서 예약 가능한 최저가. 출발일 오름차순.
 * @param unbookableDates 가격은 있었지만 **한국에서 예약할 수 있는 곳이 하나도 없던** 날.
 *   화면이 "값이 없는 날"과 구별해서 보여줄 수 있어야 한다 — 둘은 사용자에게 다른 뜻이다.
 *   도메인은 이유(예약처 등)를 모른다. "이 날은 살 수 없다"는 사실만 안다.
 */
data class CalendarDeals(
    val deals: List<PriceQuote>,
    val unbookableDates: Set<LocalDate>,
)
