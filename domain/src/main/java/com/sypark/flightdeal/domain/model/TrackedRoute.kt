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
    /**
     * 사용자가 마지막으로 **실제로 통보받은** 가격. 변동 판정의 기준선이다.
     *
     * 마지막으로 관측한 값과 다르다. 관측은 폴링할 때마다 쌓이지만 이 값은
     * 알림이 전달된 뒤에만 옮긴다. 전달에 실패하면 그대로 남아 다음 실행에서
     * 같은 변동이 다시 잡힌다 — 놓치는 것보다 중복이 낫다.
     */
    val notifiedPrice: Won?,
    val createdAt: Instant,
)
