package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.Won
import javax.inject.Inject

/**
 * 직전 스냅샷과 새 가격을 비교해 알릴 변동을 판정한다.
 * 워커의 알림 로직 전부가 여기 있다.
 *
 * @return 알릴 변동이 없으면 null.
 */
class DetectPriceChangesUseCase @Inject constructor() {

    operator fun invoke(
        tracked: TrackedRoute,
        previous: PriceSnapshot?,
        current: Won,
    ): PriceChange? {
        // 비교 대상이 없으면 "변동"이라는 개념 자체가 성립하지 않는다.
        if (previous == null) return null
        if (previous.price == current) return null

        return PriceChange(
            trackedRouteId = tracked.id,
            previous = previous.price,
            current = current,
            direction = if (current < previous.price) Direction.DOWN else Direction.UP,
            // 방향과 무관하게 판정한다. 올랐어도 목표가 이하면 살 만한 가격이다.
            reachedTarget = tracked.targetPrice?.let { current <= it } ?: false,
        )
    }
}
