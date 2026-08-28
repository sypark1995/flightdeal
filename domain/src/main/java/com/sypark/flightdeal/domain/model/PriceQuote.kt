package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 특정 노선·날짜의 가격 하나.
 *
 * @param foundAt 이 가격이 관측된 시각. 소스가 캐시 기반이라 조회 시각과 다를 수 있다.
 * @param deepLink 예약처로 연결할 URL. 없을 수 있다.
 */
data class PriceQuote(
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val price: Won,
    val airline: String?,
    val foundAt: Instant,
    val deepLink: String?,
)
