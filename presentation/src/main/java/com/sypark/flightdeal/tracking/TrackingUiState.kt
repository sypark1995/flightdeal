package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute

/**
 * @param latest 가장 최근 관측값. 등록 직후라면 등록 시점의 가격이다.
 * @param previous 그 직전 관측값. 아직 한 번밖에 없으면 null이고, 화면은 변동을 표시하지 않는다.
 * @param history 보관 기간 안의 전체 관측 이력, 시간 오름차순. 그래프의 재료다.
 * @param hasDeparted 출발일이 지났는가. 지나면 더 이상 조회하지 않으므로 화면에
 *   보이는 가격은 최신이 아니다 — 카드가 그걸 밝혀야 한다.
 */
data class TrackedItem(
    val tracked: TrackedRoute,
    val latest: PriceSnapshot?,
    val previous: PriceSnapshot?,
    val history: List<PriceSnapshot>,
    val hasDeparted: Boolean,
)

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Empty : TrackingUiState
    data class Success(val items: List<TrackedItem>) : TrackingUiState
}
