package com.sypark.flightdeal.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
     * 마지막 두 관측값을 붙인다. 하나뿐이면 [TrackedItem.previous]가 null이고
     * 화면은 변동을 표시하지 않는다 — 등록 직후엔 비교할 대상이 없다.
     */
    private fun TrackedRoute.itemFlow(): Flow<TrackedItem> =
        history.observeHistory(id, HISTORY_DAYS).map { recent ->
            TrackedItem(
                tracked = this,
                latest = recent.lastOrNull(),
                previous = recent.getOrNull(recent.lastIndex - 1),
            )
        }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}
