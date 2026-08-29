package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.repository.PriceAlertRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import java.time.Clock
import javax.inject.Inject

/**
 * 알림이 전달된 것을 확인하고 기준선을 옮기며, 그 사실을 기록으로 남긴다.
 * 알림을 실제로 띄운 뒤에만 부른다 — 먼저 부르면 놓친 변동이 생긴다.
 *
 * 기준선 갱신과 기록은 **같은 자리에서 일어나야 한다.** 따로 두면 알림은 갔는데
 * 기록에 없거나, 기록에는 있는데 기준선이 안 옮겨간 상태가 생긴다.
 */
class ConfirmNotifiedUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val alerts: PriceAlertRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(changes: List<PriceChange>) {
        alerts.record(changes, clock.instant())
        changes.forEach { trackedRoutes.markNotified(it.trackedRouteId, it.current) }
    }
}
