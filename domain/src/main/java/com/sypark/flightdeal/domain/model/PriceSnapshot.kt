package com.sypark.flightdeal.domain.model

import java.time.Instant

/**
 * @param tripType 이 가격이 어떤 종류의 운임이었는지. 추적 항목의 종류가 바뀔 수 있으므로
 *   스냅샷도 자기 종류를 들고 있어야 한다. 종류가 다른 스냅샷끼리 비교하면
 *   매번 "60% 하락"이 뜬다.
 */
data class PriceSnapshot(
    val trackedRouteId: Long,
    val price: Won,
    val tripType: TripType,
    val capturedAt: Instant,
)

enum class Direction { DOWN, UP }

data class PriceChange(
    val trackedRouteId: Long,
    val previous: Won,
    val current: Won,
    val direction: Direction,
    val reachedTarget: Boolean,
)
