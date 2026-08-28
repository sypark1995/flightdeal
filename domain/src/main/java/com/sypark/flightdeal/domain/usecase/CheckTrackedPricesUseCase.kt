package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import java.time.Clock
import java.time.YearMonth
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
        history.pruneOlderThan(HISTORY_DAYS)

        return trackedRoutes.getAll().mapNotNull { tracked -> check(tracked) }
    }

    private suspend fun check(tracked: TrackedRoute): PriceChange? {
        val current = currentPrice(tracked) ?: return null
        val previous = history.latest(tracked.id)

        history.append(
            PriceSnapshot(
                trackedRouteId = tracked.id,
                price = current,
                // 추적 항목의 종류를 그대로 쓴다. 섞이면 매번 가짜 하락이 뜬다.
                tripType = tracked.tripType,
                capturedAt = clock.instant(),
            )
        )

        return detectChanges(tracked, previous, current)
    }

    /**
     * 한 노선이 실패했다고 나머지를 포기하지 않는다.
     *
     * 왕복은 귀국일을 정확히 지정하지 않고 같은 달로 조회한다. API가 출발월·귀국월 단위로만
     * 받기 때문이다. 그래서 등록한 귀국일과 다른 조합의 가격이 잡힐 수 있다 —
     * 같은 기준으로 계속 비교하므로 변동 판정은 성립한다.
     */
    private suspend fun currentPrice(tracked: TrackedRoute) =
        when (
            val result = prices.calendarPrices(
                route = tracked.route,
                month = YearMonth.from(tracked.departDate),
                tripType = tracked.tripType,
            )
        ) {
            is AppResult.Success ->
                result.data.filter { it.departDate == tracked.departDate }.minByOrNull { it.price.amount }?.price
            else -> null
        }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}
