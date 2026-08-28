package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * @param tripType 왕복과 편도는 가격대가 세 배쯤 차이 난다. 어느 쪽으로 등록했는지
 *   기억하지 않으면 다음 조회에서 다른 종류를 받아와 가짜 변동으로 읽힌다.
 *   등록 요청에서 받아 저장한다 — 응답에서 추론하지 않는다.
 */
data class TrackedRoute(
    val id: Long,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val tripType: TripType,
    val targetPrice: Won?,
    val createdAt: Instant,
)
