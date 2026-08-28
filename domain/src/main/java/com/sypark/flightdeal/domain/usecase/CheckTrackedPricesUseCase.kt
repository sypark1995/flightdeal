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
import java.time.LocalDate
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

        // 출발일이 지난 노선은 조회하지 않는다. 소스가 지난 날짜에 아무것도 주지
        // 않아 매번 헛돌고 API 쿼터만 쓴다. 오늘 출발은 아직 탈 수 있으므로 남긴다.
        //
        // `LocalDate.ofInstant`를 쓰면 안 된다. 그건 API 34부터라서 minSdk 26인
        // 이 앱에서는 안드로이드 13 이하 전 기기가 워커를 돌릴 때마다
        // NoSuchMethodError로 죽는다. 이 모듈은 순수 JVM이라 JDK에서는 존재하고,
        // 테스트도 린트도 잡지 못한다 — 기기에서만 드러난다.
        val today = LocalDate.now(clock)
        return trackedRoutes.getAll()
            .filterNot { it.hasDeparted(today) }
            .mapNotNull { tracked -> check(tracked) }
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
