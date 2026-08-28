package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

class UntrackRouteUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
) {
    /** 이력은 저장소의 외래키가 함께 지운다. */
    suspend operator fun invoke(id: Long) = trackedRoutes.remove(id)
}
