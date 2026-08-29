package com.sypark.flightdeal.alerts

import com.sypark.flightdeal.domain.model.PriceAlert
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import java.time.LocalDate

/**
 * 화면에 필요한 만큼만 [PriceAlert]에 노선 정보를 채운 것. 노선 이름은 기록에
 * 저장돼 있지 않다 — `tracked_route`가 지워지면 이 기록도 CASCADE로 함께
 * 지워지므로 "지워진 노선의 알림"이라는 상태 자체가 없다.
 */
data class AlertHistoryItem(
    val alert: PriceAlert,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val tripType: TripType,
    /** [relativeTimeKo]로 미리 계산해 둔다. 화면은 문구만 그린다. */
    val relativeTime: String,
)

sealed interface AlertHistoryUiState {
    data object Loading : AlertHistoryUiState
    data object Empty : AlertHistoryUiState
    data class Success(val items: List<AlertHistoryItem>) : AlertHistoryUiState
}
