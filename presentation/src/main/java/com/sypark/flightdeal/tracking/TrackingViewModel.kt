package com.sypark.flightdeal.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.PRICE_HISTORY_RETENTION_DAYS
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.UntrackRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
    private val untrackRoute: UntrackRouteUseCase,
) : ViewModel() {

    /**
     * 노선별 이력 Flow를 그대로 묶는다. `.first()`로 한 번 찍어 오면 구독이 바로 끊겨
     * 워커가 새 가격을 저장해도 화면이 안 바뀐다 — 가격 추적 화면이 가격 변화를
     * 못 보여주게 된다. `combine`은 조회를 병렬로 돌리기도 한다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrackingUiState> = trackedRoutes.observeAll()
        .flatMapLatest { routes ->
            if (routes.isEmpty()) {
                flowOf(TrackingUiState.Empty)
            } else {
                combine(routes.map { it.itemFlow() }) { items ->
                    TrackingUiState.Success(items.toList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingUiState.Loading)

    fun untrack(id: Long) {
        viewModelScope.launch { untrackRoute(id) }
    }

    /**
     * [TrackedItem.previous]는 최신값 바로 앞 관측이 아니라, 최신값과 가격이 다른
     * 가장 최근 관측이다.
     *
     * 스냅샷은 값이 그대로여도 폴링마다 쌓인다. 단순히 "마지막 두 개"를 비교하면
     * 변동 없는 주기가 한 번만 껴도 최근 두 값이 같아져 ▼가 사라진다 — 알림으로
     * 방금 통보한 하락까지 화면에서 지워진다. 저장된 값이 전부 같으면 previous는
     * null이고 화살표는 정당하게 사라진다.
     */
    private fun TrackedRoute.itemFlow(): Flow<TrackedItem> =
        history.observeHistory(id, PRICE_HISTORY_RETENTION_DAYS).map { recent ->
            val latest = recent.lastOrNull()
            TrackedItem(
                tracked = this,
                latest = latest,
                previous = recent.dropLast(1).lastOrNull { it.price != latest?.price },
            )
        }
}
