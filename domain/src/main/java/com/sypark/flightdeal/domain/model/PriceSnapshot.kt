package com.sypark.flightdeal.domain.model

import java.time.Instant

/**
 * 가격 이력을 얼마나 오래 들고 있을지. 정리(prune)와 추이 그래프 조회가 각자
 * 따로 상수를 선언하면 둘이 어긋날 수 있다 — 정리 기간이 그래프 기간보다 짧아지면
 * 그래프에 보여야 할 가장 오래된 점들이 사용자 몰래 사라진다. 한 곳에서만 선언해서
 * 두 값이 항상 같게 한다.
 */
const val PRICE_HISTORY_RETENTION_DAYS = 90

/**
 * @param tripType 이 가격이 어떤 종류의 운임이었는지 기록만 한다. 지금은 어떤 판정도
 *   이 값을 읽지 않는다 — 왕복/편도가 섞인 스냅샷을 나란히 비교하는 코드가 없기
 *   때문인데, 그건 이 필드 덕분이 아니라 `tracked_route`의 유니크 인덱스가
 *   노선·날짜·`tripType`을 함께 키로 삼아서 애초에 한 추적 항목의 종류가 바뀌는
 *   경로 자체가 없기 때문이다. 이 필드는 나중에 추이 그래프가 생겼을 때 그 리더가
 *   왕복과 편도를 한 그래프에 섞어 그리지 않도록 거를 수 있게 미리 남겨두는 재료다.
 */
data class PriceSnapshot(
    val trackedRouteId: Long,
    val price: Won,
    val tripType: TripType,
    val capturedAt: Instant,
)

enum class Direction { DOWN, UP }

data class PriceChange(
    val trackedRouteId: Long,
    val previous: Won,
    val current: Won,
    val direction: Direction,
    val reachedTarget: Boolean,
)
