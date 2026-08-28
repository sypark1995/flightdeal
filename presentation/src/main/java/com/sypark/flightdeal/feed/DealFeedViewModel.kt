package com.sypark.flightdeal.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // 재시도 버튼을 연타하면 느린 이전 요청이 나중에 끝나 최신 결과를 덮어쓴다.
        // 결과만 버리는 게 아니라 요청 자체를 취소한다. 버릴 응답을 받자고
        // 네트워크와 배터리를 쓸 이유가 없다.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = DealFeedUiState.Loading

            // 기본 출발지는 인천 고정. 설정 화면이 생기면 DataStore에서 읽는다.
            _uiState.value = try {
                when (val result = getDealFeed(Airport.INCHEON)) {
                    is AppResult.Success -> DealFeedUiState.Success(result.data)
                    AppResult.Empty -> DealFeedUiState.Empty
                    is AppResult.NetworkError -> DealFeedUiState.Error(retryable = true)
                    is AppResult.Unknown -> DealFeedUiState.Error(retryable = false)
                }
            } catch (e: CancellationException) {
                // 취소는 오류가 아니다. 삼키면 취소된 요청이 화면을 오류로 만든다.
                throw e
            } catch (e: Throwable) {
                // Repository 구현체가 AppResult 대신 예외를 던져도 앱이 죽어서는 안 된다.
                DealFeedUiState.Error(retryable = false)
            }
        }
    }
}
