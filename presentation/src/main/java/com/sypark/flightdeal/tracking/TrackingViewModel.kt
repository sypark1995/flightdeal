package com.sypark.flightdeal.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.UntrackRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    val uiState: StateFlow<TrackingUiState> = trackedRoutes.observeAll()
        .map { routes ->
            if (routes.isEmpty()) {
                TrackingUiState.Empty
            } else {
                TrackingUiState.Success(routes.map { it.withRecentPrices() })
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
    private suspend fun TrackedRoute.withRecentPrices(): TrackedItem {
        val recent = history.observeHistory(id, days = HISTORY_DAYS).first()
        return TrackedItem(
            tracked = this,
            latest = recent.lastOrNull(),
            previous = recent.getOrNull(recent.lastIndex - 1),
        )
    }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}
