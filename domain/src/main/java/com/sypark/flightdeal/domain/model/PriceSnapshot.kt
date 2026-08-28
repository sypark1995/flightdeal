package com.sypark.flightdeal.domain.model

import java.time.Instant

data class PriceSnapshot(
    val trackedRouteId: Long,
    val price: Won,
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
