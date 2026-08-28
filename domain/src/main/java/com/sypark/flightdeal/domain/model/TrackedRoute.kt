package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

data class TrackedRoute(
    val id: Long,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val targetPrice: Won?,
    val createdAt: Instant,
)
