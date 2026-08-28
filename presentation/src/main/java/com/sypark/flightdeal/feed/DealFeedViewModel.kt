package com.sypark.flightdeal.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DealFeedUiState>(DealFeedUiState.Loading)
    val uiState: StateFlow<DealFeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DealFeedUiState.Loading

            // 기본 출발지는 인천 고정. 설정 화면이 생기면 DataStore에서 읽는다.
            _uiState.value = when (val result = getDealFeed(Airport.INCHEON)) {
                is AppResult.Success -> DealFeedUiState.Success(result.data)
                AppResult.Empty -> DealFeedUiState.Empty
                is AppResult.NetworkError -> DealFeedUiState.Error(retryable = true)
                is AppResult.Unknown -> DealFeedUiState.Error(retryable = false)
            }
        }
    }
}
