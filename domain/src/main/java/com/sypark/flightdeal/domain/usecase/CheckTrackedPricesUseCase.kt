package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.PRICE_HISTORY_RETENTION_DAYS
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import java.time.Clock
import javax.inject.Inject

/**
 * 워커가 부르는 유일한 진입점. 판정 로직 전체가 여기 있어서 JVM 테스트로 검증된다.
 */
class CheckTrackedPricesUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
    private val prices: FlightPriceRepository,
    private val detectChanges: DetectPriceChangesUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke(): List<PriceChange> {
        // 이력은 계속 쌓인다. 조회하러 온 김에 치운다.
        history.pruneOlderThan(PRICE_HISTORY_RETENTION_DAYS)

        return trackedRoutes.getAll().mapNotNull { tracked -> check(tracked) }
    }

    private suspend fun check(tracked: TrackedRoute): PriceChange? {
        val current = currentPrice(tracked) ?: return null

        // 관측 이력은 폴링마다 쌓는다. 추이 그래프의 재료이고, 판정에는 쓰지 않는다.
        history.append(
            PriceSnapshot(
                trackedRouteId = tracked.id,
                price = current,
                tripType = tracked.tripType,
                capturedAt = clock.instant(),
            )
        )

        // 기준선이 없으면 지금 값을 기준선으로 삼고 이번엔 알리지 않는다.
        // 비워둔 채로 두면 이 행은 어떤 변동도 영영 판정하지 못한다.
        if (tracked.notifiedPrice == null) {
            trackedRoutes.markNotified(tracked.id, current)
            return null
        }

        // 비교는 마지막으로 통보한 값과 한다. 마지막으로 관측한 값과 비교하면
        // 알림이 실패했을 때 그 변동이 기준선에 흡수돼 영영 사라진다.
        return detectChanges(tracked, tracked.notifiedPrice, current)
    }

    /** 한 노선이 실패했다고 나머지를 포기하지 않는다. */
    private suspend fun currentPrice(tracked: TrackedRoute): Won? =
        when (
            val result = prices.trackedPrice(
                route = tracked.route,
                departDate = tracked.departDate,
                returnDate = tracked.returnDate,
                tripType = tracked.tripType,
            )
        ) {
            is AppResult.Success -> result.data
            else -> null
        }
}
