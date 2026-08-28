package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

class TrackRouteUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
) {

    /**
     * 추적 항목을 만들고 지금 가격을 첫 스냅샷으로 남긴다.
     *
     * 첫 스냅샷을 남기지 않으면 워커가 처음 도는 6시간 뒤까지 비교 대상이 없어
     * 아무 변동도 감지하지 못한다.
     *
     * @return 새 추적 항목의 id
     */
    suspend operator fun invoke(
        quote: PriceQuote,
        tripType: TripType,
        targetPrice: Won? = null,
    ): Long {
        val id = trackedRoutes.add(
            route = quote.route,
            departDate = quote.departDate,
            returnDate = quote.returnDate,
            tripType = tripType,
            targetPrice = targetPrice,
            // 첫 스냅샷과 같은 값을 기준선으로 심는다. 비워두면 등록 직후 첫 폴링에서
            // null 대비 비교가 되어 없던 변동이 잡힌다.
            notifiedPrice = quote.price,
        )

        // 이미 추적 중이면 기준선이 있다. 덧쓰면 다음 변동 판정이 어긋난다.
        // 등록은 됐는데 스냅샷 쓰기가 실패했던 노선은 여기서 채워진다.
        if (history.latest(id) == null) {
            history.append(
                PriceSnapshot(
                    trackedRouteId = id,
                    price = quote.price,
                    tripType = tripType,
                    capturedAt = quote.foundAt,
                )
            )
        }

        return id
    }
}
