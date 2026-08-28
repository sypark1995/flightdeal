package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute

/**
 * @param latest 가장 최근 관측값. 등록 직후라면 등록 시점의 가격이다.
 * @param previous 그 직전 관측값. 아직 한 번밖에 없으면 null이고, 화면은 변동을 표시하지 않는다.
 */
data class TrackedItem(
    val tracked: TrackedRoute,
    val latest: PriceSnapshot?,
    val previous: PriceSnapshot?,
)

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Empty : TrackingUiState
    data class Success(val items: List<TrackedItem>) : TrackingUiState
}
