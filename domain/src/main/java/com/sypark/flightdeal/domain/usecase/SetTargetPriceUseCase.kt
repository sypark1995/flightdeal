package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

/**
 * 목표가를 바꿔도 **통보 기준선은 건드리지 않는다.**
 * 기준선은 "마지막으로 알린 가격"이고 목표가는 "사용자가 원하는 가격"이다.
 * 목표가를 바꿨다고 그동안의 변동 판정을 초기화하면, 다음 폴링에서
 * 없던 변동이 잡힌다.
 */
class SetTargetPriceUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
) {
    suspend operator fun invoke(id: Long, target: Won?) = trackedRoutes.setTargetPrice(id, target)
}
