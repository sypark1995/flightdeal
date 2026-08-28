package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

/**
 * 알림이 전달된 것을 확인하고 기준선을 옮긴다.
 * 알림을 실제로 띄운 뒤에만 부른다 — 먼저 부르면 놓친 변동이 생긴다.
 */
class ConfirmNotifiedUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
) {
    suspend operator fun invoke(changes: List<PriceChange>) {
        changes.forEach { trackedRoutes.markNotified(it.trackedRouteId, it.current) }
    }
}
