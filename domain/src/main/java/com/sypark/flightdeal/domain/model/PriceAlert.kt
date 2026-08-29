package com.sypark.flightdeal.domain.model

import java.time.Instant

/**
 * 사용자에게 **실제로 보여준** 가격 변동 하나.
 *
 * @param notifiedAt 알림을 띄운 시각. 관측 시각이 아니다 — 못 본 알림을 나중에
 *   찾아보는 것이 이 기록의 목적이므로 "언제 알렸나"가 기준이다.
 */
data class PriceAlert(
    val id: Long,
    val trackedRouteId: Long,
    val previous: Won,
    val current: Won,
    val reachedTarget: Boolean,
    val notifiedAt: Instant,
) {
    /** 저장하지 않고 계산한다. 값과 방향이 따로 저장되면 둘이 어긋날 수 있다. */
    val direction: Direction get() = if (current < previous) Direction.DOWN else Direction.UP
}
