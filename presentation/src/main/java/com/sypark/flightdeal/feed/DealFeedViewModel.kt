package com.sypark.flightdeal.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

    // 재시도 버튼을 연타하면 느린 이전 요청이 나중에 끝나 최신 결과를 덮어쓸 수 있다.
    // 요청마다 세대 번호를 매겨, 응답이 도착했을 때 그 사이 더 최신 요청이 시작됐으면 버린다.
    // launch 직후 곧바로 취소하면(예: Job.cancel()) 아직 디스패치되지 않은 코루틴은
    // 본문이 한 줄도 실행되지 않고 사라진다 — 실제 기기에서도 이전 요청이 이미
    // 네트워크까지 나간 뒤라 그 요청 자체는 끝까지 흘러가는 경우가 흔하므로,
    // "실행은 하되 마지막 결과만 반영"하는 방식이 더 안전하다.
    private var latestRequestId = 0L

    init {
        refresh()
    }

    fun refresh() {
        val requestId = ++latestRequestId
        viewModelScope.launch {
            _uiState.value = DealFeedUiState.Loading

            // 기본 출발지는 인천 고정. 설정 화면이 생기면 DataStore에서 읽는다.
            val newState = try {
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

            // 이 요청이 끝나는 사이 더 최신 요청이 시작됐다면 이 결과는 버린다.
            if (requestId == latestRequestId) {
                _uiState.value = newState
            }
        }
    }
}
