package com.sypark.flightdeal.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.PRICE_HISTORY_RETENTION_DAYS
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.SetTargetPriceUseCase
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
    private val untrackRoute: UntrackRouteUseCase,
    private val setTargetPrice: SetTargetPriceUseCase,
    private val clock: Clock,
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

    fun setTarget(id: Long, target: Won?) {
        viewModelScope.launch { setTargetPrice(id, target) }
    }

    /** [TrackedItem.previous]를 어떻게 고르는지는 [previousDifferingSnapshot]에 적어뒀다. */
    private fun TrackedRoute.itemFlow(): Flow<TrackedItem> =
        history.observeHistory(id, PRICE_HISTORY_RETENTION_DAYS).map { recent ->
            val latest = recent.lastOrNull()
            TrackedItem(
                tracked = this,
                latest = latest,
                previous = previousDifferingSnapshot(recent, latest),
                // 그래프는 마지막 두 점이 아니라 이미 가져온 전체 이력을 그대로 쓴다.
                // 조회를 새로 추가하면 같은 데이터를 두 번 읽게 된다.
                history = recent,
                // LocalDate.now()를 직접 부르면 테스트에서 고정할 수 없다.
                hasDeparted = hasDeparted(LocalDate.now(clock)),
            )
        }
}
