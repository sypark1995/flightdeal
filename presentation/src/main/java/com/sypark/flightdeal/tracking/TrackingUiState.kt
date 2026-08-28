package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute

/**
 * @param latest 가장 최근 관측값. 등록 직후라면 등록 시점의 가격이다.
 * @param previous 그 직전 관측값. 아직 한 번밖에 없으면 null이고, 화면은 변동을 표시하지 않는다.
 * @param history 보관 기간 안의 전체 관측 이력, 시간 오름차순. 그래프의 재료다.
 */
data class TrackedItem(
    val tracked: TrackedRoute,
    val latest: PriceSnapshot?,
    val previous: PriceSnapshot?,
    val history: List<PriceSnapshot>,
)

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Empty : TrackingUiState
    data class Success(val items: List<TrackedItem>) : TrackingUiState
}
