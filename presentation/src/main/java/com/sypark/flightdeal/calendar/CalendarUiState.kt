package com.sypark.flightdeal.calendar

import com.sypark.flightdeal.domain.model.MonthCalendar

/**
 * 딜 피드와 같은 네 상태를 쓴다. [Empty]는 오류가 아니다 — 한산한 노선이 정상적으로
 * 주는 빈 응답이라, 재시도 버튼을 둬도 결과가 같다.
 */
sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(val calendar: MonthCalendar) : CalendarUiState
    data object Empty : CalendarUiState
    data class Error(val retryable: Boolean) : CalendarUiState
}
