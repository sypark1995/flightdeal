package com.sypark.flightdeal.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.PRICE_HISTORY_RETENTION_DAYS
import com.sypark.flightdeal.domain.repository.PriceAlertRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class AlertHistoryViewModel @Inject constructor(
    alerts: PriceAlertRepository,
    trackedRoutes: TrackedRouteRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * 이력 화면과 같은 보관 기간을 쓴다. `CheckTrackedPricesUseCase`가 이력과 알림
     * 기록을 같은 기간으로 정리하므로, 여기서도 같은 상수를 봐야 정리된 뒤에
     * 화면에 "정리됐어야 할" 기록이 남지 않는다.
     */
    val uiState: StateFlow<AlertHistoryUiState> = combine(
        alerts.observeRecent(PRICE_HISTORY_RETENTION_DAYS),
        trackedRoutes.observeAll(),
    ) { recent, routes ->
        val byId = routes.associateBy { it.id }
        // 노선을 못 찾는 기록은 버린다. CASCADE라 정상적으로는 생기지 않지만,
        // 그 하나 때문에 화면 전체가 못 열려서는 안 된다 —
        // RoomTrackedRouteRepository.toDomain이 같은 이유로 읽을 수 없는 행을 버린다.
        val now = clock.instant()
        val items = recent.mapNotNull { alert ->
            val tracked = byId[alert.trackedRouteId] ?: return@mapNotNull null
            AlertHistoryItem(
                alert = alert,
                route = tracked.route,
                departDate = tracked.departDate,
                returnDate = tracked.returnDate,
                tripType = tracked.tripType,
                relativeTime = relativeTimeKo(alert.notifiedAt, now, clock.zone),
            )
        }
        if (items.isEmpty()) AlertHistoryUiState.Empty else AlertHistoryUiState.Success(items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertHistoryUiState.Loading)
}
