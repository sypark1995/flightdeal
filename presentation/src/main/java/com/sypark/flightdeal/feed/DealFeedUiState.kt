package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.DealItem

/**
 * 네 가지 상태를 명시적으로 구분한다. [Empty]는 오류가 아니다.
 */
sealed interface DealFeedUiState {
    data object Loading : DealFeedUiState
    data class Success(val deals: List<DealItem>) : DealFeedUiState
    data object Empty : DealFeedUiState
    data class Error(val retryable: Boolean) : DealFeedUiState
}
