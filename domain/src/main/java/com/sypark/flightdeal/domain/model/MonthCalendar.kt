package com.sypark.flightdeal.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 한 노선·한 달의 캘린더 화면 한 장.
 *
 * @param byDate 날짜별 최저가. 값이 없는 날은 키가 없다 — 한산한 날은 정상적으로 비어 있다.
 * @param cheapestDate 그 달에서 가장 싼 날. 값이 하나도 없으면 null.
 * @param median 그 달 분포의 중앙값. 싼 날을 강조하는 기준이다.
 * @param unbookableDates 가격은 있었지만 한국에서 예약할 수 있는 곳이 없던 날.
 *   빈 날(애초에 값이 없던 날)과 구별해 표시한다.
 */
data class MonthCalendar(
    val month: YearMonth,
    val byDate: Map<LocalDate, PriceQuote>,
    val cheapestDate: LocalDate?,
    val median: Won?,
    val unbookableDates: Set<LocalDate>,
)
