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

/**
 * [TrackedItem.previous]를 계산하는 규칙. 최신값 바로 앞 관측이 아니라, 최신값과
 * 가격이 다른 가장 최근 관측을 고른다.
 *
 * 스냅샷은 값이 그대로여도 폴링마다 쌓인다. 단순히 "마지막 두 개"를 비교하면
 * 변동 없는 주기가 한 번만 껴도 최근 두 값이 같아져 화살표가 사라진다 — 알림으로
 * 방금 통보한 변동까지 화면에서 지워진다. 저장된 값이 전부 같으면 결과는 null이고
 * 화살표는 정당하게 사라진다.
 *
 * 추적 화면과 위젯이 같은 노선을 놓고 다른 화살표를 보여주면 안 되므로, 이 계산은
 * 여기 한 곳에만 있고 두 화면 모두 이 함수를 부른다.
 */
fun previousDifferingSnapshot(history: List<PriceSnapshot>, latest: PriceSnapshot?): PriceSnapshot? =
    history.dropLast(1).lastOrNull { it.price != latest?.price }

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Empty : TrackingUiState
    data class Success(val items: List<TrackedItem>) : TrackingUiState
}
